// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.net.Uri

/**
 * In-memory [ProofPhotoRepository] for unit tests and for running the app without Cloud Storage.
 *
 * Nothing is uploaded: each photo is recorded in [uploads] under a location like
 * `gs://fake-bucket/proofs/{reservationId}/photo-1`, numbered per instance. Unlike the security
 * rules, it doesn't check who uploads, that the reservation is approved, or the photo's type and
 * size; set [forcedFailure] to test a refusal. Not thread-safe; use one instance per test.
 */
class FakeProofPhotoRepository : ProofPhotoRepository {
  /**
   * Simulates a failed upload (e.g. no network or a refused photo) so callers' error paths can be
   * tested. While non-null, [upload] returns `Result.failure(forcedFailure)` and records nothing.
   * Set it back to null to make uploads succeed again.
   */
  var forcedFailure: Throwable? = null

  /** A copy of every uploaded photo, keyed by its location, in upload order. */
  val uploads: Map<String, Uri>
    get() = stored.toMap()

  private val stored = linkedMapOf<String, Uri>()

  /** Number used for the next photo's id. */
  private var nextId = 1

  /**
   * Records [photo] under a new location in [reservationId]'s proofs and returns it. Returns
   * [forcedFailure] instead, recording nothing, if it is set.
   */
  override suspend fun upload(reservationId: String, photo: Uri): Result<String> {
    forcedFailure?.let {
      return Result.failure(it)
    }
    val location = "$PROOFS$reservationId/$PHOTO_ID_PREFIX${nextId++}"
    stored[location] = photo
    return Result.success(location)
  }

  private companion object {
    const val PROOFS = "gs://fake-bucket/proofs/"
    const val PHOTO_ID_PREFIX = "photo-"
  }
}
