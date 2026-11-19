// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.aroundteam2026.aroundapp.worker.ProofUploadWorker
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.FileNotFoundException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs [ProofUploadQueueWorkManager] on a test WorkManager, whose [ProofUploadWorker]s upload to
 * fakes, to check that a reservation is [ProofUploadQueue.waiting] until its upload ends.
 */
@RunWith(AndroidJUnit4::class)
class ProofUploadQueueWorkManagerTest {
  private lateinit var context: Context
  private lateinit var photos: FakeProofPhotoRepository
  private lateinit var completions: FakeCompletionRepository
  private lateinit var queue: ProofUploadQueueWorkManager

  /** Builds every worker on the fakes, as the app's factory would on the real repositories. */
  private val factory =
      object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker =
            ProofUploadWorker(appContext, workerParameters, photos, completions) { true }
      }

  private val workManager: WorkManager
    get() = WorkManager.getInstance(context)

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    photos = FakeProofPhotoRepository()
    completions = FakeCompletionRepository()
    WorkManagerTestInitHelper.initializeTestWorkManager(
        context,
        Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(factory)
            .build(),
    )
    queue = ProofUploadQueueWorkManager(workManager)
  }

  @Test
  fun startsWithNothingWaiting() = runWithTimeout { assertTrue(queue.waiting.first().isEmpty()) }

  @Test
  fun enqueue_listsTheReservationAsWaitingWhileOffline() = runWithTimeout {
    assertTrue(queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO).isSuccess)

    assertEquals(setOf(RESERVATION_ID), queue.waiting.first())
    assertTrue(photos.uploads.isEmpty())
  }

  @Test
  fun enqueue_twoReservations_listsBoth() = runWithTimeout {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)
    queue.enqueue(OTHER_RESERVATION_ID, VENUE_ID, EXPLORER_UID, RETAKE)

    assertEquals(setOf(RESERVATION_ID, OTHER_RESERVATION_ID), queue.waiting.first())
  }

  @Test
  fun succeededUpload_leavesWaitingAndSendsTheQueuedPhoto() = runWithTimeout {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)

    runOnline(RESERVATION_ID)

    assertTrue(RESERVATION_ID !in queue.waiting.first())
    assertEquals(listOf(Uri.fromFile(PHOTO)), photos.uploads.values.toList())
    assertEquals(1, completions.observeForReservation(RESERVATION_ID).first().size)
  }

  @Test
  fun stoppedUpload_leavesWaiting() = runWithTimeout {
    photos.forcedFailure = FileNotFoundException(GONE)
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)

    runOnline(RESERVATION_ID)

    assertTrue(RESERVATION_ID !in queue.waiting.first())
    assertTrue(completions.observeForReservation(RESERVATION_ID).first().isEmpty())
  }

  @Test
  fun cancelledUpload_leavesWaiting() = runWithTimeout {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)

    workManager.cancelUniqueWork(ProofUploadWorker.uniqueWorkName(RESERVATION_ID)).await()

    assertTrue(queue.waiting.first().isEmpty())
  }

  @Test
  fun enqueue_whileTheReservationIsWaiting_keepsTheFirstProof() = runWithTimeout {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)

    assertTrue(queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, RETAKE).isSuccess)
    runOnline(RESERVATION_ID)

    assertEquals(listOf(Uri.fromFile(PHOTO)), photos.uploads.values.toList())
  }

  @Test
  fun enqueue_afterTheUploadEnded_queuesTheNewProof() = runWithTimeout {
    photos.forcedFailure = FileNotFoundException(GONE)
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)
    runOnline(RESERVATION_ID)
    photos.forcedFailure = null

    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, RETAKE)

    assertEquals(setOf(RESERVATION_ID), queue.waiting.first())
  }

  @Test
  fun enqueue_whenWorkManagerFails_returnsTheFailure() = runWithTimeout {
    val failure = IllegalStateException(UNAVAILABLE)

    val result =
        ProofUploadQueueWorkManager(brokenWorkManager(failure))
            .enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)

    assertEquals(failure, result.exceptionOrNull())
  }

  @Test
  fun enqueue_whenCancelled_rethrowsTheCancellation() = runWithTimeout {
    val thrown =
        assertThrows(CancellationException::class.java) {
          runBlocking {
            ProofUploadQueueWorkManager(brokenWorkManager(CancellationException(CANCELLED)))
                .enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)
          }
        }

    assertEquals(CANCELLED, thrown.message)
  }

  /**
   * Brings the network back for [reservationId]'s upload and waits until its work has finished, so
   * the test itself checks what [ProofUploadQueue.waiting] says then.
   */
  private suspend fun runOnline(reservationId: String) {
    val name = ProofUploadWorker.uniqueWorkName(reservationId)
    val driver = checkNotNull(WorkManagerTestInitHelper.getTestDriver(context))
    workManager.getWorkInfosForUniqueWorkFlow(name).first().forEach {
      driver.setAllConstraintsMet(it.id)
    }
    workManager.getWorkInfosForUniqueWorkFlow(name).first { works ->
      works.all { it.state.isFinished }
    }
  }

  /** Runs [body] as a test that fails, instead of hanging, if a flow stops emitting. */
  private fun runWithTimeout(body: suspend CoroutineScope.() -> Unit) = runBlocking {
    withTimeout(TIMEOUT_MS, body)
  }

  /** A WorkManager whose every [WorkManager.enqueueUniqueWork] throws [error]. */
  private fun brokenWorkManager(error: Throwable): WorkManager =
      mockk<WorkManager>(relaxed = true) {
        every { enqueueUniqueWork(any<String>(), any(), any<OneTimeWorkRequest>()) } throws error
      }

  private companion object {
    const val RESERVATION_ID = "reservation-1"
    const val OTHER_RESERVATION_ID = "reservation-2"
    const val VENUE_ID = "cafe"
    const val EXPLORER_UID = "yusuf"
    const val GONE = "gone"
    const val UNAVAILABLE = "WorkManager unavailable"
    const val CANCELLED = "screen closed"
    const val TIMEOUT_MS = 5_000L
    val PHOTO = File("/data/user/0/app/files/proofs/proof-1.jpg")
    val RETAKE = File("/data/user/0/app/files/proofs/proof-2.jpg")
  }
}
