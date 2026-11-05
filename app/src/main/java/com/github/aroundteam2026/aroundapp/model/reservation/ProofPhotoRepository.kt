// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.net.Uri

/**
 * A proof photo must be smaller than this many bytes, 5 MB, as `storage.rules` require.
 * [ProofPhotoRepository.upload] checks it before uploading.
 */
const val PROOF_PHOTO_LIMIT_BYTES = 5 * 1024 * 1024

/**
 * Uploads the photos explorers submit as [Proof.Photo], and downloads them for the venue that
 * reviews them and the party that took them. ViewModels depend on this interface only.
 */
interface ProofPhotoRepository {
  /**
   * Uploads [photo] as a new proof photo of the reservation [reservationId] and returns its Cloud
   * Storage location (`gs://...`), which [Proof.Photo] stores.
   *
   * Unlike a download URL, the location is only read through the security rules. The rules only let
   * a joined member of an approved reservation upload an image, and a refusal fails the same way on
   * every retry. Every call uploads a new photo, so a retry leaves the earlier one unused.
   *
   * Waits a bounded time and, unlike a Firestore write, fails while offline. [photo] must still be
   * readable: after the app was closed or the photo deleted, it must be picked again. Cancelling
   * the caller cancels the upload.
   *
   * @param reservationId id of the approved reservation the photo proves; not blank, without `/`.
   * @param photo the photo to upload, smaller than [PROOF_PHOTO_LIMIT_BYTES], e.g. as returned by
   *   the photo picker or the camera.
   * @return the photo's `gs://` location, or a failure if [reservationId] isn't valid or [photo] is
   *   too large ([IllegalArgumentException]), if [photo] can't be read, or if the upload was
   *   refused, failed, or didn't finish in time.
   */
  suspend fun upload(reservationId: String, photo: Uri): Result<String>

  /**
   * Downloads the proof photo stored at [location], so a screen can show it without reading Cloud
   * Storage itself. A photo is smaller than [PROOF_PHOTO_LIMIT_BYTES], so it is returned whole, in
   * a new array on every call. Waits for the download to finish, for a bounded time, and fails if
   * it doesn't finish in time.
   *
   * The security rules only let the venue of the photo's reservation and the joined members of its
   * party read it, so the Storage implementation can fail where the fake doesn't check.
   *
   * @param location the photo's `gs://` location, as returned by [upload] and stored in
   *   [Proof.Photo].
   * @return the photo's bytes, or a failure if [location] isn't a `gs://` location in this
   *   repository's bucket ([IllegalArgumentException]), if there is no photo at it
   *   ([NoSuchElementException]), or if the read was refused, failed, or didn't finish in time.
   */
  suspend fun download(location: String): Result<ByteArray>
}
