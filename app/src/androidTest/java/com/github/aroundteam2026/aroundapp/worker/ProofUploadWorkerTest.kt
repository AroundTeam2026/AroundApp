// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.github.aroundteam2026.aroundapp.model.reservation.Completion
import com.github.aroundteam2026.aroundapp.model.reservation.FakeCompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeProofPhotoRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Proof
import com.github.aroundteam2026.aroundapp.worker.ProofUploadWorker.Companion.ERROR_ALREADY_APPROVED
import com.github.aroundteam2026.aroundapp.worker.ProofUploadWorker.Companion.ERROR_BAD_INPUT
import com.github.aroundteam2026.aroundapp.worker.ProofUploadWorker.Companion.ERROR_INVALID
import com.github.aroundteam2026.aroundapp.worker.ProofUploadWorker.Companion.ERROR_PHOTO_MISSING
import com.github.aroundteam2026.aroundapp.worker.ProofUploadWorker.Companion.ERROR_REFUSED
import com.github.aroundteam2026.aroundapp.worker.ProofUploadWorker.Companion.ERROR_SUBMIT
import com.github.aroundteam2026.aroundapp.worker.ProofUploadWorker.Companion.ERROR_UPLOAD
import com.github.aroundteam2026.aroundapp.worker.ProofUploadWorker.Companion.KEY_ERROR
import com.github.aroundteam2026.aroundapp.worker.ProofUploadWorker.Companion.KEY_EXPLORER_UID
import com.github.aroundteam2026.aroundapp.worker.ProofUploadWorker.Companion.KEY_LOCATION
import com.github.aroundteam2026.aroundapp.worker.ProofUploadWorker.Companion.KEY_PHOTO
import com.github.aroundteam2026.aroundapp.worker.ProofUploadWorker.Companion.KEY_RESERVATION_ID
import com.github.aroundteam2026.aroundapp.worker.ProofUploadWorker.Companion.KEY_VENUE_ID
import com.github.aroundteam2026.aroundapp.worker.ProofUploadWorker.Companion.MAX_ATTEMPTS
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs [ProofUploadWorker] on fakes: through a test WorkManager, where the network constraint and
 * unique work matter, and directly, with [TestListenableWorkerBuilder], for each way a run ends.
 */
@RunWith(AndroidJUnit4::class)
class ProofUploadWorkerTest {
  private lateinit var context: Context
  private lateinit var photos: FakeProofPhotoRepository
  private lateinit var completions: FakeCompletionRepository

  /** The copies the worker deleted; written on the worker's thread. */
  private val discarded = CopyOnWriteArrayList<Uri>()

