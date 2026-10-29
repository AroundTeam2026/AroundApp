// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.overview

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.isActive
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the overview of the quests the explorer [explorerUid] accepted, for the Quests tab.
 *
 * It listens to the explorer's reservations and keeps the pending and approved ones, each with its
 * quest, read once, and its venue, which it listens to. Rows follow the reservations and the venues
 * as they change. If anything fails, including the reservations' listener after sign-out, the
 * overview shows [OverviewStatus.FAILED] until [retry]. The explorer can [cancel] a reservation.
 */
class OverviewViewModel(
    private val explorerUid: String,
    private val reservationRepository: ReservationRepository,
    private val questRepository: QuestRepository,
    private val venueRepository: VenueRepository,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow(OverviewUiState())
  /** What the overview shows now; the screen observes it. */
  val uiState: StateFlow<OverviewUiState> = mutableUiState.asStateFlow()

  private var listening: Job? = null

  init {
    listen()
  }

  /**
   * Called when the explorer cancels the reservation [reservationId]. Does nothing unless it is one
   * of the rows, or while it is being cancelled, so a double tap cancels once. The row shows as
   * cancelling until the listener drops it. A failure, such as being offline, keeps the row and
   * marks it failed, unless the row left meanwhile; calling this again retries.
   */
  fun cancel(reservationId: String) {
    val current = mutableUiState.value
    if (
        reservationId in current.cancellingIds ||
            current.rows.none { it.reservationId == reservationId }
    ) {
      return
    }
    mutableUiState.update {
      it.copy(
          cancellingIds = it.cancellingIds + reservationId,
          failedCancelIds = it.failedCancelIds - reservationId,
      )
    }
    viewModelScope.launch {
      reservationRepository.updateStatus(reservationId, ReservationStatus.CANCELLED).onFailure {
        Log.w(TAG, "Cancelling reservation $reservationId failed", it)
        mutableUiState.update { state ->
          // A row that left meanwhile, e.g. rejected by the venue, has nothing to mark
          val shown = state.rows.any { row -> row.reservationId == reservationId }
          state.copy(
              cancellingIds = state.cancellingIds - reservationId,
              failedCancelIds =
                  if (shown) state.failedCancelIds + reservationId else state.failedCancelIds,
          )
        }
      }
    }
  }

  /** Called when the explorer asks to try again after a failure; does nothing otherwise. */
  fun retry() {
    if (mutableUiState.value.status != OverviewStatus.FAILED) return
    mutableUiState.value = OverviewUiState()
    listen()
  }

  /**
   * Listens to the explorer's reservations and their venues until the ViewModel is cleared or
   * listening fails. A reservation's quest doesn't change, so each quest is read once per
   * listening, and a missing one is remembered as missing.
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
            venueRepository
                .observeVenues(booked.map { (reservation, _) -> reservation.venueId }.toSet())
                .map { venues -> overviewRows(booked, venues) }
          }
          .catch {
            Log.w(TAG, "Listening to the reservations of $explorerUid failed", it)
            mutableUiState.value = OverviewUiState(status = OverviewStatus.FAILED)
          }
          .collect { rows ->
            // A reservation that left the rows, e.g. once cancelled, is no longer cancelling
            val ids = rows.map { it.reservationId }.toSet()
            mutableUiState.update {
              it.copy(
                  status = OverviewStatus.LOADED,
                  rows = rows,
                  cancellingIds = it.cancellingIds intersect ids,
                  failedCancelIds = it.failedCancelIds intersect ids,
              )
            }
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
        .filter { it.status.isActive }
        .sortedWith(
            compareBy<Reservation> { it.slotStart }
                .thenByDescending { it.createdAt }
                .thenBy { it.id }
        )

/**
 * The rows for the [booked] reservations and their quests, in their order. A reservation whose
 * venue isn't among [venues], because it no longer exists, has no row.
 */
internal fun overviewRows(
    booked: List<Pair<Reservation, Quest>>,
    venues: List<Venue>,
): List<OverviewRow> {
  val venuesById = venues.associateBy { it.id }
  return booked.mapNotNull { (reservation, quest) ->
    venuesById[reservation.venueId]?.let { venue -> overviewRow(reservation, quest, venue) }
  }
}

private fun overviewRow(reservation: Reservation, quest: Quest, venue: Venue) =
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
        partySize = reservation.explorerUids.size,
        status =
            if (reservation.status == ReservationStatus.APPROVED) OverviewRowStatus.APPROVED
            else OverviewRowStatus.WAITING,
    )
