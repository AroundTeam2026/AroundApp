// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import { randomUUID } from "node:crypto";
import { DocumentData, DocumentReference, getFirestore, Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { Change } from "firebase-functions/v2/firestore";
import { APPROVED, NotificationMessage, PENDING, REJECTED, sendToUser } from "../src/notify";
import {
  notifyProofAnswered,
  PROOF_REJECTED,
  QUEST_COMPLETED,
  rewardText,
  sendProofAnswer,
  UNNAMED_VENUE,
} from "../src/proofAnswered";
import {
  EXPLORER,
  FREE_CROISSANT,
  initTestApp,
  JOINED,
  LEADER,
  membership,
  QUEST_TITLE,
  seedReservation,
  seedVenue,
  VENUE,
  VENUE_NAME,
  WITHDRAWN,
} from "./fixtures";

// The sending itself is sendToUser's, tested in notify.test.ts; the shared helpers stay real
jest.mock("../src/notify", () => ({
  ...jest.requireActual("../src/notify"),
  sendToUser: jest.fn(),
}));

const sent = jest.mocked(sendToUser);

const REASON = "The fox isn't in it";

/**
 * The texts of #631's Figma for the shared quest, venue and reward: the one place the tests spell
 * them out. Change them here, with the Figma frames, when the wording changes.
 */
const FIGMA = {
  approvedTitle: `Quest completed: ${QUEST_TITLE}`,
  approvedBody: `${VENUE_NAME} approved your proof. Your reward: a free croissant.`,
  approvedBodyWithoutReward: `${VENUE_NAME} approved your proof.`,
  rejectedTitle: `${VENUE_NAME} rejected your proof`,
  rejectedBody: `“${REASON}” You can send a new one from the Quests tab.`,
};

/** The party's joined members: the shared explorer and a friend. */
const JOINED_MEMBERS = [EXPLORER, "friend"].sort();

beforeAll(async () => {
  initTestApp();
  await seedVenue(VENUE);
});

beforeEach(() => {
  sent.mockReset();
  sent.mockResolvedValue(undefined);
});

interface Fixture {
  ref: DocumentReference;
  /** The party's pending proof, as stored before each test's update. */
  proof: DocumentData;
}

/**
 * A reservation of its own per test, of the shared quest (with [reward]) at [VENUE] or, when
 * [venueExists] is false, at a venue that isn't stored, with a pending proof. Its party has
 * [JOINED_MEMBERS] joined, "former-member" withdrawn and a membership without a uid; another
 * party's member is joined elsewhere.
 */
async function fixture({ reward = FREE_CROISSANT as unknown, venueExists = true } = {}) {
  const reservationId = await seedReservation(
    JOINED_MEMBERS.length,
    LEADER,
    reward ? { reward } : {},
  );
  await Promise.all([
    ...JOINED_MEMBERS.map((uid) => membership(reservationId, uid, JOINED)),
    membership(reservationId, "former-member", WITHDRAWN),
    membership(reservationId, null, JOINED),
    membership(randomUUID(), "other-party", JOINED),
  ]);
  const proof = {
    venueId: venueExists ? VENUE : `missing-venue-${randomUUID()}`,
    explorerUid: EXPLORER,
    status: PENDING,
    photoUrl: "gs://proofs/photo-1",
    submittedAt: Timestamp.fromMillis(1000),
  };
  const ref = getFirestore().collection("completions").doc(reservationId);
  await ref.set(proof);
  return { ref, proof } satisfies Fixture;
}

/** Writes [data] to [ref] and runs the trigger on the update, as the emulator would. */
async function updateAndNotify(ref: DocumentReference, data: DocumentData): Promise<void> {
  const before = await ref.get();
  await ref.set(data);
  const after = await ref.get();
  await notifyProofAnswered.run({
    params: { reservationId: ref.id },
    data: new Change(before, after),
  } as Parameters<typeof notifyProofAnswered.run>[0]);
}

/** The recipients of the messages sent so far, sorted. */
function recipients(): string[] {
  return sent.mock.calls.map(([uid]) => uid).sort();
}

/** The one message sent to every recipient, failing if they got different ones. */
function theMessage(): NotificationMessage {
  const messages = sent.mock.calls.map(([, message]) => message);
  for (const message of messages) expect(message).toEqual(messages[0]);
  return messages[0];
}

describe("notifyProofAnswered", () => {
  it("tells every joined member, in Figma's words, when the venue approves the proof", async () => {
    const { ref, proof } = await fixture();

    await updateAndNotify(ref, { ...proof, status: APPROVED });

    expect(recipients()).toEqual(JOINED_MEMBERS);
    expect(theMessage()).toEqual({
      title: FIGMA.approvedTitle,
      body: FIGMA.approvedBody,
      data: { kind: "proof", route: "quests", reservationId: ref.id },
    });
  });

  it("tells every joined member, in Figma's words, when the venue rejects the proof", async () => {
    const { ref, proof } = await fixture();

    await updateAndNotify(ref, { ...proof, status: REJECTED, rejectReason: REASON });

    expect(recipients()).toEqual(JOINED_MEMBERS);
    expect(theMessage()).toEqual({
      title: FIGMA.rejectedTitle,
      body: FIGMA.rejectedBody,
      data: { kind: "proof", route: "quests", reservationId: ref.id },
    });
  });

  it("sends the approval when the venue approves a proof it had rejected", async () => {
    const { ref, proof } = await fixture();
    await ref.set({ ...proof, status: REJECTED, rejectReason: REASON });

    await updateAndNotify(ref, { ...proof, status: APPROVED });

    expect(recipients()).toEqual(JOINED_MEMBERS);
    expect(theMessage()).toMatchObject({ title: FIGMA.approvedTitle, body: FIGMA.approvedBody });
  });

  it("leaves the reward out of the approval of a quest without one", async () => {
    const { ref, proof } = await fixture({ reward: null });

    await updateAndNotify(ref, { ...proof, status: APPROVED });

    expect(theMessage().body).toBe(FIGMA.approvedBodyWithoutReward);
  });

  it("doesn't end the approval with two periods when the reward already ends one", async () => {
    const reward = { type: "OTHER", description: "A tour of the kitchen." };
    const { ref, proof } = await fixture({ reward });

    await updateAndNotify(ref, { ...proof, status: APPROVED });

    expect(theMessage().body.endsWith(` ${rewardText(reward)}`)).toBe(true);
  });

  it("notifies no explorer of a new proof", async () => {
    const { ref, proof } = await fixture();

    // A new proof replacing a pending one, then one sent after a rejection
    await updateAndNotify(ref, { ...proof, submittedAt: Timestamp.fromMillis(2000) });
    await ref.set({ ...proof, status: REJECTED, rejectReason: REASON });
    await updateAndNotify(ref, { ...proof, submittedAt: Timestamp.fromMillis(3000) });

    expect(sent).not.toHaveBeenCalled();
  });

  it("sends nothing when the party has no joined member left", async () => {
    const { ref, proof } = await fixture();
    const party = await getFirestore()
      .collection("party_user")
      .where("reservationId", "==", ref.id)
      .get();
    await Promise.all(party.docs.map((doc) => doc.ref.update({ status: WITHDRAWN })));

    await updateAndNotify(ref, { ...proof, status: APPROVED });

    expect(sent).not.toHaveBeenCalled();
  });

  it.each(["quest", "reservation"])(
    "still tells every member they completed the quest when its %s was deleted",
    async (deleted) => {
      const { ref, proof } = await fixture();
      const reservation = getFirestore().collection("reservations").doc(ref.id);
      const questId = (await reservation.get()).get("questId");
      // A venue may delete a quest that still has reservations, then approve their proofs
      await (deleted === "quest"
        ? getFirestore().collection("quests").doc(questId).delete()
        : reservation.delete());

      await updateAndNotify(ref, { ...proof, status: APPROVED });

      expect(recipients()).toEqual(JOINED_MEMBERS);
      expect(theMessage()).toMatchObject({
        title: QUEST_COMPLETED,
        body: FIGMA.approvedBodyWithoutReward,
      });
    },
  );

  it("still tells every member when the venue's name can't be read", async () => {
    const { ref, proof } = await fixture({ venueExists: false });

    await updateAndNotify(ref, { ...proof, status: APPROVED });
    const approval = theMessage();
    sent.mockClear();
    await updateAndNotify(ref, { ...proof, status: REJECTED, rejectReason: REASON });
    const rejection = theMessage();

    expect(recipients()).toEqual(JOINED_MEMBERS);
    expect(approval).toMatchObject({
      title: FIGMA.approvedTitle,
      body: FIGMA.approvedBody.replace(VENUE_NAME, UNNAMED_VENUE),
    });
    expect(rejection).toMatchObject({ title: PROOF_REJECTED, body: FIGMA.rejectedBody });
  });

  it("still tells the others when one send fails, and logs whose failed", async () => {
    const { ref, proof } = await fixture();
    const warn = jest.spyOn(logger, "warn").mockImplementation(() => undefined);
    sent.mockImplementation(async (uid) => {
      if (uid === EXPLORER) throw new Error("FCM unavailable");
    });

    await updateAndNotify(ref, { ...proof, status: APPROVED });

    expect(recipients()).toEqual(JOINED_MEMBERS);
    expect(warn).toHaveBeenCalledTimes(1);
    expect(warn).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({ uid: EXPLORER, reservationId: ref.id }),
    );
    warn.mockRestore();
  });

  it("ignores a missing snapshot", async () => {
    const { ref, proof } = await fixture();

    // A completion created or deleted: there is no before, or no after, to compare
    await sendProofAnswer(undefined, { ...proof, status: APPROVED }, ref.id);
    await sendProofAnswer({ ...proof, status: APPROVED }, undefined, ref.id);

    expect(sent).not.toHaveBeenCalled();
  });
});

