// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.net.Uri

/**
 * In-memory [ProofPhotoRepository] for unit tests and for running the app without Cloud Storage.
 *
 * Nothing is uploaded: each photo is recorded in [uploads] under a location like
 * `gs://fake-bucket/proofs/{reservationId}/photo-1`, numbered per instance, together with the bytes
 * [read] gives for it, which [download] returns. Unlike the security rules, it doesn't check who
 * uploads or downloads, that the reservation is approved, or the photo's type and size; set
 * [forcedFailure] to test a refusal. Not thread-safe; use one instance per test.
 *
 * @param read Gives the bytes of an uploaded photo, read once when it is uploaded. A unit test
 *   can't read a [Uri], so pass the bytes [download] should return; by default they're empty.
 */
class FakeProofPhotoRepository(private val read: (Uri) -> ByteArray = { ByteArray(0) }) :
    ProofPhotoRepository {
  /**
   * Simulates a failed transfer (e.g. no network or a refused photo) so callers' error paths can be
   * tested. While non-null, [upload] and [download] return `Result.failure(forcedFailure)`, and
   * [upload] records nothing. Set it back to null to make them succeed again.
   */
  var forcedFailure: Throwable? = null

  /** A copy of every uploaded photo, keyed by its location, in upload order. */
  val uploads: Map<String, Uri>
    get() = stored.mapValues { it.value.photo }

  private val stored = linkedMapOf<String, StoredPhoto>()

  /** Number used for the next photo's id. */
  private var nextId = 1

  /**
   * Records [photo] and its bytes under a new location in [reservationId]'s proofs and returns it.
   * Returns [forcedFailure] instead, recording nothing, if it is set.
   */
  override suspend fun upload(reservationId: String, photo: Uri): Result<String> {
    forcedFailure?.let {
      return Result.failure(it)
    }
    val location = "$PROOFS$reservationId/$PHOTO_ID_PREFIX${nextId++}"
    stored[location] = StoredPhoto(photo, read(photo))
    return Result.success(location)
  }

  /**
   * Returns the bytes recorded for [location] when it was uploaded. Fails with
   * [NoSuchElementException] if nothing was uploaded there, and with [forcedFailure] if it is set.
   */
  override suspend fun download(location: String): Result<ByteArray> {
    forcedFailure?.let {
      return Result.failure(it)
    }
    val photo =
        stored[location]
            ?: return Result.failure(NoSuchElementException("No proof photo at $location"))
    return Result.success(photo.bytes)
  }

  /** An uploaded [photo] and the [bytes] [read] gave for it. */
  private class StoredPhoto(val photo: Uri, val bytes: ByteArray)

  private companion object {
    const val PROOFS = "gs://fake-bucket/proofs/"
    const val PHOTO_ID_PREFIX = "photo-"
  }
}
