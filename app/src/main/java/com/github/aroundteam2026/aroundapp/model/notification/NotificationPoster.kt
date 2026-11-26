// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.github.aroundteam2026.aroundapp.R

/** Shows [AroundNotification]s, as in #631's frames: the text, under Around's globe in purple. */
class NotificationPoster(private val context: Context) {
  private val manager = NotificationManagerCompat.from(context)

  /**
   * Shows [notification] on its channel, creating the channel the first time. A newer notification
   * about the same reservation, on the same channel, replaces it.
   *
   * Shows nothing on Android 13 and later when the user hasn't allowed notifications (#635).
   */
  fun post(notification: AroundNotification) {
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) != PERMISSION_GRANTED
    ) {
      return
    }
    val channel = notification.channel
    // High: the functions send at high priority so an answer arrives at once, so it pops up too.
    // The user can lower it per channel in the app's notification settings (#643).
    manager.createNotificationChannel(
        NotificationChannelCompat.Builder(channel.id, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(channel.label))
            .build()
    )
    val shown =
        NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_stat_around)
            .setColor(ContextCompat.getColor(context, R.color.brand_purple))
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            // A long body wraps when the notification is expanded, rather than being cut
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
    manager.notify(notification.reservationId, channel.ordinal, shown)
  }

  /**
   * Opens the app as if from the launcher, but on a cleared task, so it starts at home even when it
   * was open on another screen. Home is the screen a route names for its role: the Quests tab for
   * an explorer, the Dashboard for a venue.
   *
   * The cleared task also drops a screen left open, such as a half-filled quest form. Should a
   * route ever name another screen, put it in this intent: MainActivity is recreated, so it would
   * read it in onCreate.
   */
  private fun openApp(): PendingIntent? {
    val intent =
        context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
  }
}
