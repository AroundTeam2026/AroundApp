// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [ReservationRepository] for unit tests and for running the app without Firestore.
 *
 * Reservations are kept in insertion order in a [MutableStateFlow]. Each `observe*` flow emits
 * again only when its own result changes, not on every write. Ids are generated as `reservation-1`,
 * `reservation-2`, ... and are unique per instance. Not thread-safe; use one instance per test.
 *
 * @param now Clock used for `createdAt`, in epoch milliseconds. Pass a fixed value in tests to make
 *   timestamps predictable.
 */
class FakeReservationRepository(private val now: () -> Long = System::currentTimeMillis) :
    ReservationRepository {
  /**
   * Simulates a backend error (e.g. no network or a rejected write) so callers' error paths can be
   * tested. While non-null, [createReservation] and [updateStatus] return
   * `Result.failure(forcedFailure)` and change nothing. Set it back to null to make writes succeed
   * again.
   */
  var forcedFailure: Throwable? = null

  /** All stored reservations, keyed by id. */
  private val reservations = MutableStateFlow<Map<String, Reservation>>(emptyMap())

  /** Number used for the next generated id. */
  private var nextId = 1

  /**
   * Stores a copy of [reservation] with a new id, [now] as its creation time and
   * [ReservationStatus.PENDING] as its status, and returns that id. Returns [forcedFailure]
   * instead, storing nothing, if it is set.
   */
  override suspend fun createReservation(reservation: Reservation): Result<String> {
    forcedFailure?.let {
      return Result.failure(it)
    }
    val id = "reservation-${nextId++}"
    val stored = reservation.copy(id = id, status = ReservationStatus.PENDING, createdAt = now())
    reservations.update { it + (id to stored) }
    return Result.success(id)
  }

  /** Emits every stored reservation of [venueId], and again whenever that list changes. */
  override fun observeForVenue(venueId: String): Flow<List<Reservation>> =
      reservations.map { all -> all.values.filter { it.venueId == venueId } }.distinctUntilChanged()

  /**
   * Emits every stored reservation whose party includes [uid], and again when that list changes.
   */
  override fun observeForExplorer(uid: String): Flow<List<Reservation>> =
      reservations
          .map { all -> all.values.filter { uid in it.explorerUids } }
          .distinctUntilChanged()

  /**
   * Changes the status of the reservation with [id], keeping every other field. Returns a failure,
   * changing nothing, if there is no such reservation, if [canTransition] forbids the change, or if
   * [forcedFailure] is set.
   */
  override suspend fun updateStatus(id: String, status: ReservationStatus): Result<Unit> {
    forcedFailure?.let {
      return Result.failure(it)
    }
    val current =
        reservations.value[id]
            ?: return Result.failure(NoSuchElementException("No reservation with id $id"))
    if (!canTransition(current.status, status)) {
      return Result.failure(
          IllegalStateException("A ${current.status} reservation cannot become $status")
      )
    }
    reservations.update { it + (id to current.copy(status = status)) }
    return Result.success(Unit)
  }
}
