// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { sendToUser } from "./notify";

/** Tell the venue when a party submits or replaces its proof, not when the venue answers it. */
export const notifyVenueProof = onDocumentWritten("completions/{reservationId}", async (event) => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (!after || after.status !== "PENDING") return;

  // Every submission, from the app or the upload worker, writes a new submittedAt; the venue's
  // answer only changes status and rejectReason. So a pending proof with the same submittedAt is
  // not a new one.
  const sameProof =
    before?.status === "PENDING" &&
    (before.submittedAt as Timestamp | undefined)?.isEqual(after.submittedAt);
  if (sameProof) return;

  const reservationId = event.params.reservationId;
  const db = getFirestore();
  const reservation = await db.collection("reservations").doc(reservationId).get();
  if (!reservation.exists) throw new Error(`Missing reservation ${reservationId}`);
  const { questId, venueId, explorerName, joinedCount } = reservation.data()!;
  const quest = await db.collection("quests").doc(questId).get();
  if (!quest.exists) throw new Error(`Missing quest ${questId}`);

  const sender = typeof explorerName === "string" && explorerName.trim() ? explorerName : null;
  const subject =
    joinedCount > 1 ? (sender ? `${sender}'s party` : "A party") : sender ?? "An explorer";
  const proof = after.photoUrl ? "a photo" : "a written answer";
  await sendToUser(venueId, {
    title: `Proof to review: ${quest.get("title")}`,
    body: `${subject} sent ${proof}.`,
    data: { kind: "proof", route: "venue_dashboard", reservationId },
  });
});
