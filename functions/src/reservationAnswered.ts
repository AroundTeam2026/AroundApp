// Co-authored-by: OpenAI Codex <noreply@openai.com>

import { DocumentData, getFirestore } from "firebase-admin/firestore";
import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { sendToUser } from "./notify";

/**
 * Notifies the current party when a venue answers its reservation. Membership lives in
 * `party_user`, not on the reservation; withdrawn members must not receive the answer.
 */
export async function sendReservationAnswer(
  before: DocumentData | undefined,
  after: DocumentData | undefined,
  reservationId: string,
): Promise<void> {
  if (before?.status !== "PENDING") return;
  if (after?.status !== "APPROVED" && after?.status !== "REJECTED") return;

  const members = await getFirestore()
    .collection("party_user")
    .where("reservationId", "==", reservationId)
    .where("status", "==", "JOINED")
    .get();
  const uids = new Set(
    members.docs
      .map((doc) => doc.get("uid"))
      .filter((uid): uid is string => typeof uid === "string" && uid.length > 0),
  );
  const approved = after.status === "APPROVED";
  await Promise.all(
    [...uids].map((uid) =>
      sendToUser(uid, {
        title: approved ? "Reservation approved" : "Reservation declined",
        body: approved
          ? "Your party's reservation was approved."
          : "Your party's reservation was declined.",
        data: { kind: "reservation", route: "quests", reservationId },
      }),
    ),
  );
}

export const notifyReservationAnswered = onDocumentUpdated(
  "reservations/{reservationId}",
  async (event) => {
    await sendReservationAnswer(
      event.data?.before.data(),
      event.data?.after.data(),
      event.params.reservationId,
    );
  },
);
