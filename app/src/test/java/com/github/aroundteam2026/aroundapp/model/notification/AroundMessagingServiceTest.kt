// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Notification
import android.app.NotificationManager
import com.google.firebase.messaging.RemoteMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AroundMessagingServiceTest {
  private val service = Robolectric.buildService(AroundMessagingService::class.java).create().get()
  private val manager = service.getSystemService(NotificationManager::class.java)

  private fun receive(data: Map<String, String>) {
    shadowOf(service.application).grantPermissions(POST_NOTIFICATIONS)
    service.onMessageReceived(RemoteMessage.Builder("test").setData(data).build())
  }

  @Test
  fun showsTheNotificationAMessageCarries() {
    receive(
        mapOf(
            "title" to "Café Lumière approved your reservation",
            "body" to "Latte art, Sat 28 Nov",
            "kind" to "reservation",
            "route" to "quests",
            "reservationId" to "r1",
        )
    )

    val notification = shadowOf(manager).allNotifications.single()
    assertEquals(
        "Café Lumière approved your reservation",
        notification.extras.getString(Notification.EXTRA_TITLE),
    )
    assertEquals("reservations", notification.channelId)
  }

  @Test
  fun ignoresAMessageItCantRead() {
    receive(mapOf("title" to "Hello"))

    assertTrue(shadowOf(manager).allNotifications.isEmpty())
  }
}
