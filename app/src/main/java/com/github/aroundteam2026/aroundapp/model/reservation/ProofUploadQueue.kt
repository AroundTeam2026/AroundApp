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
   * collection, then each change. A reservation leaves once its proof is submitted, or once its
   * upload stops for good, which lists it in [failed] unless its proof is no longer needed.
   */
  val waiting: Flow<Set<String>>

  /**
   * The ids of the reservations whose photo proof failed for good and wasn't sent, e.g. refused by
   * the security rules, its photo gone, or out of attempts: the current ones on collection, then
   * each change. A reservation whose completion the venue already approved isn't listed, since its
   * proof is no longer needed. A reservation leaves once its proof is queued again, or once the
   * queue forgets the failure, which may happen after about a day.
   */
  val failed: Flow<Set<String>>

  /**
   * Queues [reservationId]'s failed proof again, with the same photo, if it is still kept: it
   * leaves [failed] and is listed in [waiting].
   *
   * @param reservationId id of a reservation listed in [failed].
   * @return success once the proof is queued again; a [NoSuchElementException] if [reservationId]
   *   has no failed proof, a [java.io.FileNotFoundException] if its photo is no longer kept, or the
   *   failure that kept it from being queued.
   */
  suspend fun retry(reservationId: String): Result<Unit>
}