  /** Builds every worker on the fakes, as the app's factory would on the real repositories. */
  private val factory =
      object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker =
            ProofUploadWorker(appContext, workerParameters, photos, completions) {
              discarded += it
              true
            }
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
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(factory)
            .build(),
    )
  }

  // ---- through WorkManager ----

  @Test
  fun online_uploadsSubmitsTheProofAndDeletesTheCopy() = runBlocking {
    val request = request()
    workManager.enqueue(request).await()
    assertEquals(WorkInfo.State.ENQUEUED, info(request).first().state)
    assertTrue("Uploaded while offline", photos.uploads.isEmpty())

    val info = runOnline(request)

    assertEquals(WorkInfo.State.SUCCEEDED, info.state)
    val location = checkNotNull(info.outputData.getString(KEY_LOCATION))
    assertEquals(mapOf(location to PHOTO), photos.uploads)
    val completion = submitted().single()
    assertEquals(Proof.Photo(location), completion.proof)
    assertEquals(VENUE_ID, completion.venueId)
    assertEquals(EXPLORER_UID, completion.explorerUid)
    assertEquals(listOf(PHOTO), discarded)
  }

  @Test
  fun refusedUpload_stopsWithoutRetryingAndKeepsTheCopy() = runBlocking {
    photos.forcedFailure = checkNotNull(StorageException.fromExceptionAndHttpCode(null, FORBIDDEN))
    val request = request()
    workManager.enqueue(request).await()

    val info = runOnline(request)

    assertEquals(WorkInfo.State.FAILED, info.state)
    assertEquals(ERROR_REFUSED, info.outputData.getString(KEY_ERROR))
    assertTrue(submitted().isEmpty())
    assertTrue(discarded.isEmpty())
  }

  @Test
  fun sameReservationEnqueuedTwiceWithKeep_isUploadedOnce() = runBlocking {
    val name = ProofUploadWorker.uniqueWorkName(RESERVATION_ID)
    val first = request()
    workManager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, first).await()
    workManager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request()).await()

    runOnline(first)

    assertEquals(1, workManager.getWorkInfosForUniqueWorkFlow(name).first().size)
    assertEquals(1, photos.uploads.size)
  }

  // ---- one run ----

  @Test
  fun failedUpload_isRetriedAndKeepsTheCopy() = runBlocking {
    photos.forcedFailure = IOException("offline")

    assertEquals(ListenableWorker.Result.retry(), worker().doWork())
    assertTrue(submitted().isEmpty())
    assertTrue(discarded.isEmpty())
  }

  @Test
  fun failedUpload_onTheLastAttempt_failsWithTheUploadError() = runBlocking {
    photos.forcedFailure = IOException("offline")

    assertEquals(failure(ERROR_UPLOAD), worker(attempt = MAX_ATTEMPTS - 1).doWork())
  }

  @Test
  fun uploadFailingWithIllegalState_isRetried() = runBlocking {
    photos.forcedFailure = IllegalStateException("not ready")

    assertEquals(ListenableWorker.Result.retry(), worker().doWork())
  }

  @Test
  fun failedSubmission_isRetriedAndKeepsTheCopy() = runBlocking {
    completions.forcedFailure = IOException("offline")

    assertEquals(ListenableWorker.Result.retry(), worker().doWork())
    assertTrue(discarded.isEmpty())
  }

  @Test
  fun failedSubmission_onTheLastAttempt_failsWithTheSubmitError() = runBlocking {
    completions.forcedFailure = IOException("offline")

    assertEquals(failure(ERROR_SUBMIT), worker(attempt = MAX_ATTEMPTS - 1).doWork())
  }

  @Test
  fun refusedSubmission_stops() = runBlocking {
    completions.forcedFailure =
        FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)

    assertEquals(failure(ERROR_REFUSED), worker().doWork())
    assertTrue(discarded.isEmpty())
  }

  @Test
  fun approvedCompletion_stops() = runBlocking {
    val id =
        completions.submit(RESERVATION_ID, VENUE_ID, EXPLORER_UID, Proof.Text("x")).getOrThrow()
    completions.approve(id).getOrThrow()

    assertEquals(failure(ERROR_ALREADY_APPROVED), worker().doWork())
    assertTrue(discarded.isEmpty())
  }

  @Test
  fun copyThatIsGone_stops() = runBlocking {
    photos.forcedFailure = FileNotFoundException("gone")

    assertEquals(failure(ERROR_PHOTO_MISSING), worker().doWork())
  }

  @Test
  fun invalidReservationId_stopsWithoutUploading() = runBlocking {
    val result = worker(input = input(reservationId = "nested/$RESERVATION_ID")).doWork()

    assertEquals(failure(ERROR_INVALID), result)
    assertTrue(photos.uploads.isEmpty())
  }

  @Test
  fun missingInput_stopsWithoutUploading() = runBlocking {
    val result = worker(input = workDataOf(KEY_RESERVATION_ID to RESERVATION_ID)).doWork()

    assertEquals(failure(ERROR_BAD_INPUT), result)
    assertTrue(photos.uploads.isEmpty())
  }

  // ---- helpers ----

  private fun request(): OneTimeWorkRequest =
      ProofUploadWorker.buildRequest(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)

  private fun info(request: OneTimeWorkRequest): Flow<WorkInfo> =
      workManager.getWorkInfoByIdFlow(request.id).filterNotNull()

  /** Brings the network back for [request] and waits until it has finished. */
  private suspend fun runOnline(request: OneTimeWorkRequest): WorkInfo {
    checkNotNull(WorkManagerTestInitHelper.getTestDriver(context)).setAllConstraintsMet(request.id)
    return withTimeout(TIMEOUT_MS) { info(request).first { it.state.isFinished } }
  }

  private fun input(reservationId: String = RESERVATION_ID): Data =
      workDataOf(
          KEY_RESERVATION_ID to reservationId,
          KEY_VENUE_ID to VENUE_ID,
          KEY_EXPLORER_UID to EXPLORER_UID,
          KEY_PHOTO to PHOTO.toString(),
      )

  private fun worker(attempt: Int = 0, input: Data = input()): ProofUploadWorker =
      TestListenableWorkerBuilder<ProofUploadWorker>(context)
          .setInputData(input)
          .setRunAttemptCount(attempt)
          .setWorkerFactory(factory)
          .build()

  private fun failure(code: String) = ListenableWorker.Result.failure(workDataOf(KEY_ERROR to code))

  private suspend fun submitted(): List<Completion> =
      completions.observeForReservation(RESERVATION_ID).first()

  private companion object {
    const val RESERVATION_ID = "reservation-1"
    const val VENUE_ID = "cafe"
    const val EXPLORER_UID = "yusuf"
    const val FORBIDDEN = 403
    const val TIMEOUT_MS = 5_000L
    val PHOTO: Uri = Uri.parse("file:///data/user/0/app/files/proofs/proof-1.jpg")
  }
}
