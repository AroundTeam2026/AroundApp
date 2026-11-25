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
export const QUEST_TITLE = "Find the hidden fox";
/** The explorer who reserved, as `explorerName` stores them. */
export const LEADER = "Yusuf S.";
/** A party member's uid. */
export const EXPLORER = "explorer-1";
/** A display name the rules allow, as they check only its length, but that names nobody. */
export const BLANK_NAME = "  ";

/** Points the Admin SDK at the emulator project, once for every test file. */
export function initTestApp(): void {
  if (getApps().length === 0) initializeApp({ projectId: process.env.GCLOUD_PROJECT });
}

/** Stores a quest titled [title] and returns its id. */
export async function seedQuest(title: string = QUEST_TITLE): Promise<string> {
  const questId = randomUUID();
  await getFirestore().collection("quests").doc(questId).set({ title });
  return questId;
}

/**
 * Stores a reservation of [VENUE] for a new quest, led by [explorerName] with [joinedCount]
 * members, and returns its id.
 */
export async function seedReservation(
  joinedCount: number,
  explorerName: string = LEADER,
): Promise<string> {
  const reservationId = randomUUID();
  const questId = await seedQuest();
  await getFirestore()
    .collection("reservations")
    .doc(reservationId)
    .set({ questId, venueId: VENUE, explorerName, joinedCount });
  return reservationId;
}
