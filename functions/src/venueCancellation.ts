// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import { onDocumentUpdatedWithAuthContext } from "firebase-functions/v2/firestore";
import { CANCELLED, sendToUser, storedName, textOf, UNNAMED_LEADER } from "./notify";

/** The title of the venue's notification, the same whatever the quest. */
export const CANCELLATION_TITLE = "Booking cancelled";

/** What the venue reads: [leader] cancelled the quest [title], or a deleted quest's booking. */
export function cancellationBody(leader: string, title?: string): string {
  return title
    ? `${leader} cancelled their booking of ${title}.`
    : `${leader} cancelled their booking.`;
}

/**
 * Tells the venue a party cancelled its reservation, pending or approved: "Booking cancelled" and
 * "{leader} cancelled their booking of {quest}.".
 *
 * Only the move to CANCELLED notifies, so a member joining or leaving, or the venue's answer,
 * notifies nobody. The venue may cancel a pending reservation itself; it made that change, so it
 * gets nothing (authId is the uid of whoever wrote it).
 *
 * Unlike a new request, a missing quest doesn't stop it: a venue may delete a quest that still
 * has reservations, and the venue must still hear that the party won't come. The body then names
 * no quest.
 */
export const notifyVenueCancellation = onDocumentUpdatedWithAuthContext(
  "reservations/{reservationId}",
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (after?.status !== CANCELLED || before?.status === CANCELLED) return;
    if (event.authId === after.venueId) return;

    const leader = storedName(after.explorerName) ?? UNNAMED_LEADER;
    await sendToUser(after.venueId, {
      title: CANCELLATION_TITLE,
      body: cancellationBody(leader, await textOf(`quests/${after.questId}`, "title")),
      data: {
        kind: "cancellation",
        route: "venue_dashboard",
        reservationId: event.params.reservationId,
      },
    });
  },
);
