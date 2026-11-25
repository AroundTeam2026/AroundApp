// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

/**
 * What the notification functions' tests share: the same venue, quest and people, and the steps to
 * store them on the Firestore emulator. Each test gets ids of its own, so tests never see each
 * other's documents.
 *
 * Mocking `sendToUser` stays in each test file, since Jest only hoists a `jest.mock` written there.
 */

export const VENUE = "venue-1";
/** The name [VENUE] stores, once seedVenue has run. */
export const VENUE_NAME = "Café Lumière";
export const QUEST_TITLE = "Find the hidden fox";
/** A quest's reward, as QuestRepositoryFirestore stores it. */
export const FREE_CROISSANT = { type: "FREE_ITEM", name: "croissant" };
/** The explorer who reserved, as `explorerName` stores them. */
export const LEADER = "Yusuf S.";
/** A party member's uid. */
export const EXPLORER = "explorer-1";
/** A reservation's or a proof's status (ReservationStatus, CompletionStatus). */
export const APPROVED = "APPROVED";
export const REJECTED = "REJECTED";
/** A party member's status (PartyMemberStatus in PartyMember.kt). */
export const JOINED = "JOINED";
export const WITHDRAWN = "WITHDRAWN";
/** A display name the rules allow, as they check only its length, but that names nobody. */
export const BLANK_NAME = "  ";

/** Points the Admin SDK at the emulator project, once for every test file. */
export function initTestApp(): void {
  if (getApps().length === 0) initializeApp({ projectId: process.env.GCLOUD_PROJECT });
}

/**
 * Stores a quest titled [QUEST_TITLE], with any other [fields], such as its party size, and
 * returns its id.
 */
export async function seedQuest(fields: Record<string, unknown> = {}): Promise<string> {
  const questId = randomUUID();
  await getFirestore()
    .collection("quests")
    .doc(questId)
    .set({ title: QUEST_TITLE, ...fields });
  return questId;
}

/** Stores [VENUE], named [VENUE_NAME]. */
export async function seedVenue(): Promise<void> {
  await getFirestore().collection("venues").doc(VENUE).set({ name: VENUE_NAME });
}

/**
 * Stores a reservation of [VENUE] for a new quest with any other [questFields], such as its
 * reward, led by [explorerName] with [joinedCount] members, and returns its id.
 */
export async function seedReservation(
  joinedCount: number,
  explorerName: string = LEADER,
  questFields: Record<string, unknown> = {},
): Promise<string> {
  const reservationId = randomUUID();
  const questId = await seedQuest(questFields);
  await getFirestore()
    .collection("reservations")
    .doc(reservationId)
    .set({ questId, venueId: VENUE, explorerName, joinedCount });
  return reservationId;
}

/**
 * Stores the membership of [uid] in the party of [reservationId] with [status], as
 * PartyUserFirestore does; a null [uid] stores a malformed one without it.
 */
export async function seedMembership(
  reservationId: string,
  uid: string | null,
  status: string,
): Promise<void> {
  await getFirestore()
    .collection("party_user")
    .doc(`${reservationId}_${uid ?? randomUUID()}`)
    .set({ reservationId, ...(uid ? { uid } : {}), status, joinedAt: new Date() });
}
