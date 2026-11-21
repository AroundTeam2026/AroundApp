// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import { logger } from "firebase-functions";

/** The screen a tapped notification opens; the app reads it in AroundMessagingService (#637). */
export type NotificationRoute = "quests" | "venue_dashboard";

/** What every notification function sends: the text shown, and where a tap leads. */
export interface NotificationMessage {
  title: string;
  body: string;
  data: { route: NotificationRoute; reservationId: string };
}

/**
 * Sends [message] to every phone of the user [uid], whose FCM tokens are saved at
 * users/{uid}/tokens/{token} (#633).
 *
 * Stub: it only logs the message for now, so the notification functions (#638 to #642) can call
 * it and be tested before the sending is written (#632). Tests of those functions mock this
 * module and check what they pass to it.
 */
export async function sendToUser(uid: string, message: NotificationMessage): Promise<void> {
  logger.info("sendToUser is a stub: nothing was sent", {
    uid,
    title: message.title,
    route: message.data.route,
    reservationId: message.data.reservationId,
  });
}
