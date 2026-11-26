// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import androidx.annotation.StringRes
import com.github.aroundteam2026.aroundapp.R

/**
 * The Android channels notifications are shown on, one per kind and role, as in #631's note. The
 * user can turn each one off in the app's notification settings (#643).
 *
 * @property id the channel's id: never change it, or Android forgets the user's choice for it.
 * @property label the name Android shows for it in the app's notification settings.
 */
enum class NotificationChannelKind(val id: String, @param:StringRes val label: Int) {
  RESERVATIONS("reservations", R.string.notification_channel_reservations),
  PROOFS("proofs", R.string.notification_channel_proofs),
  BOOKING_REQUESTS("booking_requests", R.string.notification_channel_booking_requests),
  PROOFS_TO_REVIEW("proofs_to_review", R.string.notification_channel_proofs_to_review),
  CANCELLATIONS("cancellations", R.string.notification_channel_cancellations),
}

/**
 * A notification sent by the Cloud Functions (#632), read from the `data` of its FCM message.
 *
 * @property reservationId the reservation it is about: a newer notification about the same
 *   reservation, on the same channel, replaces this one.
 * @property channel where it is shown, picked by its kind and the role its route belongs to.
 */
data class AroundNotification(
    val title: String,
    val body: String,
    val reservationId: String,
    val channel: NotificationChannelKind,
) {
  companion object {
    /**
     * Reads the notification in [data], as `sendToUser` in functions/src/notify.ts writes it, or
     * returns null when a field is missing or names a kind or route the app doesn't know.
     *
     * The route tells the role: "quests" is the explorer's tab, "venue_dashboard" the venue's.
     */
    fun fromData(data: Map<String, String>): AroundNotification? {
      val explorer =
          when (data["route"]) {
            "quests" -> true
            "venue_dashboard" -> false
            else -> return null
          }
      val channel =
          when (data["kind"]) {
            "reservation" ->
                if (explorer) NotificationChannelKind.RESERVATIONS
                else NotificationChannelKind.BOOKING_REQUESTS
            "proof" ->
                if (explorer) NotificationChannelKind.PROOFS
                else NotificationChannelKind.PROOFS_TO_REVIEW
            // Only venues are told about cancellations (#641)
            "cancellation" -> if (explorer) return null else NotificationChannelKind.CANCELLATIONS
            else -> return null
          }
      return AroundNotification(
          title = data["title"] ?: return null,
          body = data["body"] ?: return null,
          reservationId = data["reservationId"] ?: return null,
          channel = channel,
      )
    }
  }
}
