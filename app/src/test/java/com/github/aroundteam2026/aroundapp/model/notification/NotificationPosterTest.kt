// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.github.aroundteam2026.aroundapp.MainActivity
import com.github.aroundteam2026.aroundapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Runs on Android 14 (robolectric.properties), where notifications need the user's permission. */
@RunWith(RobolectricTestRunner::class)
class NotificationPosterTest {
  private val app = ApplicationProvider.getApplicationContext<Application>()
  private val manager = app.getSystemService(NotificationManager::class.java)
  private val poster = NotificationPoster(app)

  @Before
  fun allowNotifications() {
    shadowOf(app).grantPermissions(POST_NOTIFICATIONS)
  }

  private fun shown(): List<Notification> = shadowOf(manager).allNotifications

  @Test
  fun showsTheTextOnTheChannelOfItsKind() {
    poster.post(PROOF_TO_REVIEW)

    val notification = shown().single()
    assertEquals(PROOF_TO_REVIEW.title, notification.extras.getString(Notification.EXTRA_TITLE))
    assertEquals(PROOF_TO_REVIEW.body, notification.extras.getString(Notification.EXTRA_TEXT))
    assertEquals(NotificationChannelKind.PROOFS_TO_REVIEW.id, notification.channelId)
    assertEquals(R.drawable.ic_stat_around, notification.smallIcon.resId)
    assertEquals(ContextCompat.getColor(app, R.color.brand_purple), notification.color)
    assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
  }

  @Test
  fun createsTheChannelUnderItsName() {
    poster.post(PROOF_TO_REVIEW)

    val channel = manager.getNotificationChannel(NotificationChannelKind.PROOFS_TO_REVIEW.id)
    assertEquals(app.getString(R.string.notification_channel_proofs_to_review), channel.name)
    assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
  }

  @Test
  fun tappingItOpensTheAppOnAClearedTask() {
    poster.post(PROOF_TO_REVIEW)

    // MainActivityTest.tappingANotificationOpensTheQuestsTab checks the screen it lands on
    val opened = shadowOf(shown().single().contentIntent).savedIntent
    assertEquals(MainActivity::class.java.name, opened.component?.className)
    assertTrue(opened.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    assertTrue(opened.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
  }

  @Test
  fun aNewerNotificationAboutTheSameReservationReplacesTheOlder() {
    poster.post(PROOF_TO_REVIEW)
    poster.post(PROOF_TO_REVIEW.copy(body = "Sam sent another photo."))
    poster.post(PROOF_TO_REVIEW.copy(channel = NotificationChannelKind.CANCELLATIONS))

    assertEquals(
        setOf(
            "Sam sent another photo." to NotificationChannelKind.PROOFS_TO_REVIEW.id,
            PROOF_TO_REVIEW.body to NotificationChannelKind.CANCELLATIONS.id,
        ),
        shown().map { it.extras.getString(Notification.EXTRA_TEXT) to it.channelId }.toSet(),
    )
  }

  @Test
  fun showsNothingWithoutThePermission() {
    shadowOf(app).denyPermissions(POST_NOTIFICATIONS)

    poster.post(PROOF_TO_REVIEW)

    assertTrue(shown().isEmpty())
  }
}
