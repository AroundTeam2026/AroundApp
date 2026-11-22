// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { logger } from "firebase-functions";

/**
 * How long FCM keeps a message for a phone that is off: a day. Past that, a reservation or a proof
 * has likely been answered or ended, and a late notification would only mislead (FCM's default is
 * 4 weeks).
 */
export const MESSAGE_TTL_MS = 24 * 60 * 60 * 1000;

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

/**
 * The FCM errors that mean a token is gone for good: the app was uninstalled or its data cleared.
 * Other errors, e.g. "messaging/internal-error", may pass, so their tokens are kept.
 */
export const GONE_TOKEN_ERRORS: ReadonlySet<string> = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

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
 * maintenance window, and a phone that stays off longer than [MESSAGE_TTL_MS] never gets it.
 *
 * A phone FCM refuses is logged and skipped: the others still get the message. When FCM says the
 * token is gone for good ([GONE_TOKEN_ERRORS]), its document is deleted, so later messages don't
 * try it again and a user's tokens don't pile up. A token that can't be deleted is logged and
 * kept, since the message has already gone out: the next message tries again. It rejects only
 * when the tokens can't be read or FCM can't be reached at all.
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
    android: { priority: "high", ttl: MESSAGE_TTL_MS },
  });

  if (response.failureCount > 0) {
    logger.warn("sendToUser: some phones didn't get the message", {
      uid,
      failed: response.failureCount,
      sent: response.successCount,
      errors: response.responses.flatMap((r) => (r.error ? [r.error.code] : [])),
    });
  }

  // responses[i] answers tokens[i], which is saved.docs[i].id
  const gone = saved.docs.filter((_, i) => {
    const code = response.responses[i].error?.code;
    return code !== undefined && GONE_TOKEN_ERRORS.has(code);
  });
  if (gone.length === 0) return;
  const results = await Promise.allSettled(gone.map((doc) => doc.ref.delete()));
  const failed = results.filter((r) => r.status === "rejected").length;
  if (failed < gone.length) {
    logger.info("sendToUser: deleted the tokens of phones that are gone", {
      uid,
      deleted: gone.length - failed,
    });
  }
  if (failed > 0) {
    logger.warn("sendToUser: couldn't delete some gone tokens", {
      uid,
      failed,
      errors: results.flatMap((r) => (r.status === "rejected" ? [String(r.reason)] : [])),
    });
  }
}
