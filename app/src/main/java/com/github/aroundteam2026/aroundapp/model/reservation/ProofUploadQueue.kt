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
   * upload stops for good.
   */
  val waiting: Flow<Set<String>>
}
