// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import { DocumentSnapshot, getFirestore } from "firebase-admin/firestore";
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
 * The FCM errors that mean a phone's ID is gone for good: the app was uninstalled or its data
 * cleared. Other errors, e.g. "messaging/internal-error", may pass, so their IDs are kept.
 *
 * The registration-token codes stay because a phone may still save an old-style token. No FCM doc
 * says so, but the Android SDK's code does: in firebase-messaging 25.1.2,
 * GmsRegistrationClient.registerInternal() falls back to the token path when Play services is older
 * than 261200000 (26.12), even through register() (#634). How FCM answers such a token sent in
 * `fids` isn't confirmed, so these codes may never come up.
 */
export const GONE_TOKEN_ERRORS: ReadonlySet<string> = new Set([
  "messaging/installation-id-not-registered",
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

/**
 * The status of a reservation, or of a proof, waiting for the venue's answer: the only one a
 * reservation is created with, and the one a proof has when it is sent.
 */
export const PENDING = "PENDING";

/** The venue's yes to a reservation, or to a proof. */
export const APPROVED = "APPROVED";

/** The venue's no to a reservation, or to a proof. */
export const REJECTED = "REJECTED";

/** A reservation withdrawn by its party or its venue. Final. */
export const CANCELLED = "CANCELLED";

/**
 * What the venue's notifications call the explorer who reserved when [storedName] finds no name,
 * so every notification shows a nameless leader the same way.
 */
export const UNNAMED_LEADER = "An explorer";

/**
 * The reservation's stored explorerName, or null when it names nobody: missing on reservations
 * made before names were stored, or blank, as the rules only check a name's length.
 */
export function storedName(explorerName: unknown): string | null {
  return typeof explorerName === "string" && explorerName.trim() ? explorerName : null;
}

/**
 * Reads the quest [questId] a reservation is for. A missing quest throws, so Cloud Functions logs
 * the failure rather than a notification going out without the quest's title.
 */
export async function readQuest(questId: string): Promise<DocumentSnapshot> {
  const quest = await getFirestore().collection("quests").doc(questId).get();
  if (!quest.exists) throw new Error(`Missing quest ${questId}`);
  return quest;
}

/** [value] if it is text that isn't blank, else undefined: a name or a title worth showing. */
export function nonBlank(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() ? value : undefined;
}

/**
 * The non-blank text [field] of the document at [path], or undefined if it or the field is
 * missing, e.g. a quest the venue deleted: a notification then says the same without that name
 * rather than not going out.
 */
export async function textOf(path: string, field: string): Promise<string | undefined> {
  return nonBlank((await getFirestore().doc(path).get()).get(field));
}

/** What every notification function sends: the text shown, and where a tap leads. */
export interface NotificationMessage {
  title: string;
  body: string;
  data: { kind: NotificationKind; route: NotificationRoute; reservationId: string };
}

/**
 * Sends [message] to every phone of the user [uid]. Each phone is saved as the id of a document at
 * users/{uid}/tokens/{token} (#633): the ID FCM registered it under, its Firebase Installation ID
 * (#634), sent as FCM's `fids`. A user with no phone saved, e.g. signed out everywhere, gets
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
  const fids = saved.docs.map((doc) => doc.id);
  if (fids.length === 0) {
    logger.info("sendToUser: the user has no phone to notify", { uid });
    return;
  }

  const response = await getMessaging().sendEachForMulticast({
    fids,
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

  // responses[i] answers fids[i], which is saved.docs[i].id
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
