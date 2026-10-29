// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.party

import kotlinx.coroutines.flow.Flow

/** Reads and writes [PartyMember]s. ViewModels depend on this interface only. */
interface PartyRepository {
  /**
   * Makes [uid] a [PartyMemberStatus.JOINED] member of the party of [reservationId]. The repository
   * sets `joinedAt` and `updatedAt`.
   *
   * The security rules only let an explorer join as themselves, a pending reservation, once: the
   * Firestore implementation fails when the server refuses.
   *
   * @return success, or a failure if [uid] already has a membership for this reservation, whatever
   *   its status, or if the write failed.
   */
  suspend fun join(reservationId: String, uid: String): Result<Unit>

  /**
   * Moves [uid]'s membership in the party of [reservationId] to [PartyMemberStatus.WITHDRAWN] and
   * sets its `updatedAt`. The membership is kept.
   *
   * @return success, or a failure if there is no such membership, if it is already withdrawn
   *   ([IllegalStateException]), or if the write failed.
   */
  suspend fun withdraw(reservationId: String, uid: String): Result<Unit>

  /**
   * Observes the party of one reservation. Emits the current members on collection, then again
   * whenever one joins or withdraws.
   *
   * @param reservationId id of the reservation.
   * @return every membership of that reservation, joined or withdrawn, in unspecified order.
   * @throws Exception from the flow if the backend stops the listener (e.g. permission denied for
   *   someone outside the party). Collectors must catch it, e.g. with `.catch`, or it crashes their
   *   scope.
   */
  fun observeMembers(reservationId: String): Flow<List<PartyMember>>
}
