// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import com.github.aroundteam2026.aroundapp.model.party.PartyMemberStatus
import com.github.aroundteam2026.aroundapp.model.party.meetsPartySize
import kotlinx.coroutines.flow.Flow

/** Reads and writes [Reservation]s. ViewModels depend on this interface only. */
interface ReservationRepository {
  /**
   * Stores a new pending reservation and makes [creatorUid] the first [PartyMemberStatus.JOINED]
   * member of its party, in one atomic write.
   *
   * The repository sets `id`, `createdAt` and `status`; their values in [reservation] are ignored.
   *
   * Waits a bounded time for the backend. A refusal by the security rules in that time, e.g. for an
   * inactive quest, is returned as a failure. Without an answer, e.g. while offline, the write
   * stays queued and this returns success, so a later refusal is not reported.
   *
   * @param reservation the reservation to store; its `id`, `createdAt` and `status` are ignored.
   * @param creatorUid uid of the explorer making the reservation, who joins its party.
   * @return the new reservation's id, or a failure if the write could not be issued or the backend
   *   refused it in time.
   */
  suspend fun createReservation(reservation: Reservation, creatorUid: String): Result<String>

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
   * Observes the reservations an explorer is part of. Emits on collection and on every change,
   * including when the explorer joins or withdraws from a party.
   *
   * @param uid uid of the explorer.
   * @return every reservation whose party [uid] joined and didn't withdraw from, whatever its
   *   status, in unspecified order.
   * @throws Exception from the flow if the backend stops the listener (e.g. permission denied after
   *   sign-out). Collectors must catch it, e.g. with `.catch`, or it crashes their scope.
   */
  fun observeForExplorer(uid: String): Flow<List<Reservation>>

  /**
   * Moves a reservation to [status], changing nothing else.
   *
   * The security rules let the venue that owns the reservation make any change, and a joined member
   * of the party only cancel it (E21). The Firestore implementation therefore fails when an
   * explorer asks for any other status, even though the fake allows it. The rules also refuse to
   * approve a party without the size its quest asks for ([meetsPartySize]).
   *
   * @param id id of the reservation.
   * @param status the new status; the change must be allowed by [canTransition].
   * @return success, or a failure if no reservation can be changed under this id, if the change is
   *   not allowed ([IllegalStateException]), if the party doesn't have its quest's size when
   *   approving, or if the write failed.
   */
  suspend fun updateStatus(id: String, status: ReservationStatus): Result<Unit>
}
