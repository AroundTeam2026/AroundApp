// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * Queues photo proofs taken offline, to be uploaded and submitted once the device is online, even
 * if the app was closed meanwhile (#406). ViewModels depend on this interface only, so they never
 * import WorkManager.
 */
interface ProofUploadQueue {
  /**
   * Queues the photo proof in [photo] for [reservationId], to be uploaded and submitted once
   * online. A reservation has one proof waiting at a time: while [reservationId]'s is waiting, this
   * replaces it with [photo], cancelling its upload, so the latest photo is the one sent.
   *
   * @param reservationId id of the approved reservation the photo proves.
   * @param venueId id of the venue that owns the reservation's quest.
   * @param explorerUid uid of the party member sending the proof.
   * @param photo the copy kept in app storage, which stays readable after the app is closed.
   * @return success once the proof is queued, or the failure that kept it from being queued.
   */
  suspend fun enqueue(
      reservationId: String,
      venueId: String,
      explorerUid: String,
      photo: File,
  ): Result<Unit>

  /**
   * The ids of the reservations whose photo proof is waiting to upload: the current ones on
   * collection, then each change. A reservation leaves once its proof is submitted, once its upload
   * stops for good, which lists it in [failed] unless its proof is no longer needed, or once it is
   * [cancel]led.
   */
  val waiting: Flow<Set<String>>

  /**
   * The reservations whose photo proof failed for good, each with why: the current ones on
   * collection, then each change. One whose completion is already approved isn't listed. A
   * reservation leaves once its proof is queued again or [cancel]led, or after about a day.
   */
  val failed: Flow<Map<String, UploadFailure>>

  /**
   * Queues [reservationId]'s failed proof again, with the same photo, if it is still kept: it
   * leaves [failed] and is listed in [waiting]. Only a [UploadFailure.RETRYABLE] failure can pass
   * on a retry; a [UploadFailure.PHOTO_MISSING] one can't be retried.
   *
   * @param reservationId id of a reservation listed in [failed].
   * @return success once the proof is queued again; a [NoSuchElementException] if [reservationId]
   *   has no failed proof, an [IllegalStateException] if its failed proof can't be rebuilt, a
   *   [java.io.FileNotFoundException] if its photo is no longer kept, or the failure that kept it
   *   from being queued.
   */
  suspend fun retry(reservationId: String): Result<Unit>

  /**
   * Cancels [reservationId]'s waiting or failed proof, if any, and deletes its copy, for a
   * reservation cancelled or whose party the explorer left, since the rules would refuse its upload
   * (#625). It then leaves [waiting] and [failed].
   *
   * @param reservationId id of the reservation whose proof to cancel.
   * @return success once no proof of [reservationId] is waiting or failed, including when none was,
   *   or the failure that kept it from being cancelled.
   */
  suspend fun cancel(reservationId: String): Result<Unit>

  /**
   * Cancels every waiting or failed proof and deletes their copies, e.g. at sign-out: each keeps
   * the uid of the explorer who queued it, so after a sign-out the rules would refuse its upload
   * (#626). [waiting] and [failed] are then empty.
   *
   * @return success once no proof is waiting or failed, including when none was, or the failure
   *   that kept them from being cancelled.
   */
  suspend fun cancelAll(): Result<Unit>
}
