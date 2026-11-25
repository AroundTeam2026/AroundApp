// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import com.github.aroundteam2026.aroundapp.model.reservation.FakeProofUploadQueue.QueuedProof
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests [FakeProofUploadQueue] against the [ProofUploadQueue] contract, one behaviour per test. */
class FakeProofUploadQueueTest {
  private lateinit var queue: FakeProofUploadQueue

  /** Gives every test a fresh queue with nothing waiting. */
  @Before
  fun setUp() {
    queue = FakeProofUploadQueue()
  }

  @Test
  fun startsWithNothingWaitingOrFailed() = runTest {
    assertTrue(queue.waiting.first().isEmpty())
    assertTrue(queue.failed.first().isEmpty())
    assertTrue(queue.queued.isEmpty())
  }

  @Test
  fun enqueue_recordsTheProofAndListsItsReservationAsWaiting() = runTest {
    val result = queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)

    assertTrue(result.isSuccess)
    assertEquals(listOf(QueuedProof(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)), queue.queued)
    assertEquals(setOf(RESERVATION_ID), queue.waiting.first())
  }

  @Test
  fun enqueue_whileTheReservationIsWaiting_replacesTheProof() = runTest {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)

    val result = queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, RETAKE)

    assertTrue(result.isSuccess)
    assertEquals(listOf(PHOTO, RETAKE), queue.queued.map { it.photo })
    assertEquals(setOf(RESERVATION_ID), queue.waiting.first())
  }

  @Test
  fun enqueue_afterTheReservationFinished_queuesTheNewProof() = runTest {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)
    queue.finish(RESERVATION_ID)

    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, RETAKE)

    assertEquals(listOf(PHOTO, RETAKE), queue.queued.map { it.photo })
    assertEquals(setOf(RESERVATION_ID), queue.waiting.first())
  }

  @Test
  fun enqueue_whenFailureIsForced_returnsItAndQueuesNothing() = runTest {
    val failure = IllegalStateException(FAILURE)
    queue.forcedFailure = failure

    assertEquals(
        failure,
        queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO).exceptionOrNull(),
    )
    assertTrue(queue.queued.isEmpty())
    assertTrue(queue.waiting.first().isEmpty())
  }

  @Test
  fun forcedFailure_setBackToNull_letsEnqueueSucceedAgain() = runTest {
    queue.forcedFailure = IllegalStateException(FAILURE)
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)

    queue.forcedFailure = null

    assertTrue(queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO).isSuccess)
  }

  @Test
  fun finish_removesOnlyThatReservationFromWaiting() = runTest {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)
    queue.enqueue(OTHER_RESERVATION_ID, VENUE_ID, EXPLORER_UID, RETAKE)

    queue.finish(RESERVATION_ID)

    assertEquals(setOf(OTHER_RESERVATION_ID), queue.waiting.first())
  }

  @Test
  fun fail_movesTheReservationFromWaitingToFailed() = runTest {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)
    queue.enqueue(OTHER_RESERVATION_ID, VENUE_ID, EXPLORER_UID, RETAKE)

    queue.fail(RESERVATION_ID, UploadFailure.REFUSED)

    assertEquals(setOf(OTHER_RESERVATION_ID), queue.waiting.first())
    assertEquals(mapOf(RESERVATION_ID to UploadFailure.REFUSED), queue.failed.first())
  }

  @Test
  fun enqueue_afterAFailure_removesTheReservationFromFailed() = runTest {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)
    queue.fail(RESERVATION_ID)

    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, RETAKE)

    assertTrue(queue.failed.first().isEmpty())
    assertEquals(setOf(RESERVATION_ID), queue.waiting.first())
  }

  @Test
  fun retry_queuesTheFailedProofAgainWithTheSameArguments() = runTest {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)
    queue.fail(RESERVATION_ID)

    assertTrue(queue.retry(RESERVATION_ID).isSuccess)

    val proof = QueuedProof(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)
    assertEquals(listOf(proof, proof), queue.queued)
    assertEquals(setOf(RESERVATION_ID), queue.waiting.first())
    assertTrue(queue.failed.first().isEmpty())
  }

  @Test
  fun retry_afterAMissingPhoto_failsAndStaysFailed() = runTest {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)
    queue.fail(RESERVATION_ID, UploadFailure.PHOTO_MISSING)

    assertTrue(queue.retry(RESERVATION_ID).exceptionOrNull() is FileNotFoundException)

    assertEquals(1, queue.queued.size)
    assertEquals(mapOf(RESERVATION_ID to UploadFailure.PHOTO_MISSING), queue.failed.first())
  }

  @Test
  fun retry_whileTheProofIsWaiting_failsAndQueuesNothing() = runTest {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)

    val result = queue.retry(RESERVATION_ID)

    assertTrue(result.exceptionOrNull() is NoSuchElementException)
    assertEquals(1, queue.queued.size)
  }

  @Test
  fun cancel_recordsItAndRemovesOnlyThatReservationFromWaiting() = runTest {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)
    queue.enqueue(OTHER_RESERVATION_ID, VENUE_ID, EXPLORER_UID, RETAKE)

    assertTrue(queue.cancel(RESERVATION_ID).isSuccess)

    assertEquals(listOf(RESERVATION_ID), queue.cancelled)
    assertEquals(setOf(OTHER_RESERVATION_ID), queue.waiting.first())
  }

  @Test
  fun cancel_afterAFailure_removesTheReservationFromFailed() = runTest {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)
    queue.fail(RESERVATION_ID)

    assertTrue(queue.cancel(RESERVATION_ID).isSuccess)

    assertTrue(queue.failed.first().isEmpty())
    assertTrue(queue.retry(RESERVATION_ID).exceptionOrNull() is NoSuchElementException)
  }

  @Test
  fun cancelAll_afterAFailure_emptiesFailed() = runTest {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)
    queue.fail(RESERVATION_ID)

    assertTrue(queue.cancelAll().isSuccess)

    assertTrue(queue.failed.first().isEmpty())
  }

  @Test
  fun cancel_whenFailureIsForced_returnsItAndCancelsNothing() = runTest {
    val failure = IllegalStateException(FAILURE)
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)
    queue.cancelFailure = failure

    assertEquals(failure, queue.cancel(RESERVATION_ID).exceptionOrNull())
    assertTrue(queue.cancelled.isEmpty())
    assertEquals(setOf(RESERVATION_ID), queue.waiting.first())
  }

  @Test
  fun cancelAll_countsItAndEmptiesWaiting() = runTest {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)
    queue.enqueue(OTHER_RESERVATION_ID, VENUE_ID, EXPLORER_UID, RETAKE)

    assertTrue(queue.cancelAll().isSuccess)

    assertEquals(1, queue.cancelAllCount)
    assertTrue(queue.waiting.first().isEmpty())
  }

  @Test
  fun cancelAll_whenFailureIsForced_returnsItAndCancelsNothing() = runTest {
    val failure = IllegalStateException(FAILURE)
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)
    queue.cancelFailure = failure

    assertEquals(failure, queue.cancelAll().exceptionOrNull())
    assertEquals(0, queue.cancelAllCount)
    assertEquals(setOf(RESERVATION_ID), queue.waiting.first())
  }

  @Test
  fun waiting_reportsEachChange() = runTest {
    val seen = mutableListOf<Set<String>>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      queue.waiting.collect { seen += it }
    }

    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)
    queue.finish(RESERVATION_ID)

    assertEquals(listOf(emptySet(), setOf(RESERVATION_ID), emptySet<String>()), seen)
  }

  private companion object {
    const val RESERVATION_ID = "reservation-1"
    const val OTHER_RESERVATION_ID = "reservation-2"
    const val VENUE_ID = "cafe"
    const val EXPLORER_UID = "yusuf"
    const val FAILURE = "no space left"
    val PHOTO = File("proofs/proof-1.jpg")
    val RETAKE = File("proofs/proof-2.jpg")
  }
}
