// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.party

import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [PartyRepository] for tests.
 *
 * Unlike the rules, it doesn't check that the reservation exists and is pending, or who joins. Like
 * them, it refuses a join while the party has as many joined members as [capacityOf] its
 * reservation, and a withdrawal by the creator. The creator is the first explorer to join a
 * reservation, as `FakeReservationRepository.createReservation` joins them before anyone else; two
 * `FakeReservationRepository`s sharing one instance would hand out the same ids and mix their
 * creators up. Only when given [statusOf] and [hasSentProof] does it also refuse a withdrawal from
 * a reservation that is neither pending nor approved, or by a member who sent their proof.
 * [observeMembers] emits again only when its own result changes. Not thread-safe; use one instance
 * per test.
 *
 * @param capacityOf The [partyCapacity] of the quest of a reservation, given its id. By default
 *   every party holds [MAX_PARTY_SIZE], as for a quest whose size isn't exact.
 * @param statusOf The status of a reservation, given its id. By default every reservation is
 *   pending.
 * @param hasSentProof Whether an explorer sent their proof for a reservation, given its id and the
 *   explorer's uid. By default nobody has.
 * @param now Clock used for `joinedAt` and `updatedAt`, in epoch milliseconds. Last, so tests can
 *   pass it as a trailing lambda.
 */
class FakePartyRepository(
    private val capacityOf: suspend (reservationId: String) -> Int = { MAX_PARTY_SIZE },
    private val statusOf: (reservationId: String) -> ReservationStatus = {
      ReservationStatus.PENDING
    },
    private val hasSentProof: (reservationId: String, uid: String) -> Boolean = { _, _ -> false },
    private val now: () -> Long = System::currentTimeMillis,
) : PartyRepository {
  /**
   * Simulates a backend error. While non-null, [join] and [withdraw] return
   * `Result.failure(forcedFailure)` and change nothing.
   */
  var forcedFailure: Throwable? = null

  /** All memberships, keyed by [partyMemberId]. */
  private val members = MutableStateFlow<Map<String, PartyMember>>(emptyMap())

  /** The uid of the creator of each reservation, keyed by reservation id. */
  private val creators = mutableMapOf<String, String>()

  override suspend fun join(reservationId: String, uid: String): Result<Unit> {
    forcedFailure?.let {
      return Result.failure(it)
    }
    val id = partyMemberId(reservationId, uid)
    if (id in members.value) {
      return Result.failure(
          IllegalStateException("$uid already has a membership in $reservationId")
      )
    }
    val joined =
        members.value.values.count {
          it.reservationId == reservationId && it.status == PartyMemberStatus.JOINED
        }
    if (joined >= capacityOf(reservationId)) {
      return Result.failure(IllegalStateException("The party of $reservationId is full"))
    }
    creators.putIfAbsent(reservationId, uid)
    val time = now()
    members.update {
      it + (id to PartyMember(reservationId, uid, PartyMemberStatus.JOINED, time, time))
    }
    return Result.success(Unit)
  }

  override suspend fun withdraw(reservationId: String, uid: String): Result<Unit> {
    forcedFailure?.let {
      return Result.failure(it)
    }
    val id = partyMemberId(reservationId, uid)
    val current =
        members.value[id]
            ?: return Result.failure(NoSuchElementException("No membership with id $id"))
    if (!canTransition(current.status, PartyMemberStatus.WITHDRAWN)) {
      return Result.failure(IllegalStateException("$uid already withdrew from $reservationId"))
    }
    refusalToWithdraw(reservationId, uid)?.let {
      return Result.failure(IllegalStateException(it))
    }
    members.update {
      it + (id to current.copy(status = PartyMemberStatus.WITHDRAWN, updatedAt = now()))
    }
    return Result.success(Unit)
  }

  /** Why the rules would refuse [uid]'s withdrawal from [reservationId], or null if they allow it. */
  private fun refusalToWithdraw(reservationId: String, uid: String): String? {
    val status = statusOf(reservationId)
    return when {
      creators[reservationId] == uid -> "$uid created $reservationId and can only cancel it"
      status != ReservationStatus.PENDING && status != ReservationStatus.APPROVED ->
          "Nobody can leave the $status reservation $reservationId"
      hasSentProof(reservationId, uid) -> "$uid sent their proof for $reservationId"
      else -> null
    }
  }

  override fun observeMembers(reservationId: String): Flow<List<PartyMember>> =
      members
          .map { all -> all.values.filter { it.reservationId == reservationId } }
          .distinctUntilChanged()
}
