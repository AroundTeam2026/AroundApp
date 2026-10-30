// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import com.github.aroundteam2026.aroundapp.model.party.FakePartyRepository
import com.github.aroundteam2026.aroundapp.model.party.PartyRepository
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
 * Parties live in [party], like `party_user` in Firestore: [createReservation] joins the creator
 * there. Share [party] with the code under test to read the memberships.
 *
 * @param now Clock used for `createdAt`, in epoch milliseconds. Pass a fixed value in tests to make
 *   timestamps predictable.
 * @param party Where the parties of the reservations are kept.
 */
class FakeReservationRepository(
    private val now: () -> Long = System::currentTimeMillis,
    private val party: PartyRepository = FakePartyRepository(now),
) : ReservationRepository {
  /**
   * Simulates a backend error (e.g. no network or a rejected write) so callers' error paths can be
   * tested. While non-null, [createReservation] and [updateStatus] return
   * `Result.failure(forcedFailure)` and change nothing, not even the party. Set it back to null to
   * make writes succeed again.
   */
  var forcedFailure: Throwable? = null

  /** All stored reservations, keyed by id. */
  private val reservations = MutableStateFlow<Map<String, Reservation>>(emptyMap())

  /** Number used for the next generated id. */
  private var nextId = 1

  /**
   * Joins [creatorUid] to the party of a new id in [party], then stores a copy of [reservation]
   * with that id, [now] as its creation time and [ReservationStatus.PENDING] as its status, and
   * returns the id. Returns [forcedFailure] instead, storing nothing, if it is set, and the failure
   * of [party] if joining fails, without storing the reservation.
   */
  override suspend fun createReservation(
      reservation: Reservation,
      creatorUid: String,
  ): Result<String> {
    forcedFailure?.let {
      return Result.failure(it)
    }
    val id = "reservation-${nextId++}"
    party.join(id, creatorUid).onFailure {
      return Result.failure(it)
    }
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
