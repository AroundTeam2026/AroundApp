// Co-authored-by: OpenAI Codex <noreply@openai.com>

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { sendToUser } from "../src/notify";
import { notifyReservationAnswered, sendReservationAnswer } from "../src/reservationAnswered";

jest.mock("../src/notify", () => ({ sendToUser: jest.fn() }));

const sender = jest.mocked(sendToUser);

async function membership(reservationId: string, uid: string, status: string): Promise<void> {
  await getFirestore()
    .collection("party_user")
    .doc(`${reservationId}_${uid}`)
    .set({ reservationId, uid, status, joinedAt: new Date(), updatedAt: new Date() });
}

beforeAll(() => {
  if (getApps().length === 0) initializeApp({ projectId: process.env.GCLOUD_PROJECT });
});

beforeEach(() => {
  sender.mockReset();
  sender.mockResolvedValue(undefined);
});

describe("sendReservationAnswer", () => {
  it("passes the update event's snapshots and reservation id to the sender", async () => {
    const reservationId = randomUUID();
    await membership(reservationId, "creator", "JOINED");

    await notifyReservationAnswered.run({
      data: {
        before: { data: () => ({ status: "PENDING" }) },
        after: { data: () => ({ status: "APPROVED" }) },
      },
      params: { reservationId },
    } as never);

    expect(sender).toHaveBeenCalledWith("creator", {
      title: "Reservation approved",
      body: "Your party's reservation was approved.",
      data: { kind: "reservation", route: "quests", reservationId },
    });
  });

  it.each([
    ["APPROVED", "Reservation approved", "Your party's reservation was approved."],
    ["REJECTED", "Reservation declined", "Your party's reservation was declined."],
  ])("notifies every joined member when a reservation becomes %s", async (status, title, body) => {
    const reservationId = randomUUID();
    await Promise.all([
      membership(reservationId, "creator", "JOINED"),
      membership(reservationId, "friend", "JOINED"),
      membership(reservationId, "former-member", "WITHDRAWN"),
      membership(randomUUID(), "other-party", "JOINED"),
    ]);

    await sendReservationAnswer({ status: "PENDING" }, { status }, reservationId);

    expect(sender).toHaveBeenCalledTimes(2);
    expect(sender.mock.calls.map(([uid]) => uid).sort()).toEqual(["creator", "friend"]);
    for (const [, message] of sender.mock.calls) {
      expect(message).toEqual({
        title,
        body,
        data: { kind: "reservation", route: "quests", reservationId },
      });
    }
  });

  it.each([
    ["PENDING", "PENDING"],
    ["APPROVED", "APPROVED"],
    ["APPROVED", "CANCELLED"],
    ["REJECTED", "REJECTED"],
    ["PENDING", "CANCELLED"],
  ])("ignores a change from %s to %s", async (before, after) => {
    await sendReservationAnswer({ status: before }, { status: after }, randomUUID());
    expect(sender).not.toHaveBeenCalled();
  });

  it("ignores a missing snapshot", async () => {
    await sendReservationAnswer(undefined, { status: "APPROVED" }, randomUUID());
    await sendReservationAnswer({ status: "PENDING" }, undefined, randomUUID());
    expect(sender).not.toHaveBeenCalled();
  });

  it("sends nothing when the reservation has no joined members", async () => {
    const reservationId = randomUUID();
    await membership(reservationId, "former-member", "WITHDRAWN");

    await sendReservationAnswer({ status: "PENDING" }, { status: "APPROVED" }, reservationId);

    expect(sender).not.toHaveBeenCalled();
  });

  it("attempts every joined member even if one send fails", async () => {
    const reservationId = randomUUID();
    await Promise.all([
      membership(reservationId, "first", "JOINED"),
      membership(reservationId, "second", "JOINED"),
    ]);
    sender.mockRejectedValueOnce(new Error("FCM unavailable"));

    await expect(
      sendReservationAnswer({ status: "PENDING" }, { status: "APPROVED" }, reservationId),
    ).rejects.toThrow("FCM unavailable");
    expect(sender).toHaveBeenCalledTimes(2);
  });
});
