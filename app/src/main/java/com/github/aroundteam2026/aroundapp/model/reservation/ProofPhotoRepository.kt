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
   * Uploads [photo] as a new proof photo of the reservation [reservationId], under
   * `proofs/{reservationId}/{photoId}` with a fresh `photoId`, and returns its Cloud Storage
   * location (`gs://...`), which [Proof.Photo] stores. It is not a download URL: those can be read
   * by anyone who has them, while reads of the location go through the security rules.
   *
   * Every call uploads a new photo and never replaces or deletes one, so a retry or a retake leaves
   * the earlier photo unused. Waits for the upload to finish, for a bounded time: unlike a
   * Firestore write, an upload isn't kept for later while offline, so it fails if it doesn't finish
   * in time.
   *
   * A blank [reservationId], or one with a `/` that would nest the folder, and a photo of
   * [PROOF_PHOTO_LIMIT_BYTES] or more fail with [IllegalArgumentException] before anything is
   * uploaded; a photo that large must be made smaller first.
   *
   * The Storage implementation uploads with an `image/...` content type, which the security rules
   * require: from the `ContentResolver`, or `image/jpeg` when it has none, as for some camera
   * files.
   *
   * The security rules only let a joined member of an approved reservation upload, and only an
   * image, so the Storage implementation can fail where the fake doesn't check. Such a refusal
   * fails the same way on every retry: e.g. a reservation cancelled after its approval never takes
   * a proof again.
   *
   * [photo] must still be readable when this is called: the photo picker only lets the app read
   * what it picked while the screen that picked it lives, so after the app was closed, or if the
   * photo was deleted, it fails and the photo must be picked again. Cancelling the caller cancels
   * the upload.
   *
   * @param reservationId id of the approved reservation the photo proves.
   * @param photo the photo to upload, e.g. as returned by the photo picker or the camera.
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
