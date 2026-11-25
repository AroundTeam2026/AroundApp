// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import { DocumentData, getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { APPROVED, NotificationMessage, PENDING, REJECTED, sendToUser } from "./notify";
import { joinedMembers } from "./party";

/**
 * The notification for a reservation [approved] or declined, with the venue's name and the
 * quest's title as in Figma (#638). Without either, e.g. once a venue deleted the quest and then
 * declined its pending reservations, it says the same without the names, so the party still hears
 * back.
 */
function answerText(
  approved: boolean,
  venueName: string | undefined,
  questTitle: string | undefined,
): Pick<NotificationMessage, "title" | "body"> {
  if (venueName && questTitle) {
    return approved
      ? {
          title: `${venueName} approved your reservation`,
          body: `${questTitle} · Head over and send your proof once you're there.`,
        }
      : {
          title: `${venueName} declined your reservation`,
          body: `${questTitle} · Have a look at other quests nearby.`,
        };
  }
  return approved
    ? { title: "Reservation approved", body: "Your party's reservation was approved." }
    : { title: "Reservation declined", body: "Your party's reservation was declined." };
}

/** The non-blank text [field] of the document at [path], or undefined if it or the field is missing. */
async function textOf(path: string, field: string): Promise<string | undefined> {
  const value = (await getFirestore().doc(path).get()).get(field);
  return typeof value === "string" && value.trim() ? value : undefined;
}

/**
 * Notifies the current party when a venue answers its reservation. Membership lives in
 * `party_user`, not on the reservation; withdrawn members must not receive the answer.
 *
 * Every member is tried: a member whose send fails is logged with the reservation, and the others
 * still get the answer.
 */
export async function sendReservationAnswer(
  before: DocumentData | undefined,
  after: DocumentData | undefined,
  reservationId: string,
): Promise<void> {
  if (before?.status !== PENDING) return;
  if (after?.status !== APPROVED && after?.status !== REJECTED) return;

  const [uids, venueName, questTitle] = await Promise.all([
    joinedMembers(reservationId),
    typeof after.venueId === "string" ? textOf(`venues/${after.venueId}`, "name") : undefined,
    typeof after.questId === "string" ? textOf(`quests/${after.questId}`, "title") : undefined,
  ]);
  const message: NotificationMessage = {
    ...answerText(after.status === APPROVED, venueName, questTitle),
    data: { kind: "reservation", route: "quests", reservationId },
  };

  const results = await Promise.allSettled(uids.map((uid) => sendToUser(uid, message)));
  results.forEach((result, i) => {
    if (result.status === "rejected") {
      logger.warn("notifyReservationAnswered: a member didn't get the answer", {
        uid: uids[i],
        reservationId,
        error: String(result.reason),
      });
    }
  });
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