describe("rewardText", () => {
  // Written as the app writes a reward on a quest's card (QuestChips.kt, RewardAmountText.kt)
  it.each([
    [{ type: "DISCOUNT", amount: 10, unit: "PERCENT" }, "10% off"],
    [{ type: "DISCOUNT", amount: 12.5, unit: "PERCENT" }, "12.5% off"],
    [{ type: "DISCOUNT", amount: 12.25, unit: "PERCENT" }, "12.25% off"],
    [{ type: "DISCOUNT", amount: 5, unit: "CHF" }, "CHF 5 off"],
    [{ type: "DISCOUNT", amount: 2.5, unit: "CHF" }, "CHF 2.50 off"],
    [{ type: "FREE_ITEM", name: " croissant ", specifics: "any flavour" }, "a free croissant"],
    [{ type: "OTHER", description: "A tour of the kitchen" }, "A tour of the kitchen"],
  ])("writes %j as %s", (reward, text) => {
    expect(rewardText(reward)).toBe(text);
  });

  it.each([
    [undefined],
    [null],
    ["FREE_ITEM"],
    [{ type: "UNKNOWN" }],
    [{ type: "DISCOUNT", unit: "CHF" }],
    [{ type: "DISCOUNT", amount: 5, unit: "EUR" }],
    [{ type: "FREE_ITEM", name: " " }],
    [{ type: "OTHER" }],
  ])("gives null for %j, which it can't read", (reward) => {
    expect(rewardText(reward)).toBeNull();
  });
});
