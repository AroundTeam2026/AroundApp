// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus.APPROVED
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus.CANCELLED
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus.PENDING
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus.REJECTED
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests [canTransition] for reservations and the construction rules of [Reservation]. */
class ReservationStatusTest {
  private val allowed =
      setOf(
          PENDING to APPROVED,
          PENDING to REJECTED,
          PENDING to CANCELLED,
          APPROVED to CANCELLED,
      )

  /** Checks every pair of statuses, so an extra or a missing transition fails the test. */
  @Test
  fun canTransition_allowsExactlyTheTransitionsOfTheSchema() {
    for (from in ReservationStatus.entries) {
      for (to in ReservationStatus.entries) {
        assertEquals("$from -> $to", (from to to) in allowed, canTransition(from, to))
      }
    }
  }

  /** A party always has someone in it, so an empty list of explorers is refused. */
  @Test(expected = IllegalArgumentException::class)
  fun reservation_withNoExplorers_isRejected() {
    Reservation("r1", "q1", "v1", emptyList(), 0L, PENDING, 0L)
  }

  /** A reservation with at least one explorer is valid. */
  @Test
  fun reservation_withAnExplorer_isAccepted() {
    val reservation = Reservation("r1", "q1", "v1", listOf("u1"), 0L, PENDING, 0L)
    assertEquals(listOf("u1"), reservation.explorerUids)
  }
}
