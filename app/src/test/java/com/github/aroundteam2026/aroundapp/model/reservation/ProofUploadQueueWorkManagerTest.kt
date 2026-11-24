// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.github.aroundteam2026.aroundapp.worker.ProofUploadWorker
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Runs [ProofUploadQueueWorkManager] on a test WorkManager, whose [ProofUploadWorker]s upload to
 * fakes, to check that a reservation is [ProofUploadQueue.waiting] until its upload ends, and
 * [ProofUploadQueue.failed] if it failed for good.
 */
@RunWith(AndroidJUnit4::class)
class ProofUploadQueueWorkManagerTest {
  private lateinit var context: Context
  private lateinit var photos: FakeProofPhotoRepository
  private lateinit var completions: FakeCompletionRepository
  private lateinit var queue: ProofUploadQueueWorkManager
  private val discarded = mutableListOf<Uri>()

  /** Holds the photos [ProofUploadQueue.retry] needs to still be kept. */
  @get:Rule val folder = TemporaryFolder()

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
    queue =
        ProofUploadQueueWorkManager(workManager) {
          discarded += it
          true
        }
  }

  @Test
  fun startsWithNothingWaitingOrFailed() = runWithTimeout {
    assertTrue(queue.waiting.first().isEmpty())
    assertTrue(queue.failed.first().isEmpty())
  }

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
  fun succeededUpload_leavesWaitingWithoutFailingAndSendsTheQueuedPhoto() = runWithTimeout {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)

    runOnline(RESERVATION_ID)

    assertTrue(RESERVATION_ID !in queue.waiting.first())
    assertTrue(queue.failed.first().isEmpty())
    assertEquals(listOf(Uri.fromFile(PHOTO)), photos.uploads.values.toList())
    assertEquals(1, completions.observeForReservation(RESERVATION_ID).first().size)
  }

  @Test
  fun stoppedUpload_leavesWaitingForFailed() = runWithTimeout {
    failUpload(PHOTO)

    assertTrue(RESERVATION_ID !in queue.waiting.first())
    assertEquals(mapOf(RESERVATION_ID to UploadFailure.PHOTO_MISSING), queue.failed.first())
    assertTrue(completions.observeForReservation(RESERVATION_ID).first().isEmpty())
  }

  @Test
  fun stoppedUpload_ofAnApprovedCompletion_isNotListedAsFailed() = runWithTimeout {
    completions.submit(RESERVATION_ID, VENUE_ID, EXPLORER_UID, Proof.Photo(LOCATION))
    completions.approve(completions.observeForReservation(RESERVATION_ID).first().single().id)
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)

    runOnline(RESERVATION_ID)

    assertTrue(queue.waiting.first().isEmpty())
    assertTrue(queue.failed.first().isEmpty())
  }

  @Test
  fun cancelledUpload_leavesWaitingWithoutFailing() = runWithTimeout {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)

    workManager.cancelUniqueWork(ProofUploadWorker.uniqueWorkName(RESERVATION_ID)).await()

    assertTrue(queue.waiting.first().isEmpty())
    assertTrue(queue.failed.first().isEmpty())
  }

  @Test
  fun retry_queuesTheFailedProofAgainWithTheSamePhoto() = runWithTimeout {
    val photo = folder.newFile()
    failUpload(photo)
    photos.forcedFailure = null

    assertTrue(queue.retry(RESERVATION_ID).isSuccess)

    assertEquals(setOf(RESERVATION_ID), queue.waiting.first())
    assertTrue(queue.failed.first().isEmpty())
    runOnline(RESERVATION_ID)
    assertEquals(listOf(Uri.fromFile(photo)), photos.uploads.values.toList())
    val completion = completions.observeForReservation(RESERVATION_ID).first().single()
    assertEquals(VENUE_ID, completion.venueId)
    assertEquals(EXPLORER_UID, completion.explorerUid)
  }

  @Test
  fun retry_withoutAKeptFailedProof_failsAndChangesNothing() = runWithTimeout {
    queue.enqueue(OTHER_RESERVATION_ID, VENUE_ID, EXPLORER_UID, folder.newFile())
    val photo = folder.newFile()
    failUpload(photo)
    photo.delete()

    assertTrue(queue.retry(RESERVATION_ID).exceptionOrNull() is FileNotFoundException)
    assertTrue(queue.retry(OTHER_RESERVATION_ID).exceptionOrNull() is NoSuchElementException)
    assertEquals(setOf(RESERVATION_ID), queue.failed.first().keys)
    assertEquals(setOf(OTHER_RESERVATION_ID), queue.waiting.first())
  }

  @Test
  fun retry_whenWorkManagerFails_returnsTheFailure() = runWithTimeout {
    val failure = IllegalStateException(UNAVAILABLE)

    val result =
        ProofUploadQueueWorkManager(brokenWorkManager(failure, works = flow { throw failure })) {
              true
            }
            .retry(RESERVATION_ID)

    assertEquals(failure, result.exceptionOrNull())
  }

  @Test
  fun retry_whenQueuingFails_returnsTheFailure() = runWithTimeout {
    val failure = IllegalStateException(UNAVAILABLE)
    val works = flowOf(listOf(failedWork(ProofUploadWorker.ERROR_UPLOAD, folder.newFile())))

    val result =
        ProofUploadQueueWorkManager(brokenWorkManager(failure, works)) { true }
            .retry(RESERVATION_ID)

    assertEquals(failure, result.exceptionOrNull())
  }

  @Test
  fun retry_ofAnApprovedCompletion_findsNoFailedProof() = runWithTimeout {
    val works =
        flowOf(listOf(failedWork(ProofUploadWorker.ERROR_ALREADY_APPROVED, folder.newFile())))

    val result =
        ProofUploadQueueWorkManager(brokenWorkManager(IllegalStateException(UNAVAILABLE), works)) {
              true
            }
            .retry(RESERVATION_ID)

    assertTrue(result.exceptionOrNull() is NoSuchElementException)
  }

  @Test
  fun retry_ofAWorkQueuedBeforeItsVenueWasTagged_failsAsNotRebuildable() = runWithTimeout {
    val untagged =
        WorkInfo(
            UUID.randomUUID(),
            WorkInfo.State.FAILED,
            setOf(ProofUploadWorker.TAG, ProofUploadWorker.uniqueWorkName(RESERVATION_ID)),
            workDataOf(ProofUploadWorker.KEY_ERROR to ProofUploadWorker.ERROR_UPLOAD),
        )

    val result =
        ProofUploadQueueWorkManager(
                brokenWorkManager(IllegalStateException(UNAVAILABLE), flowOf(listOf(untagged)))
            ) {
              true
            }
            .retry(RESERVATION_ID)

    assertTrue(result.exceptionOrNull() is IllegalStateException)
  }

  @Test
  fun retry_afterARetakeWasQueued_failsAndKeepsTheRetake() = runWithTimeout {
    failUpload(PHOTO)
    photos.forcedFailure = null
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, RETAKE)

    val result = queue.retry(RESERVATION_ID)

    assertTrue(result.exceptionOrNull() is NoSuchElementException)
    assertEquals(setOf(RESERVATION_ID), queue.waiting.first())
    assertTrue(Uri.fromFile(RETAKE) !in discarded)
    runOnline(RESERVATION_ID)
    assertEquals(listOf(Uri.fromFile(RETAKE)), photos.uploads.values.toList())
  }

  @Test
  fun failed_tellsWhyEachProofFailed() = runWithTimeout {
    val reasons =
        mapOf(
            ProofUploadWorker.ERROR_UPLOAD to UploadFailure.RETRYABLE,
            ProofUploadWorker.ERROR_SUBMIT to UploadFailure.RETRYABLE,
            ProofUploadWorker.ERROR_REFUSED to UploadFailure.REFUSED,
            ProofUploadWorker.ERROR_INVALID to UploadFailure.REFUSED,
            ProofUploadWorker.ERROR_BAD_INPUT to UploadFailure.REFUSED,
            ProofUploadWorker.ERROR_PHOTO_MISSING to UploadFailure.PHOTO_MISSING,
        )
    // Each work is named after its error code, so the map says which reason each one got
    val works = reasons.keys.map { failedWork(it, PHOTO, reservationId = it) }
    val manager =
        mockk<WorkManager>(relaxed = true) {
          every { getWorkInfosByTagFlow(ProofUploadWorker.TAG) } returns flowOf(works)
        }

    assertEquals(reasons, ProofUploadQueueWorkManager(manager) { true }.failed.first())
  }

  @Test
  fun enqueue_whileTheReservationIsWaiting_sendsTheNewProof() = runWithTimeout {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)

    assertTrue(queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, RETAKE).isSuccess)
    runOnline(RESERVATION_ID)

    assertEquals(listOf(Uri.fromFile(RETAKE)), photos.uploads.values.toList())
    assertEquals(listOf(Uri.fromFile(PHOTO)), discarded)
  }

  @Test
  fun enqueue_afterAFailure_leavesFailedAndDeletesTheFailedCopy() = runWithTimeout {
    failUpload(PHOTO)
    photos.forcedFailure = null

    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, RETAKE)

    assertEquals(setOf(RESERVATION_ID), queue.waiting.first())
    assertTrue(queue.failed.first().isEmpty())
    assertEquals(listOf(Uri.fromFile(PHOTO)), discarded)
  }

  @Test
  fun enqueue_whenWorkManagerFails_returnsTheFailure() = runWithTimeout {
    val failure = IllegalStateException(UNAVAILABLE)

    val result =
        ProofUploadQueueWorkManager(brokenWorkManager(failure)) { true }
            .enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)

    assertEquals(failure, result.exceptionOrNull())
  }

  @Test
  fun enqueue_whenCancelled_rethrowsTheCancellation() = runWithTimeout {
    val thrown =
        assertThrows(CancellationException::class.java) {
          runBlocking {
            ProofUploadQueueWorkManager(brokenWorkManager(CancellationException(CANCELLED))) {
                  true
                }
                .enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)
          }
        }

    assertEquals(CANCELLED, thrown.message)
  }

  @Test
  fun cancel_stopsTheWaitingUploadAndDeletesItsCopy() = runWithTimeout {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)
    queue.enqueue(OTHER_RESERVATION_ID, VENUE_ID, EXPLORER_UID, RETAKE)

    assertTrue(queue.cancel(RESERVATION_ID).isSuccess)

    val works =
        workManager.getWorkInfosForUniqueWorkFlow(ProofUploadWorker.uniqueWorkName(RESERVATION_ID))
    assertEquals(listOf(WorkInfo.State.CANCELLED), works.first().map { it.state })
    assertEquals(setOf(OTHER_RESERVATION_ID), queue.waiting.first())
    assertEquals(listOf(Uri.fromFile(PHOTO)), discarded)
  }

  @Test
  fun cancel_afterAFailure_leavesFailedAndDeletesTheCopy() = runWithTimeout {
    val photo = folder.newFile()
    failUpload(photo)

    assertTrue(queue.cancel(RESERVATION_ID).isSuccess)

    assertTrue(queue.failed.first().isEmpty())
    assertEquals(listOf(Uri.fromFile(photo)), discarded)
    assertTrue(queue.retry(RESERVATION_ID).exceptionOrNull() is NoSuchElementException)
  }

  @Test
  fun cancel_afterTheUploadEnded_deletesNothing() = runWithTimeout {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)
    runOnline(RESERVATION_ID)

    assertTrue(queue.cancel(RESERVATION_ID).isSuccess)

    assertTrue(discarded.isEmpty())
  }

  @Test
  fun cancel_whenWorkManagerFails_returnsTheFailureAndDeletesNothing() = runWithTimeout {
    val failure = IllegalStateException(UNAVAILABLE)

    val result =
        ProofUploadQueueWorkManager(brokenWorkManager(failure)) {
              discarded += it
              true
            }
            .cancel(RESERVATION_ID)

    assertEquals(failure, result.exceptionOrNull())
    assertTrue(discarded.isEmpty())
  }

  @Test
  fun cancel_whenCancelled_rethrowsTheCancellation() = runWithTimeout {
    val thrown =
        assertThrows(CancellationException::class.java) {
          runBlocking {
            ProofUploadQueueWorkManager(brokenWorkManager(CancellationException(CANCELLED))) {
                  true
                }
                .cancel(RESERVATION_ID)
          }
        }

    assertEquals(CANCELLED, thrown.message)
  }

  /** Queues [photo] as [RESERVATION_ID]'s proof, and runs its upload until it stops for good. */
  private suspend fun failUpload(photo: File) {
    photos.forcedFailure = FileNotFoundException(GONE)
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, photo)
    runOnline(RESERVATION_ID)
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

  /** A work of [reservationId]'s proof [photo] that failed for good with the error [code]. */
  private fun failedWork(code: String, photo: File, reservationId: String = RESERVATION_ID) =
      WorkInfo(
          UUID.randomUUID(),
          WorkInfo.State.FAILED,
          ProofUploadWorker.buildRequest(reservationId, VENUE_ID, EXPLORER_UID, Uri.fromFile(photo))
              .tags,
          workDataOf(ProofUploadWorker.KEY_ERROR to code),
      )

  /** Runs [body] as a test that fails, instead of hanging, if a flow stops emitting. */
  private fun runWithTimeout(body: suspend CoroutineScope.() -> Unit) = runBlocking {
    withTimeout(TIMEOUT_MS, body)
  }

  /**
   * A WorkManager whose every [WorkManager.enqueueUniqueWork] and [cancelUniqueWork] throws
   * [error], and whose [WorkManager.getWorkInfosForUniqueWorkFlow] is [works].
   */
  private fun brokenWorkManager(
      error: Throwable,
      works: Flow<List<WorkInfo>> = flowOf(emptyList()),
  ): WorkManager =
      mockk<WorkManager>(relaxed = true) {
        every { getWorkInfosForUniqueWorkFlow(any()) } returns works
        every { enqueueUniqueWork(any<String>(), any(), any<OneTimeWorkRequest>()) } throws error
        every { cancelUniqueWork(any()) } throws error
      }

  private companion object {
    const val RESERVATION_ID = "reservation-1"
    const val OTHER_RESERVATION_ID = "reservation-2"
    const val VENUE_ID = "cafe"
    const val EXPLORER_UID = "yusuf"
    const val GONE = "gone"
    const val UNAVAILABLE = "WorkManager unavailable"
    const val CANCELLED = "screen closed"
    const val LOCATION = "gs://proofs/reservation-1.jpg"
    const val TIMEOUT_MS = 5_000L
    val PHOTO = File("/data/user/0/app/files/proofs/proof-1.jpg")
    val RETAKE = File("/data/user/0/app/files/proofs/proof-2.jpg")
  }
}
