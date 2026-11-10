// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import com.github.aroundteam2026.aroundapp.model.party.EVERY_PARTY_CODE_TAKEN
import com.github.aroundteam2026.aroundapp.model.party.FakePartyRepository
import com.github.aroundteam2026.aroundapp.model.party.MAX_PARTY_CODE_ATTEMPTS
import com.github.aroundteam2026.aroundapp.model.party.MAX_PARTY_SIZE
import com.github.aroundteam2026.aroundapp.model.party.PartyCodeNotFoundException
import com.github.aroundteam2026.aroundapp.model.party.PartyMemberStatus
import com.github.aroundteam2026.aroundapp.model.party.PartyRepository
import com.github.aroundteam2026.aroundapp.model.party.meetsPartySize
import com.github.aroundteam2026.aroundapp.model.party.newPartyCode
import com.github.aroundteam2026.aroundapp.model.party.normalizePartyCode
import com.github.aroundteam2026.aroundapp.model.party.observeJoinedCounts
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
 * @param newCode Draws the party code of a new reservation. Random by default; pass a taken code to
 *   cause a collision.
 */
class FakeReservationRepository(
    private val now: () -> Long = System::currentTimeMillis,
    party: PartyRepository? = null,
    private val quests: QuestRepository? = null,
    private val newCode: () -> String = { newPartyCode() },
) : ReservationRepository {
  /** Where the parties of the reservations are kept: the one given, or one of their own. */
  val party: PartyRepository = party ?: FakePartyRepository(capacityOf = ::capacityOf, now = now)

  /**
   * Simulates a backend error (e.g. no network or a rejected write) so callers' error paths can be
   * tested. While non-null, [createReservation], [findReservationIdByCode], [getPartyPreview] and
   * [updateStatus] return `Result.failure(forcedFailure)` and change nothing, not even the party.
   * Set it back to null to make them succeed again.
   */
  var forcedFailure: Throwable? = null

  /** All stored reservations, keyed by id. */
  private val reservations = MutableStateFlow<Map<String, Reservation>>(emptyMap())

  /** The id of the reservation of each party code, like the `party_codes` collection. */
  private val codes = mutableMapOf<String, String>()

  /** Number used for the next generated id. */
  private var nextId = 1

  /**
   * Draws a party code no stored reservation has, drawing again while [newCode] returns a taken one,
   * at most [MAX_PARTY_CODE_ATTEMPTS] codes in all. Then joins [creatorUid] to the party of a new id
   * in [party], stores a copy of [reservation] with that id, the code, [now] as its creation time
   * and [ReservationStatus.PENDING] as its status, and returns the id. Returns [forcedFailure]
   * instead, storing nothing, if it is set, an [IllegalStateException] if every code drawn was
   * taken, and the failure of [party] if joining fails, without storing the reservation.
   */
  override suspend fun createReservation(
      reservation: Reservation,
      creatorUid: String,
  ): Result<String> {
    forcedFailure?.let {
      return Result.failure(it)
    }
    val code =
        generateSequence { newCode() }.take(MAX_PARTY_CODE_ATTEMPTS).firstOrNull { it !in codes }
            ?: return Result.failure(IllegalStateException(EVERY_PARTY_CODE_TAKEN))
    val id = "reservation-${nextId++}"
    party.join(id, creatorUid).onFailure {
      return Result.failure(it)
    }
    val stored =
        reservation.copy(
            id = id,
            status = ReservationStatus.PENDING,
            createdAt = now(),
            partyCode = code,
        )
    codes[code] = id
    reservations.update { it + (id to stored) }
    return Result.success(id)
  }

  /**
   * Returns the id of the reservation with the party code [code], typed in any case and with
   * spaces, or a [PartyCodeNotFoundException] holding the normalized code, or [code] if it is not a
   * party code, if none has it. Returns [forcedFailure] if it is set.
   */
  override suspend fun findReservationIdByCode(code: String): Result<String> {
    forcedFailure?.let {
      return Result.failure(it)
    }
    val normalized = normalizePartyCode(code)
    val id = normalized?.let { codes[it] }
    // The normalized code when there is one, as the Firestore repository reports it
    return id?.let { Result.success(it) }
        ?: Result.failure(PartyCodeNotFoundException(normalized ?: code))
  }

  /**
   * Returns the stored reservation with [reservationId] and how many explorers are joined in its
   * party, or a [NoSuchElementException] if there is none. Returns [forcedFailure] if it is set.
   */
  override suspend fun getPartyPreview(reservationId: String): Result<PartyPreview> {
    forcedFailure?.let {
      return Result.failure(it)
    }
    val reservation =
        reservations.value[reservationId]
            ?: return Result.failure(NoSuchElementException("No reservation with id $reservationId"))
    return Result.success(PartyPreview(reservation, joinedCount(reservationId)))
  }

  /** How many explorers are joined in the party of [reservationId]. */
  private suspend fun joinedCount(reservationId: String): Int =
      party.observeJoinedCounts(listOf(reservationId)).first().getValue(reservationId)

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
      val joined = joinedCount(id)
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
