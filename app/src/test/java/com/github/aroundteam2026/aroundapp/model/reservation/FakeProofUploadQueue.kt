// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [ProofUploadQueue] for unit tests. Each queued proof is recorded in [queued] and its
 * reservation listed in [waiting] until a test calls [finish], as if its upload ended. Nothing is
 * uploaded. Not thread-safe; use one instance per test.
 */
class FakeProofUploadQueue : ProofUploadQueue {
  /**
   * Simulates a proof that can't be queued, so callers' error paths can be tested. While non-null,
   * [enqueue] returns `Result.failure(forcedFailure)` and queues nothing. Set it back to null to
   * make it succeed again.
   */
  var forcedFailure: Throwable? = null

  /** Every proof queued, in order; a proof kept in place of a later one is listed once. */
  val queued: List<QueuedProof>
    get() = recorded.toList()

  private val recorded = mutableListOf<QueuedProof>()

  private val waitingIds = MutableStateFlow<Set<String>>(emptySet())

  override val waiting: Flow<Set<String>> = waitingIds

  /**
   * Records the proof and lists [reservationId] in [waiting]. Fails with [forcedFailure] if it is
   * set, and keeps the waiting proof if [reservationId] already has one, as [ProofUploadQueue]
   * describes.
   */
  override suspend fun enqueue(
      reservationId: String,
      venueId: String,
      explorerUid: String,
      photo: File,
  ): Result<Unit> {
    forcedFailure?.let {
      return Result.failure(it)
    }
    if (reservationId !in waitingIds.value) {
      recorded += QueuedProof(reservationId, venueId, explorerUid, photo)
      waitingIds.update { it + reservationId }
    }
    return Result.success(Unit)
  }

  /** Ends [reservationId]'s upload, whether it was submitted or stopped: it leaves [waiting]. */
  fun finish(reservationId: String) {
    waitingIds.update { it - reservationId }
  }

  /** A proof [enqueue] queued, with the arguments it was given. */
  data class QueuedProof(
      val reservationId: String,
      val venueId: String,
      val explorerUid: String,
      val photo: File,
  )
}
