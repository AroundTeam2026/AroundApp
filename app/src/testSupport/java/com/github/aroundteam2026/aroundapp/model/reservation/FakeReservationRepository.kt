// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import com.github.aroundteam2026.aroundapp.model.party.FakePartyRepository
import com.github.aroundteam2026.aroundapp.model.party.MAX_PARTY_SIZE
import com.github.aroundteam2026.aroundapp.model.party.PartyMemberStatus
import com.github.aroundteam2026.aroundapp.model.party.PartyRepository
import com.github.aroundteam2026.aroundapp.model.party.meetsPartySize
import com.github.aroundteam2026.aroundapp.model.party.partyCapacity
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [ReservationRepository] for tests.
 *
 * Reservations are kept in insertion order in a [MutableStateFlow]. Each `observe*` flow emits
 * again only when its own result changes, not on every write. Ids are generated as `reservation-1`,
 * `reservation-2`, ... and are unique per instance. Not thread-safe; use one instance per test.
 *
 * Parties live in [party], like `party_user` in Firestore: [createReservation] joins the creator
 * there, and [observeForExplorer] follows the explorer's joined memberships in it. Share [party]
 * with the code under test to make other explorers join or withdraw.
 *
 * @param now Clock used for `createdAt`, in epoch milliseconds. Pass a fixed value in tests to make
 *   timestamps predictable.
 * @param party Where the parties of the reservations are kept. By default, a [FakePartyRepository]
 *   of their own, which holds as many explorers as the [partyCapacity] of each reservation's quest
 *   in [quests], or [MAX_PARTY_SIZE] without them.
 * @param quests Where the quests of the reservations are read, to approve only a party of the size
 *   its quest asks for, as the rules do. Without it, approving doesn't check the party's size.
 */
class FakeReservationRepository(
    private val now: () -> Long = System::currentTimeMillis,
    party: PartyRepository? = null,
    private val quests: QuestRepository? = null,
) : ReservationRepository {
  /** Where the parties of the reservations are kept: the one given, or one of their own. */
  val party: PartyRepository = party ?: FakePartyRepository(capacityOf = ::capacityOf, now = now)

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
   * Emits every stored reservation whose party in [party] [uid] joined and didn't withdraw from,
   * and again when that list changes.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeForExplorer(uid: String): Flow<List<Reservation>> =
      reservations
          .flatMapLatest { all ->
            if (all.isEmpty()) {
              flowOf(emptyList())
            } else {
              combine(
                  all.values.map { reservation ->
                    party.observeMembers(reservation.id).map { members ->
                      reservation.takeIf {
                        members.any { it.uid == uid && it.status == PartyMemberStatus.JOINED }
                      }
                    }
                  }
              ) {
                it.filterNotNull()
              }
            }
          }
          .distinctUntilChanged()

  /**
   * Changes the status of the reservation with [id], keeping every other field. Returns a failure,
   * changing nothing, if there is no such reservation, if [canTransition] forbids the change, if
   * [forcedFailure] is set, or, given [quests], when approving a party that doesn't have the size its
   * quest asks for ([IllegalStateException]) or whose quest is missing ([NoSuchElementException]).
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
    if (status == ReservationStatus.APPROVED && quests != null) {
      val quest =
          quests.getQuest(current.questId)
              ?: return Result.failure(NoSuchElementException("No quest ${current.questId}"))
      val joined =
          party.observeMembers(id).first().count { it.status == PartyMemberStatus.JOINED }
      if (!quest.meetsPartySize(joined)) {
        return Result.failure(
            IllegalStateException("A party of $joined doesn't have the size of quest ${quest.id}")
        )
      }
    }
    reservations.update { it + (id to current.copy(status = status)) }
    return Result.success(Unit)
  }

  /**
   * The [partyCapacity] of the quest of the reservation with [reservationId], read from [quests].
   * [MAX_PARTY_SIZE] without [quests], or when the reservation or its quest is missing, as while
   * [createReservation] joins the creator before storing the reservation.
   */
  private suspend fun capacityOf(reservationId: String): Int {
    val questId = reservations.value[reservationId]?.questId ?: return MAX_PARTY_SIZE
    return quests?.getQuest(questId)?.partyCapacity ?: MAX_PARTY_SIZE
  }
}
