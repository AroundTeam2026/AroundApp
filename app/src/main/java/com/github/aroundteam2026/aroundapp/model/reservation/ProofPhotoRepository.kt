// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.net.Uri

/**
 * Stores the photos explorers send as proof of a quest. ViewModels depend on this interface only.
 */
interface ProofPhotoRepository {
  /**
   * Uploads [photo] as a proof of the reservation [reservationId] and returns where it is stored,
   * to submit as a [Proof.Photo].
   *
   * Every call stores a new file, even for a photo uploaded before, so a retake never replaces an
   * earlier proof; a caller that retries should reuse the URL of a photo that was uploaded. The
   * storage rules only let a joined member of the party of an approved reservation upload, and only
   * an image smaller than 5 MB, so an implementation backed by Cloud Storage can refuse a photo
   * that a fake accepts.
   *
   * @param reservationId id of the approved reservation the photo is a proof of.
   * @param photo the photo to upload, e.g. the one the explorer took or picked.
   * @return the stored photo's URL, or a failure if the photo can't be read, the upload failed, or
   *   the backend refused it.
   */
  suspend fun upload(reservationId: String, photo: Uri): Result<String>
}
