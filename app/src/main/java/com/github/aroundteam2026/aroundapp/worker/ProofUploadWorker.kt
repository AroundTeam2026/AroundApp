package com.github.aroundteam2026.aroundapp.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Uploads one proof photo that is kept in app storage. Runs only with a network connection and
 * retries with exponential backoff, so a photo taken offline is sent once the phone is back online.
 * The upload queue (#538) enqueues it with [buildRequest].
 */
class ProofUploadWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val localPath = inputData.getString(KEY_LOCAL_PATH)
    val remotePath = inputData.getString(KEY_REMOTE_PATH)
    if (localPath.isNullOrBlank() || remotePath.isNullOrBlank()) {
      return Result.failure(workDataOf(KEY_ERROR to ERROR_BAD_INPUT))
    }

    val file = File(localPath)
    if (!file.exists()) {
      // Retrying cannot bring the file back.
      return Result.failure(workDataOf(KEY_ERROR to ERROR_FILE_MISSING))
    }

    return try {
      val stored = uploaderProvider().upload(file, remotePath)
      Result.success(workDataOf(KEY_REMOTE_PATH to stored))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
        Result.failure(workDataOf(KEY_ERROR to (e.message ?: ERROR_UPLOAD)))
      } else {
        Result.retry()
      }
    }
  }

  companion object {
    const val KEY_LOCAL_PATH = "local_path"
    const val KEY_REMOTE_PATH = "remote_path"
    const val KEY_ERROR = "error"

    const val ERROR_BAD_INPUT = "bad_input"
    const val ERROR_FILE_MISSING = "file_missing"
    const val ERROR_UPLOAD = "upload_failed"

    const val TAG = "proof-upload"
    const val MAX_ATTEMPTS = 5

    /** Replaced in tests with a fake. */
    @Volatile var uploaderProvider: () -> ProofPhotoUploader = { FirebaseProofPhotoUploader() }

    /** One unique work per remote path, so the same photo is never uploaded twice. */
    fun uniqueWorkName(remotePath: String): String = "$TAG:$remotePath"

    fun buildRequest(localPath: String, remotePath: String): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<ProofUploadWorker>()
            .setInputData(workDataOf(KEY_LOCAL_PATH to localPath, KEY_REMOTE_PATH to remotePath))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            )
            .addTag(TAG)
            .build()
  }
}
