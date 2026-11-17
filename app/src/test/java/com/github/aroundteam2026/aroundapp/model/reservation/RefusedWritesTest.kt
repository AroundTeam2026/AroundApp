// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import org.junit.Assert.assertEquals
import org.junit.Test

/** Telling a refused write apart with [RefusedWrites]. */
class RefusedWritesTest {
  private val writes = RefusedWrites().apply { record("r1", "quest") }

  private fun listed(status: ReservationStatus = ReservationStatus.PENDING) =
      listOf(newPendingReservation("quest", "venue", "Ada", 0L).copy(id = "r1", status = status))

  @Test
  fun aWriteNotListedYetIsNotRefused() {
    assertEquals(emptySet<String>(), writes.refusedIn(emptyList()))
  }

  @Test
  fun aWriteListedAsPendingThenGoneIsRefusedOnce() {
    writes.refusedIn(listed())

    assertEquals(setOf("quest"), writes.refusedIn(emptyList()))
    assertEquals(emptySet<String>(), writes.refusedIn(emptyList()))
  }

  @Test
  fun aWriteTheVenueAnsweredOrTheExplorerCancelledIsNotRefused() {
    for (status in listOf(ReservationStatus.APPROVED, ReservationStatus.CANCELLED)) {
      val answered = RefusedWrites().apply { record("r1", "quest") }
      answered.refusedIn(listed())
      answered.refusedIn(listed(status))

      assertEquals(emptySet<String>(), answered.refusedIn(emptyList()))
    }
  }

  @Test
  fun clearForgetsTheWrites() {
    writes.refusedIn(listed())

    writes.clear()

    assertEquals(emptySet<String>(), writes.refusedIn(emptyList()))
  }
}
