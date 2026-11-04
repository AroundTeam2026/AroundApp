// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [CompletionRepository] for unit tests and for running the app without Firestore.
 *
 * Completions are kept in insertion order in a [MutableStateFlow]. Each `observe*` flow emits again
 * only when its own result changes, not on every write. Each completion is stored under its
 * [completionId]. Not thread-safe; use one instance per test.
 *
 * Unlike the security rules, it doesn't check who writes, that the reservation is approved, or that
 * the proof is of the quest's type; set [forcedFailure] to test a refusal.
 *
 * @param now Clock used for `submittedAt`, in epoch milliseconds. Pass a fixed value in tests to
 *   make timestamps predictable.
 */
class FakeCompletionRepository(private val now: () -> Long = System::currentTimeMillis) :
    CompletionRepository {
  /**
   * Simulates a backend error (e.g. no network or a refused write) so callers' error paths can be
   * tested. While non-null, [submit], [approve] and [reject] with a non-blank reason return
   * `Result.failure(forcedFailure)` and change nothing; a blank reason is the caller's mistake, so
   * it fails first, as it would without a backend. Set it back to null to make writes succeed
   * again.
   */
  var forcedFailure: Throwable? = null

  /** All stored completions, keyed by id. */
  private val completions = MutableStateFlow<Map<String, Completion>>(emptyMap())

  /**
   * Stores a pending completion under its [completionId] with [now] as its submission time, and
   * returns the id. Returns [forcedFailure] instead, storing nothing, if it is set, and an
   * [IllegalStateException], changing nothing, if a completion is already stored under that id.
   */
  override suspend fun submit(
      reservationId: String,
      venueId: String,
      explorerUid: String,
      proof: Proof,
  ): Result<String> {
    forcedFailure?.let {
      return Result.failure(it)
    }
    val id = completionId(reservationId, explorerUid)
    if (id in completions.value) {
      return Result.failure(IllegalStateException("Completion $id was already submitted"))
    }
    val stored =
        Completion(
            id = id,
            reservationId = reservationId,
            venueId = venueId,
            explorerUid = explorerUid,
            proof = proof,
            status = CompletionStatus.PENDING,
            rejectReason = null,
            submittedAt = now(),
        )
    completions.update { it + (id to stored) }
    return Result.success(id)
  }

  /** Emits every stored completion of [uid], and again whenever that list changes. */
  override fun observeForExplorer(uid: String): Flow<List<Completion>> = observe {
    it.explorerUid == uid
  }

  /** Emits every stored completion of [venueId], and again whenever that list changes. */
  override fun observeForVenue(venueId: String): Flow<List<Completion>> = observe {
    it.venueId == venueId
  }

  /** Emits every stored completion of [reservationId], and again whenever that list changes. */
  override fun observeForReservation(reservationId: String): Flow<List<Completion>> = observe {
    it.reservationId == reservationId
  }

  /**
   * Approves the completion with [id]. Fails, changing nothing, if there is no such completion, if
   * [canTransition] forbids the change, or if [forcedFailure] is set.
   */
  override suspend fun approve(id: String): Result<Unit> =
      answer(id, CompletionStatus.APPROVED, rejectReason = null)

  /** Fails with [IllegalArgumentException] if [reason] is blank, before looking up [id]. */
  override suspend fun reject(id: String, reason: String): Result<Unit> {
    if (reason.isBlank()) {
      return Result.failure(IllegalArgumentException("A rejection needs a reason"))
    }
    return answer(id, CompletionStatus.REJECTED, reason)
  }

  /** Emits the stored completions that match [keep], and again whenever that list changes. */
  private fun observe(keep: (Completion) -> Boolean): Flow<List<Completion>> =
      completions.map { all -> all.values.filter(keep) }.distinctUntilChanged()

  /**
   * Gives the completion with [id] the venue's answer, [status] with [rejectReason], keeping every
   * other field. Returns a failure, changing nothing, if there is no such completion, if
   * [canTransition] forbids the change, or if [forcedFailure] is set.
   */
  private fun answer(id: String, status: CompletionStatus, rejectReason: String?): Result<Unit> {
    forcedFailure?.let {
      return Result.failure(it)
    }
    val current =
        completions.value[id]
            ?: return Result.failure(NoSuchElementException("No completion with id $id"))
    if (!canTransition(current.status, status)) {
      return Result.failure(
          IllegalStateException("A ${current.status} completion cannot become $status")
      )
    }
    completions.update { it + (id to current.copy(status = status, rejectReason = rejectReason)) }
    return Result.success(Unit)
  }
}
