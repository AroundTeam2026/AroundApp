// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.net.Uri

/**
 * In-memory [ProofPhotoRepository] for unit tests and for running the app without Cloud Storage.
 *
 * Each upload is recorded in [uploads] under a `gs://fake-bucket/proofs/...` location, and
 * [download] returns the bytes [read] gave for it; an unknown location fails with
 * [NoSuchElementException]. Not thread-safe; use one instance per test.
 *
 * It doesn't check the security rules, never reads a [Uri] itself, and finishes at once: test those
 * cases with [forcedFailure], or hold the caller's dispatcher, e.g. with `StandardTestDispatcher`.
 *
 * @param read Gives the bytes of an uploaded photo, read once when it is uploaded. A unit test
 *   can't read a [Uri], so pass the bytes [download] should return; by default they're empty.
 */
class FakeProofPhotoRepository(private val read: (Uri) -> ByteArray = { ByteArray(0) }) :
    ProofPhotoRepository {
  /**
   * Simulates a failed transfer (e.g. no network or a refused photo) so callers' error paths can be
   * tested. While non-null, [upload] and [download] return `Result.failure(forcedFailure)`, and
   * [upload] records nothing. An invalid reservation id, a photo that is too large or a location
   * outside its bucket is the caller's mistake, so it fails first, as it would without a backend.
   * Set it back to null to make them succeed again.
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
   * Fails, recording nothing, with [IllegalArgumentException] if [reservationId] is blank or has a
   * `/`, or if the photo's bytes are [PROOF_PHOTO_LIMIT_BYTES] or more, before anything else, and
   * with [forcedFailure] if it is set.
   */
  override suspend fun upload(reservationId: String, photo: Uri): Result<String> {
    if (reservationId.isBlank() || '/' in reservationId) {
      return Result.failure(IllegalArgumentException("Invalid reservation id \"$reservationId\""))
    }
    val bytes = read(photo)
    if (bytes.size >= PROOF_PHOTO_LIMIT_BYTES) {
      return Result.failure(
          IllegalArgumentException("A proof photo must be under $PROOF_PHOTO_LIMIT_BYTES bytes")
      )
    }
    forcedFailure?.let {
      return Result.failure(it)
    }
    val location = "$PROOFS$reservationId/$PHOTO_ID_PREFIX${nextId++}"
    stored[location] = StoredPhoto(photo, bytes)
    return Result.success(location)
  }

  /**
   * Returns a copy of the bytes recorded for [location] when it was uploaded. Fails with
   * [IllegalArgumentException] if [location] isn't in its `gs://fake-bucket/` bucket, before
   * anything else, with [forcedFailure] if it is set, and with [NoSuchElementException] if nothing
   * was uploaded there.
   */
  override suspend fun download(location: String): Result<ByteArray> {
    if (!location.startsWith(BUCKET)) {
      return Result.failure(IllegalArgumentException("$location isn't in $BUCKET"))
    }
    forcedFailure?.let {
      return Result.failure(it)
    }
    val photo =
        stored[location]
            ?: return Result.failure(NoSuchElementException("No proof photo at $location"))
    return Result.success(photo.bytes.copyOf())
  }

  /** An uploaded [photo] and the [bytes] [read] gave for it. */
  private class StoredPhoto(val photo: Uri, val bytes: ByteArray)

  private companion object {
    const val BUCKET = "gs://fake-bucket/"
    const val PROOFS = "${BUCKET}proofs/"
    const val PHOTO_ID_PREFIX = "photo-"
  }
}
