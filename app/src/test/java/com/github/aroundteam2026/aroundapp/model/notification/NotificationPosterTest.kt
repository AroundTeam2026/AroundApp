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

  private val proof =
      AroundNotification(
          "Proof to review: Latte art",
          "Sam sent a photo",
          "r1",
          NotificationChannelKind.PROOFS_TO_REVIEW,
      )

  @Before
  fun allowNotifications() {
    shadowOf(app).grantPermissions(POST_NOTIFICATIONS)
  }

  private fun shown(): List<Notification> = shadowOf(manager).allNotifications

  @Test
  fun showsTheTextOnTheChannelOfItsKind() {
    poster.post(proof)

    val notification = shown().single()
    assertEquals(
        "Proof to review: Latte art",
        notification.extras.getString(Notification.EXTRA_TITLE),
    )
    assertEquals("Sam sent a photo", notification.extras.getString(Notification.EXTRA_TEXT))
    assertEquals("proofs_to_review", notification.channelId)
    assertEquals(R.drawable.ic_stat_around, notification.smallIcon.resId)
    assertEquals(ContextCompat.getColor(app, R.color.brand_purple), notification.color)
    assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
  }

  @Test
  fun createsTheChannelUnderTheNameOfTheFigma() {
    poster.post(proof)

    val channel = manager.getNotificationChannel("proofs_to_review")
    assertEquals("Proofs to review", channel.name)
    assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
  }

  @Test
  fun tappingItOpensTheAppAtHomeOnAClearedTask() {
    poster.post(proof)

    // Home is the screen the route names: the Quests tab for an explorer, the Dashboard for a
    // venue (SessionNavigationTest and VenueHomeTest.opensOnTheDashboard check each starts there)
    val opened = shadowOf(shown().single().contentIntent).savedIntent
    assertEquals(MainActivity::class.java.name, opened.component?.className)
    assertTrue(opened.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    assertTrue(opened.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
  }

  @Test
  fun aNewerNotificationAboutTheSameReservationReplacesTheOlder() {
    poster.post(proof)
    poster.post(proof.copy(body = "Sam sent another photo"))
    poster.post(proof.copy(channel = NotificationChannelKind.CANCELLATIONS))

    assertEquals(
        setOf(
            "Sam sent another photo" to "proofs_to_review",
            "Sam sent a photo" to "cancellations",
        ),
        shown().map { it.extras.getString(Notification.EXTRA_TEXT) to it.channelId }.toSet(),
    )
  }

  @Test
  fun showsNothingWithoutThePermission() {
    shadowOf(app).denyPermissions(POST_NOTIFICATIONS)

    poster.post(proof)

    assertTrue(shown().isEmpty())
  }
}
