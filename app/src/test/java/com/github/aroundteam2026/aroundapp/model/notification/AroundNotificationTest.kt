// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AroundNotificationTest {
  /** A message as sendToUser in functions/src/notify.ts sends it. */
  private fun data(kind: String = "proof", route: String = "venue_dashboard") =
      mapOf(
          "title" to "Proof to review: Latte art",
          "body" to "Sam sent a photo",
          "kind" to kind,
          "route" to route,
          "reservationId" to "r1",
      )

  @Test
  fun readsTheTextAndTheReservation() {
    assertEquals(
        AroundNotification(
            "Proof to review: Latte art",
            "Sam sent a photo",
            "r1",
            NotificationChannelKind.PROOFS_TO_REVIEW,
        ),
        AroundNotification.fromData(data()),
    )
  }

  @Test
  fun picksTheChannelOfTheKindAndTheRole() {
    val channels =
        mapOf(
            ("reservation" to "quests") to NotificationChannelKind.RESERVATIONS,
            ("proof" to "quests") to NotificationChannelKind.PROOFS,
            ("reservation" to "venue_dashboard") to NotificationChannelKind.BOOKING_REQUESTS,
            ("proof" to "venue_dashboard") to NotificationChannelKind.PROOFS_TO_REVIEW,
            ("cancellation" to "venue_dashboard") to NotificationChannelKind.CANCELLATIONS,
        )
    channels.forEach { (message, channel) ->
      val (kind, route) = message
      assertEquals(
          message.toString(),
          channel,
          AroundNotification.fromData(data(kind, route))?.channel,
      )
    }
  }

  @Test
  fun anExplorerIsNeverToldAboutACancellation() {
    assertNull(AroundNotification.fromData(data("cancellation", "quests")))
  }

  @Test
  fun ignoresAnUnknownKindOrRoute() {
    assertNull(AroundNotification.fromData(data(kind = "chat")))
    assertNull(AroundNotification.fromData(data(route = "profile")))
  }

  @Test
  fun ignoresAMessageMissingAField() {
    listOf("title", "body", "kind", "route", "reservationId").forEach { field ->
      assertNull(field, AroundNotification.fromData(data() - field))
    }
  }
}
