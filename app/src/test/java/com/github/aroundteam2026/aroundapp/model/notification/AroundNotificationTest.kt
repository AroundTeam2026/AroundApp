// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AroundNotificationTest {
  /** [PROOF_TO_REVIEW_DATA] with another [kind] or [route]. */
  private fun data(kind: String = "proof", route: String = "venue_dashboard") =
      PROOF_TO_REVIEW_DATA + mapOf("kind" to kind, "route" to route)

  @Test
  fun readsTheTextAndTheReservation() {
    assertEquals(PROOF_TO_REVIEW, AroundNotification.fromData(PROOF_TO_REVIEW_DATA))
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
