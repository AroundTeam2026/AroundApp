// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import { DocumentData, getFirestore } from "firebase-admin/firestore";
import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { NotificationMessage, readQuest, sendToUser } from "./notify";
import { joinedMembers } from "./party";

/** The completion statuses that are the venue's answer to a proof. */
const ANSWERS: ReadonlySet<string> = new Set(["APPROVED", "REJECTED"]);

/**
 * Whether the update [before] → [after] of a completion is the venue answering its proof: a
 * pending proof approved or rejected, or a rejected one approved after all. A new proof makes the
 * completion pending again, so it is never an answer.
 */
export function isProofAnswer(
  before: DocumentData | undefined,
  after: DocumentData | undefined,
): boolean {
  return (
    before !== undefined &&
    after !== undefined &&
    ANSWERS.has(after.status) &&
    before.status !== after.status
  );
}

/**
 * A discount amount as the app writes it (RewardAmountText.kt): no decimals when it is whole, else
 * 2 for francs and 1 or 2 for a percentage, e.g. "10", "2.50", "12.5".
 */
function formatAmount(amount: number, unit: unknown): string {
  const hundredths = Math.round(amount * 100);
  const decimals = hundredths % 100 === 0 ? 0 : unit === "CHF" || hundredths % 10 !== 0 ? 2 : 1;
  return (hundredths / 100).toFixed(decimals);
}

/**
 * The quest's [reward], as stored by QuestRepositoryFirestore, in words for the notification, e.g.
 * "10% off", "CHF 5 off", "a free croissant"; null when the quest has no reward or the reward
 * can't be read.
 */
export function rewardText(reward: unknown): string | null {
  if (typeof reward !== "object" || reward === null) return null;
  const r = reward as Record<string, unknown>;
  switch (r.type) {
    case "DISCOUNT": {
      if (typeof r.amount !== "number") return null;
      const amount = formatAmount(r.amount, r.unit);
      if (r.unit === "PERCENT") return `${amount}% off`;
      if (r.unit === "CHF") return `CHF ${amount} off`;
      return null;
    }
    case "FREE_ITEM":
      return typeof r.name === "string" && r.name.trim() ? `a free ${r.name.trim()}` : null;
    case "OTHER":
      return typeof r.description === "string" && r.description.trim()
        ? r.description.trim()
        : null;
    default:
      return null;
  }
}

/** Ends [text] with a period, unless it already ends a sentence. */
function sentence(text: string): string {
  return /[.!?]$/.test(text) ? text : `${text}.`;
}

/**
 * Reads the document [collection]/[id], throwing if it doesn't exist, as readQuest does for a
 * quest: Cloud Functions logs the failure rather than a notification going out without a name.
 */
async function read(collection: string, id: string): Promise<DocumentData> {
  const doc = await getFirestore().collection(collection).doc(id).get();
  if (!doc.exists) throw new Error(`Missing ${collection}/${id}`);
  return doc.data()!;
}

/**
 * The notification for the venue's answer [after] to the proof of the reservation
 * [reservationId], with the texts of #631's Figma ("Proofs" channel):
 * - approved: "Quest completed: {quest}" / "{venue} approved your proof. Your reward: {reward}."
 * - rejected: "{venue} rejected your proof" / "“{reason}” You can send a new one from the Quests
 *   tab."
 */
export async function proofAnswerMessage(
  after: DocumentData,
  reservationId: string,
): Promise<NotificationMessage> {
  const data = { kind: "proof", route: "quests", reservationId } as const;
  const venueName = (await read("venues", after.venueId)).name;

  if (after.status === "REJECTED") {
    return {
      title: `${venueName} rejected your proof`,
      body: `“${after.rejectReason}” You can send a new one from the Quests tab.`,
      data,
    };
  }

  const { questId } = await read("reservations", reservationId);
  const quest = await readQuest(questId);
  const reward = rewardText(quest.get("reward"));
  return {
    title: `Quest completed: ${quest.get("title")}`,
    body: reward
      ? `${venueName} approved your proof. Your reward: ${sentence(reward)}`
      : `${venueName} approved your proof.`,
    data,
  };
}

/**
 * Tells every joined member of the party when the venue answers its proof (#640). A new proof, or
 * any other change, notifies no explorer: the venue hears of a new proof through #642.
 */
export async function sendProofAnswer(
  before: DocumentData | undefined,
  after: DocumentData | undefined,
  reservationId: string,
): Promise<void> {
  if (!isProofAnswer(before, after)) return;
  const uids = await joinedMembers(reservationId);
  if (uids.length === 0) return;
  const message = await proofAnswerMessage(after!, reservationId);
  await Promise.all(uids.map((uid) => sendToUser(uid, message)));
}

// A completion's id is its reservation's (completionId in Completion.kt)
export const notifyProofAnswered = onDocumentUpdated(
  "completions/{reservationId}",
  async (event) => {
    await sendProofAnswer(
      event.data?.before.data(),
      event.data?.after.data(),
      event.params.reservationId,
    );
  },
);
