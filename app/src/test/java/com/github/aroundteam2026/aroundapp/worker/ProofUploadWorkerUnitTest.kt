// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests the tags that tell which reservation a [ProofUploadWorker] run uploads for, without a
 * device. Its runs are tested on one, in `androidTest`.
 */
class ProofUploadWorkerUnitTest {

  @Test
  fun reservationIdOf_theUniqueWorkName_returnsTheReservationId() {
    assertEquals(
        RESERVATION_ID,
        ProofUploadWorker.reservationIdOf(ProofUploadWorker.uniqueWorkName(RESERVATION_ID)),
    )
  }

  @Test
  fun reservationIdOf_theSharedTag_returnsNull() {
    assertNull(ProofUploadWorker.reservationIdOf(ProofUploadWorker.TAG))
  }

  @Test
  fun reservationIdOf_aNameWithoutAReservationId_returnsNull() {
    assertNull(ProofUploadWorker.reservationIdOf(ProofUploadWorker.uniqueWorkName(EMPTY)))
  }

  @Test
  fun reservationIdOf_theWorkerClassTag_returnsNull() {
    // WorkManager tags every run with its worker's class name itself, so a run's tags hold it too
    assertNull(ProofUploadWorker.reservationIdOf(ProofUploadWorker::class.java.name))
  }

  private companion object {
    const val RESERVATION_ID = "reservation-1"
    const val EMPTY = ""
  }
}
