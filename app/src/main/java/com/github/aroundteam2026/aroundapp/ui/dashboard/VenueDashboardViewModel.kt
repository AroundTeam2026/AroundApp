// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the dashboard of the signed-in venue: its reservations, split into those it has to answer
 * and the approved ones still ahead, with each reservation's quest title and explorer's name.
 *
 * It follows the signed-in venue, and keeps up as its reservations and quests change and as the day
 * changes, by [clock] in [zone]. It only listens while the dashboard is watched. If listening
 * fails, the dashboard shows [VenueDashboardStatus.FAILED] until [retry].
 *
 * [approve] and [decline] answer a pending reservation; a failure sets
 * [VenueDashboardUiState.updateFailed] until [clearUpdateError].
 */
class VenueDashboardViewModel(
    authRepository: AuthRepository,
    private val reservationRepository: ReservationRepository,
    questRepository: QuestRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
  /** Bumped by [retry]: each new value restarts the listeners, even for the same venue. */
  private val retries = MutableStateFlow(0)

  /** The answers being saved and whether the last one failed. */
  private val answers = MutableStateFlow(Answers())

  private data class Answers(
      val saving: Set<String> = emptySet(),
      val failed: Boolean = false,
  )

  /** What the dashboard shows now; the screen observes it. */
  @OptIn(ExperimentalCoroutinesApi::class)
  val uiState: StateFlow<VenueDashboardUiState> =
      combine(
              combine(authRepository.currentUserId, retries) { venueId, _ -> venueId }
                  .flatMapLatest { venueId ->
                    if (venueId == null) flowOf(VenueDashboardUiState())
                    else listen(venueId, questRepository)
                  },
              answers,
          ) { listened, answers ->
            listened.copy(savingIds = answers.saving, updateFailed = answers.failed)
          }
          .stateIn(
              viewModelScope,
              SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
              VenueDashboardUiState(),
          )

  /** Approves the pending reservation [reservationId]; see [answer]. */
  fun approve(reservationId: String) = answer(reservationId, ReservationStatus.APPROVED)

  /** Declines the pending reservation [reservationId]; see [answer]. */
  fun decline(reservationId: String) = answer(reservationId, ReservationStatus.REJECTED)

  /** Clears [VenueDashboardUiState.updateFailed], once the screen has shown it. */
  fun clearUpdateError() {
    answers.update { it.copy(failed = false) }
  }

  /** Listens again, after listening failed. */
  fun retry() {
    retries.update { it + 1 }
  }

  /**
   * Moves the reservation [reservationId] to [status]. Does nothing while an answer to it is being
   * saved, so a double tap saves once. A failure sets [VenueDashboardUiState.updateFailed]; on
   * success, the listener shows the new status.
   */
  private fun answer(reservationId: String, status: ReservationStatus) {
    val before = answers.getAndUpdate { it.copy(saving = it.saving + reservationId) }
    if (reservationId in before.saving) return
    viewModelScope.launch {
      reservationRepository.updateStatus(reservationId, status).onFailure {
        Log.w(TAG, "Answering reservation $reservationId with $status failed", it)
        answers.update { answers -> answers.copy(failed = true) }
      }
      answers.update { it.copy(saving = it.saving - reservationId) }
    }
  }

  /**
   * The dashboard of the venue [venueId], from its reservations and quests, again at each midnight
   * so a past day's reservations leave "Upcoming". A failing listener gives a failed dashboard.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun listen(venueId: String, quests: QuestRepository): Flow<VenueDashboardUiState> =
      combine(
              reservationRepository.observeForVenue(venueId),
              quests.observeQuestsByVenue(venueId),
              ::Pair,
          )
          .transformLatest { (reservations, venueQuests) ->
            while (true) {
              val now = clock()
              emit(dashboardOf(reservations, venueQuests, now, zone))
              delay(nextStartOfDay(now, zone) - now)
            }
          }
          // The repositories' flows throw if the backend stops a listener
          .catch {
            Log.w(TAG, "Listening to the dashboard of $venueId failed", it)
            emit(VenueDashboardUiState(status = VenueDashboardStatus.FAILED))
          }

  companion object {
    private const val TAG = "VenueDashboardViewModel"

    /** How long the listeners outlive the tab, so a quick rotation doesn't restart them. */
    private const val STOP_TIMEOUT_MS = 5_000L

    /** Builds the [VenueDashboardViewModel] of the signed-in venue with the app's repositories. */
    fun factory(): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        VenueDashboardViewModel(
            AuthRepositoryProvider.repository,
            ReservationRepositoryProvider.repository,
            QuestRepositoryProvider.repository,
        )
      }
    }
  }
}

/**
 * The loaded dashboard for [reservations] at [now] in [zone], titled from [quests]. Pending
 * reservations need an answer, oldest request first. Approved ones with a slot from today on are
 * upcoming, soonest first. Ties go to the smallest id. Rejected and cancelled ones are left out.
 *
 * A pending reservation's slot can be in the past: accepting a quest doesn't pick a slot yet, so it
 * only dates the request. It still needs an answer.
 */
internal fun dashboardOf(
    reservations: List<Reservation>,
    quests: List<Quest>,
    now: Long,
    zone: ZoneId,
): VenueDashboardUiState {
  val titles = quests.associate { it.id to it.title }
  val today = startOfDay(now, zone)
  val tomorrow = nextStartOfDay(now, zone)
  val approved = reservations.filter { it.status == ReservationStatus.APPROVED }
  return VenueDashboardUiState(
      status = VenueDashboardStatus.LOADED,
      needsAnswer =
          reservations
              .filter { it.status == ReservationStatus.PENDING }
              .sortedWith(compareBy<Reservation> { it.createdAt }.thenBy { it.id })
              .map { it.toDashboardReservation(titles) },
      upcoming =
          approved
              .filter { it.slotStart >= today }
              .sortedWith(compareBy<Reservation> { it.slotStart }.thenBy { it.id })
              .map { it.toDashboardReservation(titles) },
      todayCount = approved.count { it.slotStart in today until tomorrow },
  )
}

/** The first millisecond of the day [time] falls on, in [zone]. */
internal fun startOfDay(time: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(time)
        .atZone(zone)
        .toLocalDate()
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

/** The first millisecond of the day after the one [time] falls on, in [zone]. */
internal fun nextStartOfDay(time: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(time)
        .atZone(zone)
        .toLocalDate()
        .plusDays(1)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

private fun Reservation.toDashboardReservation(titles: Map<String, String>) =
    DashboardReservation(
        reservationId = id,
        questTitle = titles[questId],
        explorerName = explorerName.ifBlank { null },
        partySize = explorerUids.size,
        slotStart = slotStart,
        approved = status == ReservationStatus.APPROVED,
    )
