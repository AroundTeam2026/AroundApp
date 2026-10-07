// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import com.github.aroundteam2026.aroundapp.model.reservation.CompletionStatus.APPROVED
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionStatus.PENDING
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionStatus.REJECTED
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests [canTransition] for completions against the transition table of the schema. */
class CompletionStatusTest {
  private val allowed = setOf(PENDING to APPROVED, PENDING to REJECTED)

  /** Checks every pair of statuses, so an extra or a missing transition fails the test. */
  @Test
  fun canTransition_allowsExactlyTheTransitionsOfTheSchema() {
    for (from in CompletionStatus.entries) {
      for (to in CompletionStatus.entries) {
        assertEquals("$from -> $to", (from to to) in allowed, canTransition(from, to))
      }
    }
  }
}
