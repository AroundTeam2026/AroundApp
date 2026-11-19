// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.worker

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.Proof
import com.github.aroundteam2026.aroundapp.model.reservation.ProofPhotoRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ProofPhotoRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.isRulesRefusal
import com.github.aroundteam2026.aroundapp.ui.completion.ProofPhotos
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit

/**
 * Sends a photo proof once the device is online, even if the app was closed meanwhile (#406): it
 * uploads the copy [ProofPhotos] keeps in app storage (#412) through [ProofPhotoRepository],
 * submits the proof with the photo's `gs://` location through [CompletionRepository], then deletes
 * the copy. The upload queue (#538) enqueues it with [buildRequest].
 *
 * A failure that can pass, such as a lost connection or a timeout, is retried with backoff, for at
 * most [MAX_ATTEMPTS] runs. One that would fail the same way on every run stops at once: a refusal
 * by the security rules, a copy that is gone, input the repositories reject, or a completion the
 * venue already approved. A failed run's output holds one of the `ERROR_*` codes under [KEY_ERROR].
 *
 * The copy is only deleted once its proof is submitted, so a failed run leaves it for the next one,
 * and [ProofPhotos.discardStale] deletes it if no run succeeds. A photo can't be replaced, so a run
 * whose upload worked but whose submission didn't leaves that upload unused, as
 * [ProofPhotoRepository.upload] describes; the next run uploads the photo again.
 *
 * @param photos Uploads the photo.
 * @param completions Submits the proof.
 * @param discard Deletes the kept copy once its proof is submitted.
 */
class ProofUploadWorker(
    context: Context,
    params: WorkerParameters,
    private val photos: ProofPhotoRepository,
    private val completions: CompletionRepository,
    private val discard: suspend (Uri) -> Boolean,
) : CoroutineWorker(context, params) {

  /** The constructor WorkManager's default factory calls: the app's own repositories. */
  constructor(
      context: Context,
      params: WorkerParameters,
  ) : this(
      context,
      params,
      ProofPhotoRepositoryProvider.repository(context),
      CompletionRepositoryProvider.repository,
      ProofPhotos(context)::discard,
  )

  override suspend fun doWork(): Result {
    val reservationId = inputData.getString(KEY_RESERVATION_ID)
    val venueId = inputData.getString(KEY_VENUE_ID)
    val explorerUid = inputData.getString(KEY_EXPLORER_UID)
    val photo = inputData.getString(KEY_PHOTO)?.takeIf { it.isNotBlank() }?.toUri()
    if (reservationId == null || venueId == null || explorerUid == null || photo == null) {
      return failure(ERROR_BAD_INPUT)
    }

    val location =
        photos.upload(reservationId, photo).getOrElse {
          return retryOrStop(it, ERROR_UPLOAD)
        }
    completions.submit(reservationId, venueId, explorerUid, Proof.Photo(location)).onFailure {
      // CompletionRepository.submit's way of saying the venue already approved the completion
      if (it is IllegalStateException) return failure(ERROR_ALREADY_APPROVED)
      return retryOrStop(it, ERROR_SUBMIT)
    }
    discard(photo)
    return Result.success(workDataOf(KEY_LOCATION to location))
  }

  /**
   * A failure with the reason if [error] would fail the same way on every run; otherwise a retry,
   * until the last attempt, which fails with [lastAttemptCode].
   */
  private fun retryOrStop(error: Throwable, lastAttemptCode: String): Result =
      when {
        isRulesRefusal(error) -> failure(ERROR_REFUSED)
        error is FileNotFoundException -> failure(ERROR_PHOTO_MISSING)
        error is IllegalArgumentException -> failure(ERROR_INVALID)
        runAttemptCount + 1 >= MAX_ATTEMPTS -> failure(lastAttemptCode)
        else -> Result.retry()
      }

  private fun failure(code: String) = Result.failure(workDataOf(KEY_ERROR to code))

  companion object {
    /** Input: id of the approved reservation the photo proves. */
    const val KEY_RESERVATION_ID = "reservation_id"

    /** Input: id of the venue that owns the reservation's quest. */
    const val KEY_VENUE_ID = "venue_id"

    /** Input: uid of the party member sending the proof. */
    const val KEY_EXPLORER_UID = "explorer_uid"

    /** Input: the kept copy, as the `file://` [Uri] [ProofPhotos.shrink] returns, as a string. */
    const val KEY_PHOTO = "photo"

    /** Output of a success: the photo's `gs://` location, as [Proof.Photo] stores it. */
    const val KEY_LOCATION = "location"

    /** Output of a failure: one of the `ERROR_*` codes below. */
    const val KEY_ERROR = "error"

    /** An input is missing. */
    const val ERROR_BAD_INPUT = "bad_input"

    /** A repository rejected the input, e.g. an invalid reservation id or a photo too large. */
    const val ERROR_INVALID = "invalid"

    /** The kept copy can no longer be read. */
    const val ERROR_PHOTO_MISSING = "photo_missing"

    /** The security rules refused the upload or the submission. */
    const val ERROR_REFUSED = "refused"

    /** The venue already approved the party's completion, which is final. */
    const val ERROR_ALREADY_APPROVED = "already_approved"

    /** The upload still failed on the last attempt. */
    const val ERROR_UPLOAD = "upload_failed"

    /** The submission still failed on the last attempt. */
    const val ERROR_SUBMIT = "submit_failed"

    /** Tag of every proof upload, to observe them all. */
    const val TAG = "proof-upload"

    /** How many runs a failure that can pass gets before the work fails. */
    const val MAX_ATTEMPTS = 5

    /**
     * Name to enqueue the upload of [reservationId]'s proof under as unique work: a party has one
     * completion per reservation, so one upload per reservation is pending at a time.
     */
    fun uniqueWorkName(reservationId: String): String = "$TAG:$reservationId"

    /**
     * The work that sends [photo], a copy [ProofPhotos] keeps, as the proof of the reservation
     * [reservationId]. It waits for a network connection and retries with exponential backoff.
     */
    fun buildRequest(
        reservationId: String,
        venueId: String,
        explorerUid: String,
        photo: Uri,
    ): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<ProofUploadWorker>()
            .setInputData(
                workDataOf(
                    KEY_RESERVATION_ID to reservationId,
                    KEY_VENUE_ID to venueId,
                    KEY_EXPLORER_UID to explorerUid,
                    KEY_PHOTO to photo.toString(),
                )
            )
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
