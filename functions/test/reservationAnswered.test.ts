// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import { randomUUID } from "node:crypto";
import { logger } from "firebase-functions";
import { APPROVED, CANCELLED, PENDING, REJECTED, sendToUser } from "../src/notify";
import { notifyReservationAnswered, sendReservationAnswer } from "../src/reservationAnswered";
import { initTestApp, membership, QUEST_TITLE, seedQuest, seedVenue, VENUE_NAME } from "./fixtures";

// The sending itself is sendToUser's, tested in notify.test.ts; the shared helpers stay real
jest.mock("../src/notify", () => ({
  ...jest.requireActual("../src/notify"),
  sendToUser: jest.fn(),
}));

const sender = jest.mocked(sendToUser);

beforeAll(initTestApp);

beforeEach(() => {
  sender.mockReset();
  sender.mockResolvedValue(undefined);
});

afterEach(() => jest.restoreAllMocks());

/** A venue and a quest of their own, so the notification can name them. */
async function venueAndQuest(): Promise<{ venueId: string; questId: string }> {
  const [venueId, questId] = await Promise.all([seedVenue(), seedQuest()]);
  return { venueId, questId };
}

describe("sendReservationAnswer", () => {
  it("passes the update event's snapshots and reservation id to the sender", async () => {
    const reservationId = randomUUID();
    await membership(reservationId, "creator", "JOINED");

    await notifyReservationAnswered.run({
      data: {
        before: { data: () => ({ status: PENDING }) },
        after: { data: () => ({ status: APPROVED }) },
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
    [APPROVED, "Reservation approved", "Your party's reservation was approved."],
    [REJECTED, "Reservation declined", "Your party's reservation was declined."],
  ])("notifies every joined member when a reservation becomes %s", async (status, title, body) => {
    const reservationId = randomUUID();
    await Promise.all([
      membership(reservationId, "creator", "JOINED"),
      membership(reservationId, "friend", "JOINED"),
      membership(reservationId, "former-member", "WITHDRAWN"),
      membership(randomUUID(), "other-party", "JOINED"),
    ]);

    await sendReservationAnswer({ status: PENDING }, { status }, reservationId);

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
    [PENDING, PENDING],
    [APPROVED, APPROVED],
    [APPROVED, CANCELLED],
    [REJECTED, REJECTED],
    [PENDING, CANCELLED],
  ])("ignores a change from %s to %s", async (before, after) => {
    await sendReservationAnswer({ status: before }, { status: after }, randomUUID());
    expect(sender).not.toHaveBeenCalled();
  });

  it("ignores a missing snapshot", async () => {
    await sendReservationAnswer(undefined, { status: APPROVED }, randomUUID());
    await sendReservationAnswer({ status: PENDING }, undefined, randomUUID());
    expect(sender).not.toHaveBeenCalled();
  });

  it("sends nothing when the reservation has no joined members", async () => {
    const reservationId = randomUUID();
    await membership(reservationId, "former-member", "WITHDRAWN");

    await sendReservationAnswer({ status: PENDING }, { status: APPROVED }, reservationId);

    expect(sender).not.toHaveBeenCalled();
  });

  it("still tells the others when one send fails, and logs whose failed", async () => {
    const reservationId = randomUUID();
    await Promise.all([
      membership(reservationId, "first", "JOINED"),
      membership(reservationId, "second", "JOINED"),
    ]);
    const warn = jest.spyOn(logger, "warn").mockImplementation(() => undefined);
    sender.mockImplementation(async (uid) => {
      if (uid === "first") throw new Error("FCM unavailable");
    });

    await sendReservationAnswer({ status: PENDING }, { status: APPROVED }, reservationId);

    expect(sender).toHaveBeenCalledTimes(2);
    expect(warn).toHaveBeenCalledTimes(1);
    expect(warn).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({ uid: "first", reservationId }),
    );
  });

  it.each([
    [
      APPROVED,
      `${VENUE_NAME} approved your reservation`,
      `${QUEST_TITLE} · Head over and send your proof once you're there.`,
    ],
    [
      REJECTED,
      `${VENUE_NAME} declined your reservation`,
      `${QUEST_TITLE} · Have a look at other quests nearby.`,
    ],
  ])(
    "names the venue and the quest when the reservation becomes %s",
    async (status, title, body) => {
      const reservationId = randomUUID();
      const { venueId, questId } = await venueAndQuest();
      await membership(reservationId, "creator", "JOINED");

      await sendReservationAnswer({ status: PENDING }, { status, venueId, questId }, reservationId);

      expect(sender).toHaveBeenCalledWith("creator", {
        title,
        body,
        data: { kind: "reservation", route: "quests", reservationId },
      });
    },
  );

  // The rules let a venue delete a quest, then decline its pending reservations
  it("still tells every member when a declined reservation's quest was deleted", async () => {
    const reservationId = randomUUID();
    const { venueId } = await venueAndQuest();
    await Promise.all([
      membership(reservationId, "creator", "JOINED"),
      membership(reservationId, "friend", "JOINED"),
    ]);

    await sendReservationAnswer(
      { status: PENDING },
      { status: REJECTED, venueId, questId: randomUUID() },
      reservationId,
    );

    expect(sender.mock.calls.map(([uid]) => uid).sort()).toEqual(["creator", "friend"]);
    for (const [, message] of sender.mock.calls) {
      expect(message).toEqual({
        title: "Reservation declined",
        body: "Your party's reservation was declined.",
        data: { kind: "reservation", route: "quests", reservationId },
      });
    }
  });

  it("runs on updated reservations", () => {
    expect(notifyReservationAnswered.__endpoint.eventTrigger).toMatchObject({
      eventType: "google.cloud.firestore.document.v1.updated",
      eventFilterPathPatterns: { document: "reservations/{reservationId}" },
    });
  });
});
