// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { BatchResponse, getMessaging } from "firebase-admin/messaging";
import { logger } from "firebase-functions";
import { NotificationMessage, sendToUser } from "../src/notify";

// FCM has no emulator: the tokens are read from the Firestore emulator, the sending is mocked
jest.mock("firebase-admin/messaging", () => ({ getMessaging: jest.fn() }));

const MESSAGE: NotificationMessage = {
  title: "Reservation accepted",
  body: "Café Lumen accepted your reservation",
  data: { kind: "reservation", route: "quests", reservationId: "reservation-1" },
};

const sendEachForMulticast = jest.fn<Promise<BatchResponse>, [unknown]>();

/** A user of their own per test, so the tests never see each other's tokens. */
async function userWithTokens(...tokens: string[]): Promise<string> {
  const uid = `user-${randomUUID()}`;
  const userTokens = getFirestore().collection("users").doc(uid).collection("tokens");
  await Promise.all(tokens.map((token) => userTokens.doc(token).set({ savedAt: new Date() })));
  return uid;
}

function sentTo(count: number, failures: string[] = []): BatchResponse {
  const responses = [
    ...failures.map((code) => ({ success: false, error: { code } })),
    ...Array.from({ length: count - failures.length }, () => ({ success: true, messageId: "id" })),
  ];
  return {
    responses,
    successCount: count - failures.length,
    failureCount: failures.length,
  } as BatchResponse;
}

beforeAll(() => {
  if (getApps().length === 0) initializeApp({ projectId: process.env.GCLOUD_PROJECT });
});

beforeEach(() => {
  sendEachForMulticast.mockReset();
  jest.mocked(getMessaging).mockReturnValue({ sendEachForMulticast } as never);
});

afterEach(() => jest.restoreAllMocks());

describe("sendToUser", () => {
  it("sends a data-only, high-priority, one-day message to every phone of the user", async () => {
    const uid = await userWithTokens("phone-a", "phone-b");
    await userWithTokens("someone-elses-phone");
    sendEachForMulticast.mockResolvedValue(sentTo(2));

    await sendToUser(uid, MESSAGE);

    expect(sendEachForMulticast).toHaveBeenCalledTimes(1);
    const sent = sendEachForMulticast.mock.calls[0][0] as { tokens: string[] };
    expect(sent.tokens.sort()).toEqual(["phone-a", "phone-b"]);
    expect(sent).toEqual({
      tokens: expect.any(Array),
      data: {
        title: "Reservation accepted",
        body: "Café Lumen accepted your reservation",
        kind: "reservation",
        route: "quests",
        reservationId: "reservation-1",
      },
      android: { priority: "high", ttl: 24 * 60 * 60 * 1000 },
    });
  });

  it("sends nothing to a user with no phone", async () => {
    const uid = await userWithTokens();
    const info = jest.spyOn(logger, "info").mockImplementation(() => undefined);

    await sendToUser(uid, MESSAGE);

    expect(sendEachForMulticast).not.toHaveBeenCalled();
    expect(info).toHaveBeenCalledWith("sendToUser: the user has no phone to notify", { uid });
  });

  it("still sends to the other phones when FCM refuses one, and logs it", async () => {
    const uid = await userWithTokens("expired-phone", "working-phone");
    sendEachForMulticast.mockResolvedValue(
      sentTo(2, ["messaging/registration-token-not-registered"]),
    );
    const warn = jest.spyOn(logger, "warn").mockImplementation(() => undefined);

    await expect(sendToUser(uid, MESSAGE)).resolves.toBeUndefined();

    expect(warn).toHaveBeenCalledWith("sendToUser: some phones didn't get the message", {
      uid,
      failed: 1,
      sent: 1,
      errors: ["messaging/registration-token-not-registered"],
    });
  });

  it("logs no warning when every phone got the message", async () => {
    const uid = await userWithTokens("phone-a");
    sendEachForMulticast.mockResolvedValue(sentTo(1));
    const warn = jest.spyOn(logger, "warn").mockImplementation(() => undefined);

    await sendToUser(uid, MESSAGE);

    expect(warn).not.toHaveBeenCalled();
  });

  it("rejects when FCM can't be reached", async () => {
    const uid = await userWithTokens("phone-a");
    sendEachForMulticast.mockRejectedValue(new Error("app/network-error"));

    await expect(sendToUser(uid, MESSAGE)).rejects.toThrow("app/network-error");
  });
});
