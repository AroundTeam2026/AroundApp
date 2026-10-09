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
   * The security rules only let an explorer reserve for a party they belong to, so the Firestore
   * implementation's server rejects a reservation whose `explorerUids` lacks the signed-in user.
   * That rejection comes after this call has returned, so it is not reported here.
   *
   * @param reservation the reservation to store; its `id`, `createdAt` and `status` are ignored.
   * @return the new reservation's id, or a failure if the write could not be issued. Success does
   *   not wait for the backend to confirm the write (e.g. while offline), and a later rejection is
   *   not reported here.
   */
  suspend fun createReservation(reservation: Reservation): Result<String>

  /**
   * Observes the reservations made for one venue's quests. Emits the current list on collection,
   * then again whenever one is added or changes.
   *
   * @param venueId id of the venue whose reservations to return.
   * @return all of that venue's reservations, whatever their status, in unspecified order.
   * @throws Exception from the flow if the backend stops the listener (e.g. permission denied after
   *   sign-out). Collectors must catch it, e.g. with `.catch`, or it crashes their scope.
   */
  fun observeForVenue(venueId: String): Flow<List<Reservation>>

  /**
   * Observes the reservations an explorer is part of. Emits on collection and on every change.
   *
   * @param uid uid of the explorer.
   * @return every reservation whose party includes [uid], whatever its status, in unspecified
   *   order.
   * @throws Exception from the flow if the backend stops the listener (e.g. permission denied after
   *   sign-out). Collectors must catch it, e.g. with `.catch`, or it crashes their scope.
   */
  fun observeForExplorer(uid: String): Flow<List<Reservation>>

  /**
   * Moves a reservation to [status], changing nothing else.
   *
   * For now only the venue that owns the reservation can change its status, because the security
   * rules allow nothing else. The Firestore implementation therefore fails when an explorer calls
   * this, for example to cancel their own reservation (E21), even though the fake allows it. The
   * rules will have to change when E21 is scheduled.
   *
   * @param id id of the reservation.
   * @param status the new status; the change must be allowed by [canTransition].
   * @return success, or a failure if no reservation can be changed under this id, if the change is
   *   not allowed ([IllegalStateException]), or if the write failed.
   */
  suspend fun updateStatus(id: String, status: ReservationStatus): Result<Unit>
}
