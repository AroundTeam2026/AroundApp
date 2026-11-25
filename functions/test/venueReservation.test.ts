// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import { randomUUID } from "node:crypto";
import { getFirestore } from "firebase-admin/firestore";
import { NotificationMessage, PENDING, sendToUser, UNNAMED_LEADER } from "../src/notify";
import { notifyVenueReservation, SOLO_PARTY_SIZE } from "../src/venueReservation";
import {
  APPROVED,
  BLANK_NAME,
  initTestApp,
  LEADER,
  QUEST_TITLE,
  seedQuest,
  VENUE,
} from "./fixtures";

// The sending itself is sendToUser's, tested in notify.test.ts; the shared helpers stay real
jest.mock("../src/notify", () => ({
  ...jest.requireActual("../src/notify"),
  sendToUser: jest.fn(),
}));

const sent = jest.mocked(sendToUser);

const MISSING_QUEST = "missing-quest";
/** The joinedCount of every new reservation: its creator alone. */
const CREATOR_ONLY = 1;
/** A quest that takes exactly this many explorers. */
const EXACT_PARTY_SIZE = 3;
/** A quest that takes at least this many explorers. */
const AT_LEAST_PARTY_SIZE = 2;

beforeAll(initTestApp);

beforeEach(() => sent.mockReset());

/** Creates the reservation [fields] and runs the trigger on it, as Firestore would. */
async function createAndNotify(fields: Record<string, unknown>): Promise<string> {
  const ref = getFirestore().collection("reservations").doc(randomUUID());
  await ref.set({ venueId: VENUE, joinedCount: CREATOR_ONLY, ...fields });
  await notifyVenueReservation.run({
    params: { reservationId: ref.id },
    data: await ref.get(),
  } as Parameters<typeof notifyVenueReservation.run>[0]);
  return ref.id;
}

function expectSent(body: string, reservationId: string) {
  expect(sent).toHaveBeenCalledTimes(1);
  expect(sent).toHaveBeenCalledWith(VENUE, {
    title: `New request: ${QUEST_TITLE}`,
    body,
    data: { kind: "reservation", route: "venue_dashboard", reservationId },
  } satisfies NotificationMessage);
}

describe("notifyVenueReservation", () => {
  it("tells the venue the party size a new request is for", async () => {
    const questId = await seedQuest({
      minPartySize: EXACT_PARTY_SIZE,
      exactPartySize: true,
    });

    const id = await createAndNotify({
      questId,
      status: PENDING,
      explorerName: LEADER,
    });

    expectSent(`${LEADER} wants to book it for a party of ${EXACT_PARTY_SIZE}.`, id);
  });

  it("names the quest's size alone for one that takes at least a party", async () => {
    const questId = await seedQuest({
      minPartySize: AT_LEAST_PARTY_SIZE,
      exactPartySize: false,
    });

    const id = await createAndNotify({
      questId,
      status: PENDING,
      explorerName: LEADER,
    });

    expectSent(`${LEADER} wants to book it for a party of ${AT_LEAST_PARTY_SIZE}.`, id);
  });

  it("names no party for a quest explorers take alone", async () => {
    const questId = await seedQuest({
      minPartySize: SOLO_PARTY_SIZE,
      exactPartySize: false,
    });

    const id = await createAndNotify({
      questId,
      status: PENDING,
      explorerName: LEADER,
    });

    expectSent(`${LEADER} wants to book it.`, id);
  });

  it("calls a leader whose reservation predates stored names an explorer", async () => {
    const questId = await seedQuest({ minPartySize: SOLO_PARTY_SIZE });

    const id = await createAndNotify({ questId, status: PENDING });

    expectSent(`${UNNAMED_LEADER} wants to book it.`, id);
  });

  it("calls a leader with a blank name an explorer", async () => {
    const questId = await seedQuest({ minPartySize: SOLO_PARTY_SIZE });

    const id = await createAndNotify({
      questId,
      status: PENDING,
      explorerName: BLANK_NAME,
    });

    expectSent(`${UNNAMED_LEADER} wants to book it.`, id);
  });

  it("notifies nobody of a reservation created in another status", async () => {
    const questId = await seedQuest({ minPartySize: SOLO_PARTY_SIZE });

    await createAndNotify({ questId, status: APPROVED, explorerName: LEADER });

    expect(sent).not.toHaveBeenCalled();
  });

  it("rejects without sending when the quest is missing", async () => {
    await expect(
      createAndNotify({
        questId: MISSING_QUEST,
        status: PENDING,
        explorerName: LEADER,
      }),
    ).rejects.toThrow(`Missing quest ${MISSING_QUEST}`);

    expect(sent).not.toHaveBeenCalled();
  });

  it("runs on new reservations only, so later writes notify nobody", () => {
    expect(notifyVenueReservation.__endpoint.eventTrigger).toMatchObject({
      eventType: "google.cloud.firestore.document.v1.created",
      eventFilterPathPatterns: { document: "reservations/{reservationId}" },
    });
  });
});
