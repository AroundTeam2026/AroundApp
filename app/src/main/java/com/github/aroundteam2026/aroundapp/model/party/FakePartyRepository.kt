// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.party

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [PartyRepository] for unit tests and for running the app without Firestore.
 *
 * Unlike the rules, it doesn't check that the reservation exists and is pending, or who joins.
 * [observeMembers] emits again only when its own result changes. Not thread-safe; use one instance
 * per test.
 *
 * @param now Clock used for `joinedAt` and `updatedAt`, in epoch milliseconds.
 */
class FakePartyRepository(private val now: () -> Long = System::currentTimeMillis) :
    PartyRepository {
  /**
   * Simulates a backend error. While non-null, [join] and [withdraw] return
   * `Result.failure(forcedFailure)` and change nothing.
   */
  var forcedFailure: Throwable? = null

  /** All memberships, keyed by [partyMemberId]. */
  private val members = MutableStateFlow<Map<String, PartyMember>>(emptyMap())

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
    members.update {
      it + (id to current.copy(status = PartyMemberStatus.WITHDRAWN, updatedAt = now()))
    }
    return Result.success(Unit)
  }

  override fun observeMembers(reservationId: String): Flow<List<PartyMember>> =
      members
          .map { all -> all.values.filter { it.reservationId == reservationId } }
          .distinctUntilChanged()
}
