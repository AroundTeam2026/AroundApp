// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import { randomUUID } from "node:crypto";
import { getFirestore } from "firebase-admin/firestore";
import {
  APPROVED,
  CANCELLED,
  NotificationMessage,
  PENDING,
  sendToUser,
  UNNAMED_LEADER,
} from "../src/notify";
import {
  CANCELLATION_TITLE,
  cancellationBody,
  notifyVenueCancellation,
} from "../src/venueCancellation";
import { BLANK_NAME, initTestApp, LEADER, QUEST_TITLE, seedQuest, VENUE } from "./fixtures";

// The sending itself is sendToUser's, tested in notify.test.ts; the shared helpers stay real
jest.mock("../src/notify", () => ({
  ...jest.requireActual("../src/notify"),
  sendToUser: jest.fn(),
}));

const sent = jest.mocked(sendToUser);

/** The uid of the party's creator, the only explorer the rules let cancel. */
const CREATOR = "creator-1";
/** Another explorer of the party, who joins and leaves but never cancels. */
const MEMBER = "member-2";

beforeAll(initTestApp);

beforeEach(() => sent.mockReset());

/** Writes [before], updates it with [change] as [authId], runs the trigger like Firestore. */
async function updateAndNotify(
  before: Record<string, unknown>,
  change: Record<string, unknown>,
  authId = CREATOR,
): Promise<string> {
  const ref = getFirestore().collection("reservations").doc(randomUUID());
  await ref.set({ venueId: VENUE, explorerName: LEADER, ...before });
  const beforeSnap = await ref.get();
  await ref.update(change);
  await notifyVenueCancellation.run({
    params: { reservationId: ref.id },
    data: { before: beforeSnap, after: await ref.get() },
    authType: "app_user",
    authId,
  } as unknown as Parameters<typeof notifyVenueCancellation.run>[0]);
  return ref.id;
}

function expectSent(body: string, reservationId: string) {
  expect(sent).toHaveBeenCalledTimes(1);
  expect(sent).toHaveBeenCalledWith(VENUE, {
    title: CANCELLATION_TITLE,
    body,
    data: { kind: "cancellation", route: "venue_dashboard", reservationId },
  } satisfies NotificationMessage);
}

describe("notifyVenueCancellation", () => {
  it.each([PENDING, APPROVED])("tells the venue a %s reservation was cancelled", async (status) => {
    const questId = await seedQuest();

    const id = await updateAndNotify({ questId, status }, { status: CANCELLED });

    // The Figma text, written out once, so a typo in cancellationBody fails here
    expectSent(`${LEADER} cancelled their booking of ${QUEST_TITLE}.`, id);
  });

  it("still tells the venue when its quest was deleted, naming no quest", async () => {
    const id = await updateAndNotify(
      { questId: randomUUID(), status: APPROVED },
      { status: CANCELLED },
    );

    expectSent(`${LEADER} cancelled their booking.`, id);
  });

  it.each([null, BLANK_NAME])("calls a leader named %p an explorer", async (explorerName) => {
    const questId = await seedQuest();

    const id = await updateAndNotify(
      { questId, status: PENDING, explorerName },
      { status: CANCELLED },
    );

    expectSent(cancellationBody(UNNAMED_LEADER, QUEST_TITLE), id);
  });

  it.each([
    ["the venue's own cancellation", { status: PENDING }, { status: CANCELLED }, VENUE],
    ["the venue's answer", { status: PENDING }, { status: APPROVED }, VENUE],
    ["a member joining", { status: PENDING, joinedCount: 1 }, { joinedCount: 2 }, MEMBER],
    ["a member leaving", { status: APPROVED, joinedCount: 2 }, { joinedCount: 1 }, MEMBER],
    ["a write to a cancelled one", { status: CANCELLED }, { joinedCount: 1 }, CREATOR],
  ])("notifies nobody of %s", async (_, before, change, authId) => {
    const questId = await seedQuest();

    await updateAndNotify({ questId, ...before }, change, authId);

    expect(sent).not.toHaveBeenCalled();
  });

  it("runs on updates of reservations, knowing who wrote them", () => {
    expect(notifyVenueCancellation.__endpoint.eventTrigger).toMatchObject({
      eventType: "google.cloud.firestore.document.v1.updated.withAuthContext",
      eventFilterPathPatterns: { document: "reservations/{reservationId}" },
    });
  });
});
