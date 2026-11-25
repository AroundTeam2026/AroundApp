// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { DocumentReference, getFirestore } from "firebase-admin/firestore";
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

/** Answers each phone FCM is asked to reach: refused with the code in [refused], else sent. */
function answerPerPhone(refused: Record<string, string>) {
  sendEachForMulticast.mockImplementation(async (message) => {
    const { fids } = message as { fids: string[] };
    const responses = fids.map((fid) =>
      fid in refused
        ? { success: false, error: { code: refused[fid] } }
        : { success: true, messageId: "id" },
    );
    const failureCount = responses.filter((r) => !r.success).length;
    return { responses, successCount: fids.length - failureCount, failureCount } as BatchResponse;
  });
}

async function tokensOf(uid: string): Promise<string[]> {
  const saved = await getFirestore().collection("users").doc(uid).collection("tokens").get();
  return saved.docs.map((doc) => doc.id).sort();
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
    answerPerPhone({});

    await sendToUser(uid, MESSAGE);

    expect(sendEachForMulticast).toHaveBeenCalledTimes(1);
    const sent = sendEachForMulticast.mock.calls[0][0] as { fids: string[] };
    expect(sent.fids.sort()).toEqual(["phone-a", "phone-b"]);
    expect(sent).toEqual({
      fids: expect.any(Array),
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
    const uid = await userWithTokens("busy-phone", "working-phone");
    answerPerPhone({ "busy-phone": "messaging/internal-error" });
    const warn = jest.spyOn(logger, "warn").mockImplementation(() => undefined);

    await expect(sendToUser(uid, MESSAGE)).resolves.toBeUndefined();

    expect(warn).toHaveBeenCalledWith("sendToUser: some phones didn't get the message", {
      uid,
      failed: 1,
      sent: 1,
      errors: ["messaging/internal-error"],
    });
  });

  it("logs no warning when every phone got the message", async () => {
    const uid = await userWithTokens("phone-a");
    answerPerPhone({});
    const warn = jest.spyOn(logger, "warn").mockImplementation(() => undefined);

    await sendToUser(uid, MESSAGE);

    expect(warn).not.toHaveBeenCalled();
  });

  it("rejects when FCM can't be reached", async () => {
    const uid = await userWithTokens("phone-a");
    sendEachForMulticast.mockRejectedValue(new Error("app/network-error"));

    await expect(sendToUser(uid, MESSAGE)).rejects.toThrow("app/network-error");
  });

  it("deletes the tokens FCM says are gone and keeps the user's others", async () => {
    const uid = await userWithTokens(
      "uninstalled-phone",
      "old-play-services-phone",
      "cleared-phone",
      "working-phone",
    );
    const other = await userWithTokens("uninstalled-phone");
    answerPerPhone({
      "uninstalled-phone": "messaging/installation-id-not-registered",
      "old-play-services-phone": "messaging/registration-token-not-registered",
      "cleared-phone": "messaging/invalid-registration-token",
    });
    jest.spyOn(logger, "warn").mockImplementation(() => undefined);
    const info = jest.spyOn(logger, "info").mockImplementation(() => undefined);

    await sendToUser(uid, MESSAGE);

    expect(await tokensOf(uid)).toEqual(["working-phone"]);
    expect(await tokensOf(other)).toEqual(["uninstalled-phone"]);
    expect(info).toHaveBeenCalledWith("sendToUser: deleted the tokens of phones that are gone", {
      uid,
      deleted: 3,
    });
  });

  it("keeps a token FCM refuses for another reason", async () => {
    const uid = await userWithTokens("busy-phone", "working-phone");
    answerPerPhone({ "busy-phone": "messaging/internal-error" });
    jest.spyOn(logger, "warn").mockImplementation(() => undefined);
    const info = jest.spyOn(logger, "info").mockImplementation(() => undefined);

    await sendToUser(uid, MESSAGE);

    expect(await tokensOf(uid)).toEqual(["busy-phone", "working-phone"]);
    expect(info).not.toHaveBeenCalled();
  });

  it("still resolves, keeps the token and warns when a gone token can't be deleted", async () => {
    const uid = await userWithTokens("uninstalled-phone", "stuck-phone");
    answerPerPhone({
      "uninstalled-phone": "messaging/registration-token-not-registered",
      "stuck-phone": "messaging/registration-token-not-registered",
    });
    const deleteDoc = DocumentReference.prototype.delete;
    jest.spyOn(DocumentReference.prototype, "delete").mockImplementation(function (
      this: DocumentReference,
      ...args
    ) {
      return this.id === "stuck-phone"
        ? Promise.reject(new Error("unavailable"))
        : deleteDoc.apply(this, args);
    });
    const warn = jest.spyOn(logger, "warn").mockImplementation(() => undefined);
    const info = jest.spyOn(logger, "info").mockImplementation(() => undefined);

    await expect(sendToUser(uid, MESSAGE)).resolves.toBeUndefined();

    expect(await tokensOf(uid)).toEqual(["stuck-phone"]);
    expect(info).toHaveBeenCalledWith("sendToUser: deleted the tokens of phones that are gone", {
      uid,
      deleted: 1,
    });
    expect(warn).toHaveBeenCalledWith("sendToUser: couldn't delete some gone tokens", {
      uid,
      failed: 1,
      errors: ["Error: unavailable"],
    });
  });

  it("logs no deletion when the only gone token can't be deleted", async () => {
    const uid = await userWithTokens("stuck-phone");
    answerPerPhone({ "stuck-phone": "messaging/registration-token-not-registered" });
    jest
      .spyOn(DocumentReference.prototype, "delete")
      .mockRejectedValue(new Error("unavailable"));
    jest.spyOn(logger, "warn").mockImplementation(() => undefined);
    const info = jest.spyOn(logger, "info").mockImplementation(() => undefined);

    await expect(sendToUser(uid, MESSAGE)).resolves.toBeUndefined();

    expect(await tokensOf(uid)).toEqual(["stuck-phone"]);
    expect(info).not.toHaveBeenCalled();
  });
});
