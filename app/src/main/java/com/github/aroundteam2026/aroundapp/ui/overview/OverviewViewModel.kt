// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.overview

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.party.PartyRepository
import com.github.aroundteam2026.aroundapp.model.party.PartyRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.party.observeJoinedCounts
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepositoryProvider
import com.github.aroundteam2026.aroundapp.ui.map.INITIALS_AVATAR
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Holds the overview of the quests the explorer [explorerUid] accepted, for the Quests tab.
 *
 * It listens to the explorer's reservations and keeps the pending and approved ones, each with its
 * quest, read once, and its venue and party, which it listens to. Rows follow the reservations, the
 * venues and the parties as they change. If anything fails, including the reservations' listener
 * after sign-out, the overview shows [OverviewStatus.FAILED] until [retry].
 */
class OverviewViewModel(
    private val explorerUid: String,
    private val reservationRepository: ReservationRepository,
    private val questRepository: QuestRepository,
    private val venueRepository: VenueRepository,
    private val partyRepository: PartyRepository,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow(OverviewUiState())
  /** What the overview shows now; the screen observes it. */
  val uiState: StateFlow<OverviewUiState> = mutableUiState.asStateFlow()

  private var listening: Job? = null

  init {
    listen()
  }

  /** Called when the explorer asks to try again after a failure; does nothing otherwise. */
  fun retry() {
    if (mutableUiState.value.status != OverviewStatus.FAILED) return
    mutableUiState.value = OverviewUiState()
    listen()
  }

  /**
   * Listens to the explorer's reservations, their venues and their parties until the ViewModel is
   * cleared or listening fails. A reservation's quest doesn't change, so each quest is read once
   * per listening, and a missing one is remembered as missing.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun listen() {
    listening?.cancel()
    val quests = mutableMapOf<String, Quest?>()
    listening = viewModelScope.launch {
      reservationRepository
          .observeForExplorer(explorerUid)
          .map { reservations ->
            val active = activeReservations(reservations)
            quests +=
                readQuests(active.map { it.questId }.distinct().filterNot(quests::containsKey))
            active.mapNotNull { reservation ->
              quests[reservation.questId]?.let { reservation to it }
            }
          }
          .flatMapLatest { booked ->
            val reservations = booked.map { (reservation, _) -> reservation }
            combine(
                venueRepository.observeVenues(reservations.map { it.venueId }.toSet()),
                partyRepository.observeJoinedCounts(reservations.map { it.id }),
            ) { venues, partySizes ->
              overviewRows(booked, venues, partySizes)
            }
          }
          .catch {
            Log.w(TAG, "Listening to the reservations of $explorerUid failed", it)
            mutableUiState.value = OverviewUiState(status = OverviewStatus.FAILED)
          }
          .collect { rows ->
            mutableUiState.value = OverviewUiState(status = OverviewStatus.LOADED, rows = rows)
          }
    }
  }

  /** The quests with the ids [questIds], read in parallel; null for those that no longer exist. */
  private suspend fun readQuests(questIds: List<String>): Map<String, Quest?> = coroutineScope {
    questIds.map { id -> async { id to questRepository.getQuest(id) } }.awaitAll().toMap()
  }

  companion object {
    private const val TAG = "OverviewViewModel"

    /**
     * Builds the [OverviewViewModel] of the explorer [explorerUid], with the app's repositories.
     */
    fun factory(explorerUid: String): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        OverviewViewModel(
            explorerUid,
            ReservationRepositoryProvider.repository,
            QuestRepositoryProvider.repository,
            VenueRepositoryProvider.repository,
            PartyRepositoryProvider.repository,
        )
      }
    }
  }
}

/**
 * The pending and approved reservations among [reservations], soonest slot first, then the newest,
 * ties going to the smallest id, so the order never depends on the repository's.
 */
internal fun activeReservations(reservations: List<Reservation>): List<Reservation> =
    reservations
        .filter {
          it.status == ReservationStatus.PENDING || it.status == ReservationStatus.APPROVED
        }
        .sortedWith(
            compareBy<Reservation> { it.slotStart }
                .thenByDescending { it.createdAt }
                .thenBy { it.id }
        )

/**
 * The rows for the [booked] reservations and their quests, in their order, with the number of
 * joined members of each party from [partySizes], keyed by reservation id. A reservation whose
 * venue isn't among [venues], because it no longer exists, has no row. A party missing from
 * [partySizes] counts as just the explorer, who is in every party they see here.
 */
internal fun overviewRows(
    booked: List<Pair<Reservation, Quest>>,
    venues: List<Venue>,
    partySizes: Map<String, Int>,
): List<OverviewRow> {
  val venuesById = venues.associateBy { it.id }
  return booked.mapNotNull { (reservation, quest) ->
    venuesById[reservation.venueId]?.let { venue ->
      // A safety net only: observeJoinedCounts gives the size of every party it is asked for
      overviewRow(reservation, quest, venue, partySizes[reservation.id] ?: 1)
    }
  }
}

private fun overviewRow(reservation: Reservation, quest: Quest, venue: Venue, partySize: Int) =
    OverviewRow(
        reservationId = reservation.id,
        questId = quest.id,
        venueId = venue.id,
        questTitle = quest.title,
        reward = quest.reward,
        venueName = venue.name,
        address = venue.address,
        avatar = INITIALS_AVATAR(venue, venue.name),
        slotStart = reservation.slotStart,
        partySize = partySize,
        status =
            if (reservation.status == ReservationStatus.APPROVED) OverviewRowStatus.APPROVED
            else OverviewRowStatus.WAITING,
    )
