// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import com.github.aroundteam2026.aroundapp.model.party.MAX_PARTY_CODE_ATTEMPTS
import com.github.aroundteam2026.aroundapp.model.party.PartyCodeNotFoundException
import com.github.aroundteam2026.aroundapp.model.party.PartyMemberStatus
import com.github.aroundteam2026.aroundapp.model.party.meetsPartySize
import kotlinx.coroutines.flow.Flow

/** Reads and writes [Reservation]s. ViewModels depend on this interface only. */
interface ReservationRepository {
  /**
   * Stores a new pending reservation with a party code no other reservation has, and makes
   * [creatorUid] the first [PartyMemberStatus.JOINED] member of its party, in one atomic write.
   *
   * The repository sets `id`, `createdAt`, `status` and `partyCode`; their values in [reservation]
   * are ignored. A code that is already taken is replaced by a new one, at most
   * [MAX_PARTY_CODE_ATTEMPTS] codes in all; after that, creating fails with an
   * [IllegalStateException].
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
   * Finds the reservation whose party code an explorer typed, in any case and with spaces, so `7kq
   * m42` finds `7KQM42`. Asks the backend, never a cache, so a code is found only online.
   *
   * @param code the party code as typed.
   * @return the reservation's id, or a [PartyCodeNotFoundException] if no reservation has that code
   *   or it is not a party code, or another failure if the backend couldn't be asked, e.g. offline.
   *   [PartyCodeNotFoundException] is a [NoSuchElementException], which [updateStatus] also returns
   *   for a missing reservation, so check for it first.
   */
  suspend fun findReservationIdByCode(code: String): Result<String>

  /**
   * Reads the reservation with [reservationId] and its number of joined members, as an explorer who
   * hasn't joined its party sees it. Asks the backend, never a cache, so a party is read only
   * online. The security rules let an explorer read any reservation whose id they know, and a venue
   * its own.
   *
   * @param reservationId id of the reservation, e.g. from [findReservationIdByCode].
   * @return the [PartyPreview], or a [NoSuchElementException] if there is no such reservation or it
   *   can't be mapped, or another failure if the backend couldn't be asked, e.g. offline.
   */
  suspend fun getPartyPreview(reservationId: String): Result<PartyPreview>

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
   * The security rules let the venue that owns the reservation make any change while it is pending,
   * and a joined member of the party only cancel it (E21). The Firestore implementation therefore
   * fails when an explorer asks for any other status, even though the fake allows it. The rules
   * also refuse to approve a party without the size its quest asks for ([meetsPartySize]).
   *
   * @param id id of the reservation.
   * @param status the new status; the change must be allowed by [canTransition].
   * @return success, or a failure if no reservation can be changed under this id, if the change is
   *   not allowed ([IllegalStateException]), if the party doesn't have its quest's size when
   *   approving, or if the write failed.
   */
  suspend fun updateStatus(id: String, status: ReservationStatus): Result<Unit>
}
