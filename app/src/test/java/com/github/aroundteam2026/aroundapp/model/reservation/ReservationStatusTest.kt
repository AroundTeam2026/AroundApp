// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus.APPROVED
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus.CANCELLED
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus.PENDING
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus.REJECTED
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests [canTransition] for reservations against the transition table of the schema. */
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
}
