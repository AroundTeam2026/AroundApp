// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { logger } from "firebase-functions";

/** The screen a tapped notification opens; the app reads it in AroundMessagingService (#637). */
export type NotificationRoute = "quests" | "venue_dashboard";

/**
 * What the notification is about. With the user's role, the app picks its Android channel from it
 * (#637), one per row of #631's Figma, so the user can turn each off on its own (#643):
 * - "reservation": "Reservations" for an explorer (#638), "Booking requests" for a venue (#639)
 * - "proof": "Proofs" for an explorer (#640), "Proofs to review" for a venue (#642)
 * - "cancellation": "Cancellations", for a venue (#641)
 */
export type NotificationKind = "reservation" | "proof" | "cancellation";

/** What every notification function sends: the text shown, and where a tap leads. */
export interface NotificationMessage {
  title: string;
  body: string;
  data: { kind: NotificationKind; route: NotificationRoute; reservationId: string };
}

/**
 * Sends [message] to every phone of the user [uid], whose FCM tokens are the ids of the documents
 * at users/{uid}/tokens/{token} (#633). A user with no token, e.g. signed out everywhere, gets
 * nothing.
 *
 * The message is data-only: title, body, kind, route and reservationId all go in FCM's `data`.
 * Android then calls AroundMessagingService.onMessageReceived even when the app is in the
 * background, so the app builds the notification itself, on the channel of its kind and the
 * user's role (#637). High priority makes Android deliver it at once rather than at the next
 * maintenance window.
 *
 * A phone FCM refuses (e.g. an expired token) is logged and skipped: the others still get the
 * message. It rejects only when FCM can't be reached at all, or the tokens can't be read.
 */
export async function sendToUser(uid: string, message: NotificationMessage): Promise<void> {
  const saved = await getFirestore().collection("users").doc(uid).collection("tokens").get();
  const tokens = saved.docs.map((doc) => doc.id);
  if (tokens.length === 0) {
    logger.info("sendToUser: the user has no phone to notify", { uid });
    return;
  }

  const response = await getMessaging().sendEachForMulticast({
    tokens,
    data: {
      title: message.title,
      body: message.body,
      kind: message.data.kind,
      route: message.data.route,
      reservationId: message.data.reservationId,
    },
    android: { priority: "high" },
  });

  if (response.failureCount > 0) {
    logger.warn("sendToUser: some phones didn't get the message", {
      uid,
      failed: response.failureCount,
      sent: response.successCount,
      errors: response.responses.flatMap((r) => (r.error ? [r.error.code] : [])),
    });
  }
}
