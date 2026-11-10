// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.joinparty

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.connectivity.ConnectivityRepository
import com.github.aroundteam2026.aroundapp.model.connectivity.ConnectivityRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.party.PARTY_CODE_LENGTH
import com.github.aroundteam2026.aroundapp.model.party.PartyRepository
import com.github.aroundteam2026.aroundapp.model.party.PartyRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.party.partyCapacity
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepositoryProvider
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewRow
import com.github.aroundteam2026.aroundapp.ui.overview.overviewRows
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the Join a party screen of the explorer [explorerUid].
 *
 * It looks a party code up as soon as it is long enough, showing the party it leads to, or why the
 * party can't be joined: no party has the code, it is full, it has ended or the venue closed it by
 * approving it, or the device is offline. [join], the screen's button, joins the party found; with
 * none found, it looks the typed code up, which also tries a failed lookup again. When the party
 * turns out not to be joinable once the explorer asks, e.g. because it filled up meanwhile, the
 * screen shows that instead.
 */
class JoinPartyViewModel(
    private val explorerUid: String,
    private val reservationRepository: ReservationRepository,
    private val questRepository: QuestRepository,
    private val venueRepository: VenueRepository,
    private val partyRepository: PartyRepository,
    private val connectivityRepository: ConnectivityRepository,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow(JoinPartyUiState())
  /** What the screen shows now; the screen observes it. */
  val uiState: StateFlow<JoinPartyUiState> = mutableUiState.asStateFlow()

  private var lookingUp: Job? = null

  /**
   * Called when the explorer edits the party code to [code]. Looks it up once it has
   * [PARTY_CODE_LENGTH] characters besides spaces, dropping any lookup of the code it replaces, and
   * shows nothing for a shorter one. Does nothing while joining or once joined.
   */
  fun updateCode(code: String) {
    if (mutableUiState.value.join != JoinPartyUiState.Join.IDLE) return
    lookingUp?.cancel()
    mutableUiState.update { it.copy(code = code, lookup = JoinPartyUiState.Lookup.Idle) }
    if (code.count { !it.isWhitespace() } >= PARTY_CODE_LENGTH) startLookup()
  }

  /**
   * Called when the explorer taps the button. Joins the party found; otherwise looks the typed code
   * up again, even a short one, which says so when no party has it. Does nothing with an empty
   * code, while joining, or once joined, so a double tap joins once.
   */
  fun join() {
    val current = mutableUiState.value
    if (!current.canSubmit) return
    val found = current.lookup as? JoinPartyUiState.Lookup.Found
    if (found == null) {
      lookingUp?.cancel()
      startLookup()
      return
    }
    lookingUp?.cancel()
    mutableUiState.update { it.copy(join = JoinPartyUiState.Join.JOINING) }
    viewModelScope.launch { joinParty(found.party) }
  }

  /** Looks the typed code up, showing [JoinPartyUiState.Lookup.Looking] until it has an answer. */
  private fun startLookup() {
    mutableUiState.update { it.copy(lookup = JoinPartyUiState.Lookup.Looking) }
    val code = mutableUiState.value.code
    lookingUp = viewModelScope.launch {
      val result = lookUp(code)
      mutableUiState.update { it.copy(lookup = result) }
    }
  }

  /** Adds the explorer to [party]; if that fails, shows why, looking the party up again. */
  private suspend fun joinParty(party: OverviewRow) {
    partyRepository
        .join(party.reservationId, explorerUid)
        .onSuccess { mutableUiState.update { it.copy(join = JoinPartyUiState.Join.JOINED) } }
        .onFailure { failure ->
          Log.w(TAG, "Joining the party of ${party.reservationId} failed", failure)
          val now = lookUp(mutableUiState.value.code)
          val lookup =
              if (now !is JoinPartyUiState.Lookup.Found) now
              else if (isMemberOf(party))
                  JoinPartyUiState.Lookup.Refused(JoinPartyUiState.Error.AlreadyJoined)
              else refusalFor(failure)
          mutableUiState.update { it.copy(lookup = lookup, join = JoinPartyUiState.Join.IDLE) }
        }
  }

  /** Whether the explorer is joined in the party of [party], e.g. as its creator. */
  private suspend fun isMemberOf(party: OverviewRow): Boolean = attempt {
    reservationRepository.observeForExplorer(explorerUid).first()
  }
      .getOrNull()
      .orEmpty()
      .any { it.id == party.reservationId }

  /** What the party the typed [code] leads to is: joinable, or the reason it isn't. */
  private suspend fun lookUp(code: String): JoinPartyUiState.Lookup {
    val id =
        reservationRepository.findReservationIdByCode(code).getOrElse {
          return refusalFor(it)
        }
    val preview =
        reservationRepository.getPartyPreview(id).getOrElse {
          return refusalFor(it)
        }
    val reservation = preview.reservation
    when (reservation.status) {
      ReservationStatus.REJECTED,
      ReservationStatus.CANCELLED -> return refused(JoinPartyUiState.Error.Ended)
      ReservationStatus.APPROVED -> return refused(JoinPartyUiState.Error.Closed)
      ReservationStatus.PENDING -> Unit
    }
    val quest =
        attempt { questRepository.getQuest(reservation.questId) }
            .getOrElse {
              return refusalFor(it)
            } ?: return refused(JoinPartyUiState.Error.Ended)
    if (preview.joinedCount >= quest.partyCapacity) {
      return refused(JoinPartyUiState.Error.Full(preview.joinedCount, quest.partyCapacity))
    }
    val venue =
        attempt { venueRepository.getVenue(reservation.venueId) }
            .getOrElse {
              return refusalFor(it)
            } ?: return refused(JoinPartyUiState.Error.Ended)
    val party =
        overviewRows(
                listOf(reservation to quest),
                listOf(venue),
                mapOf(reservation.id to preview.joinedCount),
                emptyMap(),
            )
            .single()
    return JoinPartyUiState.Lookup.Found(party)
  }

  /** The refusal [failure] of a read or a join stands for: no such party, offline, or neither. */
  private suspend fun refusalFor(failure: Throwable): JoinPartyUiState.Lookup.Refused =
      refused(
          when {
            failure is NoSuchElementException -> JoinPartyUiState.Error.NotFound
            !connectivityRepository.isOnline.first() -> JoinPartyUiState.Error.Offline
            else -> JoinPartyUiState.Error.Failed
          }
      )

  private fun refused(error: JoinPartyUiState.Error) = JoinPartyUiState.Lookup.Refused(error)

  /** The result of [block], or its failure, unless the coroutine was cancelled. */
  private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
      try {
        Result.success(block())
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }

  companion object {
    private const val TAG = "JoinPartyViewModel"

    /**
     * Builds the [JoinPartyViewModel] of the explorer [explorerUid], with the app's repositories.
     */
    fun factory(explorerUid: String): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        JoinPartyViewModel(
            explorerUid,
            ReservationRepositoryProvider.repository,
            QuestRepositoryProvider.repository,
            VenueRepositoryProvider.repository,
            PartyRepositoryProvider.repository,
            ConnectivityRepositoryProvider.repository(this[APPLICATION_KEY]!!),
        )
      }
    }
  }
}
