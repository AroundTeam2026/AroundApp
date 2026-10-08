// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import kotlinx.coroutines.flow.Flow

/** Reads and writes [Reservation]s. ViewModels depend on this interface only. */
interface ReservationRepository {
  /**
   * Stores a new reservation. The repository, not the caller, sets `id`, `createdAt` and `status`:
   * it generates a fresh id, sets the creation time, and starts the reservation as
   * [ReservationStatus.PENDING]. The values of those three fields in [reservation] are ignored.
   *
   * The Firestore implementation fails if the signed-in user is not in `explorerUids`, because the
   * security rules only let an explorer reserve for a party they belong to.
   *
   * @param reservation the reservation to store; its `id`, `createdAt` and `status` are ignored.
   * @return the new reservation's id, or a failure if it could not be stored.
   */
  suspend fun createReservation(reservation: Reservation): Result<String>

  /**
   * Observes the reservations made for one venue's quests. Emits the current list on collection,
   * then again whenever one is added or changes.
   *
   * @param venueId id of the venue whose reservations to return.
   * @return all of that venue's reservations, whatever their status, in unspecified order.
   */
  fun observeForVenue(venueId: String): Flow<List<Reservation>>

  /**
   * Observes the reservations an explorer is part of. Emits on collection and on every change.
   *
   * @param uid uid of the explorer.
   * @return every reservation whose party includes [uid], whatever its status, in unspecified
   *   order.
   */
  fun observeForExplorer(uid: String): Flow<List<Reservation>>

  /**
   * Moves a reservation to [status], changing nothing else.
   *
   * @param id id of the reservation.
   * @param status the new status; the change must be allowed by [canTransition].
   * @return success, or a failure if no reservation can be changed under this id, if the change is
   *   not allowed ([IllegalStateException]), or if the write failed.
   */
  suspend fun updateStatus(id: String, status: ReservationStatus): Result<Unit>
}
