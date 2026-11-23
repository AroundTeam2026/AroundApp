// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.connectivity.ConnectivityRepository
import com.github.aroundteam2026.aroundapp.model.connectivity.ConnectivityRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.RefusedWrites
import com.github.aroundteam2026.aroundapp.model.reservation.RefusedWritesProvider
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.bookQuest
import com.github.aroundteam2026.aroundapp.model.reservation.isActive
import com.github.aroundteam2026.aroundapp.model.user.UserRepository
import com.github.aroundteam2026.aroundapp.model.user.UserRepositoryProvider
import com.github.aroundteam2026.aroundapp.ui.observeOffline
import com.github.aroundteam2026.aroundapp.ui.observeOnlineNow
import com.github.aroundteam2026.aroundapp.ui.venuepage.AcceptStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
 * @property offline Whether the device is offline, as the app-wide offline banner says: accepting
 *   needs a connection.
 */
data class NearbyAcceptUiState(
    val acceptedQuestIds: Set<String> = emptySet(),
    val acceptingQuestIds: Set<String> = emptySet(),
    val failedQuestIds: Set<String> = emptySet(),
    val available: Boolean = false,
    val offline: Boolean = false,
) {
  /**
   * Whether "Accept quest" can be tapped: accepting is [available] and the device isn't [offline].
   */
  val canAccept: Boolean
    get() = available && !offline

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
 * quest being accepted stays so until the server answered and its reservation shows up. One whose
 * booking failed shows the failure until it is accepted again, and so does one whose reservation
 * the server refused after the app wrote it. If the reservations can't be read, accepting is
 * unavailable until the map is opened again. While [NearbyAcceptUiState.offline] says the device is
 * offline, which it follows from [connectivityRepository] as the offline banner does, accepting
 * can't be tapped.
 */
class NearbyAcceptViewModel(
    private val reservationRepository: ReservationRepository,
    private val userRepository: UserRepository,
    private val explorerUid: String,
    connectivityRepository: ConnectivityRepository,
    private val refusedWrites: RefusedWrites,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
  private val hearing = refusedWrites.hearing()
  private val _uiState =
      MutableStateFlow(NearbyAcceptUiState(failedQuestIds = refusedWrites.refusedQuests))
  /** Where accepting each nearby quest stands. */
  val uiState: StateFlow<NearbyAcceptUiState> = _uiState.asStateFlow()

  /** The explorer's reservations, as last listed; null until listed. */
  private var latest: List<Reservation>? = null

  /** Whether the device is online now: booking needs a connection, as from a venue's page. */
  private val online = connectivityRepository.observeOnlineNow(viewModelScope, TAG)

  /**
   * The quests whose reservation is being created and the backend hasn't answered yet. Firestore
   * lists such a write at once, so it doesn't count as accepted until the booking returns.
   */
  private val unconfirmed = mutableSetOf<String>()

  /**
   * The quests whose booking just failed. Firestore may still list the failed write, as pending, so
   * the quest doesn't count as accepted until no pending reservation is listed for it.
   */
  private val refusing = mutableSetOf<String>()

  init {
    connectivityRepository
        .observeOffline(TAG)
        .onEach { offline -> _uiState.update { it.copy(offline = offline) } }
        .launchIn(viewModelScope)
    // NOSONAR below: Sonar's S1874 takes Flow.catch for deprecated code, but it isn't deprecated
    viewModelScope.launch {
      reservationRepository
          .observeForExplorer(explorerUid)
          .catch { // NOSONAR
            Log.w(TAG, "Listening to the reservations of $explorerUid failed", it)
            _uiState.update { state -> state.copy(available = false) }
          }
          .collect { reservations ->
            latest = reservations
            // A failed write is over once Firestore no longer lists it as pending; an earlier
            // reservation of the quest, cancelled and still listed, doesn't keep it
            refusing.retainAll { quest ->
              reservations.any { it.questId == quest && it.status == ReservationStatus.PENDING }
            }
            show()
          }
    }
  }

  /**
   * Shows what the reservations last listed: a quest with one that still stands is accepted, unless
   * its booking is [unconfirmed] or [refusing]. A reservation the server refused after the app
   * wrote it shows as failed.
   */
  private fun show() {
    val listed = latest ?: return
    hearing.refusedIn(listed)
    // All of them, not only the newly refused: the venue page may have heard the refusal first
    val refused = refusedWrites.refusedQuests
    val accepted =
        listed.filter { it.status.isActive }.map { it.questId }.toSet() - unconfirmed - refusing
    _uiState.update {
      it.copy(
          acceptedQuestIds = accepted,
          acceptingQuestIds = it.acceptingQuestIds - accepted - refused,
          failedQuestIds = it.failedQuestIds - accepted + refused,
          available = true,
      )
    }
  }

  /**
   * Books [quest] as a pending reservation, with the explorer as its party's first member. Does
   * nothing while accepting is unavailable, or once the quest is accepted or being accepted, so a
   * double tap books once.
   */
  fun accept(quest: NearbyQuest) {
    val id = quest.questId
    val current = _uiState.value
    if (!current.available || id in current.acceptedQuestIds || id in current.acceptingQuestIds) {
      return
    }
    if (!online.value) {
      _uiState.update { it.copy(failedQuestIds = it.failedQuestIds + id) }
      return
    }
    _uiState.update {
      it.copy(
          acceptingQuestIds = it.acceptingQuestIds + id,
          failedQuestIds = it.failedQuestIds - id,
      )
    }
    unconfirmed += id
    refusing -= id
    viewModelScope.launch {
      val result =
          reservationRepository.bookQuest(userRepository, explorerUid, id, quest.venueId, clock())
      unconfirmed -= id
      result
          .onSuccess { reservationId ->
            // The quest stays "accepting" until the reservation shows up in the listener
            refusedWrites.record(reservationId, id)
            show()
          }
          .onFailure {
            Log.w(TAG, "Accepting quest $id failed", it)
            refusing += id
            _uiState.update { state ->
              state.copy(
                  acceptingQuestIds = state.acceptingQuestIds - id,
                  failedQuestIds = state.failedQuestIds + id,
              )
            }
            show()
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
            ConnectivityRepositoryProvider.repository(this[APPLICATION_KEY]!!),
            refusedWrites =
                RefusedWritesProvider.refusedWrites(this[APPLICATION_KEY]!!, explorerUid),
        )
      }
    }
  }
}
