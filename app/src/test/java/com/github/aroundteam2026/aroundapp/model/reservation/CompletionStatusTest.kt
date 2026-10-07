// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import com.github.aroundteam2026.aroundapp.model.reservation.CompletionStatus.APPROVED
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionStatus.PENDING
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionStatus.REJECTED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests [canTransition] and the construction rules of [Completion]. */
class CompletionStatusTest {
  private val allowed = setOf(PENDING to APPROVED, PENDING to REJECTED)

  /** Builds a completion with the given [status] and [rejectReason]; other fields are fixed. */
  private fun completion(status: CompletionStatus, rejectReason: String?) =
      Completion(
          id = "c1",
          reservationId = "r1",
          questId = "q1",
          venueId = "v1",
          explorerUid = "u1",
          proofUrl = "https://example.com/proof.jpg",
          status = status,
          rejectReason = rejectReason,
          submittedAt = 0L,
      )

  /** Checks every pair of statuses, so an extra or a missing transition fails the test. */
  @Test
  fun canTransition_allowsExactlyTheTransitionsOfTheSchema() {
    for (from in CompletionStatus.entries) {
      for (to in CompletionStatus.entries) {
        assertEquals("$from -> $to", (from to to) in allowed, canTransition(from, to))
      }
    }
  }

  /** A rejection must say why, so a null reason is refused. */
  @Test(expected = IllegalArgumentException::class)
  fun rejectedCompletion_withoutReason_isRejected() {
    completion(REJECTED, null)
  }

  /** A reason made only of spaces says nothing, so it is refused too. */
  @Test(expected = IllegalArgumentException::class)
  fun rejectedCompletion_withBlankReason_isRejected() {
    completion(REJECTED, "  ")
  }

  /** A rejection with a real reason is valid and keeps the reason. */
  @Test
  fun rejectedCompletion_withReason_isAccepted() {
    assertEquals("Photo is blurry", completion(REJECTED, "Photo is blurry").rejectReason)
  }

  /** The reason is only required for rejections, so a pending completion needs none. */
  @Test
  fun pendingCompletion_withoutReason_isAccepted() {
    assertNull(completion(PENDING, null).rejectReason)
  }
}
