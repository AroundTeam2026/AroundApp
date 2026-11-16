// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.bookQuest
import com.github.aroundteam2026.aroundapp.model.reservation.isActive
import com.github.aroundteam2026.aroundapp.model.user.UserRepository
import com.github.aroundteam2026.aroundapp.model.user.UserRepositoryProvider
import com.github.aroundteam2026.aroundapp.ui.venuepage.AcceptStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Where accepting each nearby quest stands, by quest id.
 *
 * @property acceptedQuestIds Quests the explorer has a pending or approved reservation for.
 * @property acceptingQuestIds Quests being booked right now.
 * @property failedQuestIds Quests whose last booking failed; accepting again retries.
 * @property available Whether quests can be accepted: not until the explorer's reservations are
 *   known, nor while they can't be read, so a quest already accepted isn't booked twice.
 */
data class NearbyAcceptUiState(
    val acceptedQuestIds: Set<String> = emptySet(),
    val acceptingQuestIds: Set<String> = emptySet(),
    val failedQuestIds: Set<String> = emptySet(),
    val available: Boolean = false,
) {
  /** Where accepting the quest [questId] stands. */
  fun statusOf(questId: String): AcceptStatus =
      when (questId) {
        in acceptedQuestIds -> AcceptStatus.ACCEPTED
        in acceptingQuestIds -> AcceptStatus.ACCEPTING
        in failedQuestIds -> AcceptStatus.FAILED
        else -> AcceptStatus.NONE
      }
}

/**
 * Accepts the map's nearby quests for the explorer [explorerUid], as the bar of a venue's page
 * does.
 *
 * It keeps up with the explorer's reservations: a quest with one that still stands is accepted. A
 * quest being accepted stays so until its reservation shows up, and one whose booking failed shows
 * the failure until it is accepted again. If the reservations can't be read, accepting is
 * unavailable until the map is opened again.
 */
class NearbyAcceptViewModel(
    private val reservationRepository: ReservationRepository,
    private val userRepository: UserRepository,
    private val explorerUid: String,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
  private val _uiState = MutableStateFlow(NearbyAcceptUiState())
  /** Where accepting each nearby quest stands. */
  val uiState: StateFlow<NearbyAcceptUiState> = _uiState.asStateFlow()

  init {
    // NOSONAR below: Sonar's S1874 takes Flow.catch for deprecated code, but it isn't deprecated
    viewModelScope.launch {
      reservationRepository
          .observeForExplorer(explorerUid)
          .catch { // NOSONAR
            Log.w(TAG, "Listening to the reservations of $explorerUid failed", it)
            _uiState.update { state -> state.copy(available = false) }
          }
          .collect { reservations ->
            val accepted = reservations.filter { it.status.isActive }.map { it.questId }.toSet()
            _uiState.update {
              it.copy(
                  acceptedQuestIds = accepted,
                  acceptingQuestIds = it.acceptingQuestIds - accepted,
                  available = true,
              )
            }
          }
    }
  }

  /**
   * Books [quest] as a pending reservation, with the explorer as its party's first member. Does
   * nothing while accepting is unavailable, or once the quest is accepted or being accepted, so a
   * double tap books once.
   *
   * Known limit: a booking the server refuses after the app wrote it only drops out of the accepted
   * quests, without "Try again". The venue page's bar tells the two apart; the map could do the
   * same if it needs to.
   */
  fun accept(quest: NearbyQuest) {
    val id = quest.questId
    val current = _uiState.value
    if (!current.available || id in current.acceptedQuestIds || id in current.acceptingQuestIds) {
      return
    }
    _uiState.update {
      it.copy(
          acceptingQuestIds = it.acceptingQuestIds + id,
          failedQuestIds = it.failedQuestIds - id,
      )
    }
    viewModelScope.launch {
      // On success, the quest stays "accepting" until the reservation shows up in the listener
      reservationRepository
          .bookQuest(userRepository, explorerUid, id, quest.venueId, clock())
          .onFailure {
            Log.w(TAG, "Accepting quest $id failed", it)
            _uiState.update { state ->
              state.copy(
                  acceptingQuestIds = state.acceptingQuestIds - id,
                  failedQuestIds = state.failedQuestIds + id,
              )
            }
          }
    }
  }

  companion object {
    private const val TAG = "NearbyAccept"

    /** Builds the ViewModel of the explorer [explorerUid] on the app's repositories. */
    fun factory(explorerUid: String): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        NearbyAcceptViewModel(
            ReservationRepositoryProvider.repository,
            UserRepositoryProvider.repository,
            explorerUid,
        )
      }
    }
  }
}
