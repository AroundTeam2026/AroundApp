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
import com.github.aroundteam2026.aroundapp.model.party.PartyMemberStatus
import com.github.aroundteam2026.aroundapp.model.party.PartyRepository
import com.github.aroundteam2026.aroundapp.model.party.PartyRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.party.partyCapacity
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.PartyPreview
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
 * It looks a party code up once it is long enough and shows the party it leads to, or why it can't
 * be joined. [join], the screen's button, joins the party found; with none found, it looks the
 * typed code up, which also retries a failed lookup. When a join is refused, it looks the party up
 * again to say why, e.g. that it filled up meanwhile.
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
   * [PARTY_CODE_LENGTH] characters besides spaces, dropping the lookup of the code it replaces.
   * Does nothing while joining or once joined.
   */
  fun updateCode(code: String) {
    if (mutableUiState.value.join != JoinPartyUiState.Join.IDLE) return
    lookingUp?.cancel()
    mutableUiState.update { it.copy(code = code, lookup = JoinPartyUiState.Lookup.Idle) }
    if (code.count { !it.isWhitespace() } >= PARTY_CODE_LENGTH) startLookup()
  }

  /**
   * Called when the explorer taps the button. Joins the party found; otherwise looks the typed code
   * up again, even a short one. Does nothing with an empty code, while joining, or once joined, so
   * a double tap joins once.
   */
  fun join() {
    val current = mutableUiState.value
    if (!current.canSubmit) return
    lookingUp?.cancel()
    val found = current.lookup as? JoinPartyUiState.Lookup.Found
    if (found == null) {
      startLookup()
      return
    }
    mutableUiState.update { it.copy(join = JoinPartyUiState.Join.JOINING) }
    viewModelScope.launch { joinParty(found.party) }
  }

  private fun startLookup() {
    mutableUiState.update { it.copy(lookup = JoinPartyUiState.Lookup.Looking) }
    val code = mutableUiState.value.code
    lookingUp = viewModelScope.launch {
      val result = lookUp { reservationRepository.findReservationIdByCode(code).getOrThrow() }
      mutableUiState.update { it.copy(lookup = result) }
    }
  }

  /** Adds the explorer to [party]; if that fails, shows why. */
  private suspend fun joinParty(party: OverviewRow) {
    partyRepository
        .join(party.reservationId, explorerUid)
        .onSuccess { mutableUiState.update { it.copy(join = JoinPartyUiState.Join.JOINED) } }
        .onFailure { failure ->
          Log.w(TAG, "Joining the party of ${party.reservationId} failed", failure)
          // The rules refuse a full, ended or already joined party alike: look it up to tell which
          var lookup = lookUp { party.reservationId }
          if (lookup is JoinPartyUiState.Lookup.Found) lookup = refusalFor(failure)
          mutableUiState.update { it.copy(lookup = lookup, join = JoinPartyUiState.Join.IDLE) }
        }
  }

  /**
   * The status of the explorer's membership in the party of [reservationId], or null if they have
   * none. Only a party's members can read its memberships, so for anyone else the read is refused,
   * which also reads as no membership.
   */
  private suspend fun membershipOf(reservationId: String): PartyMemberStatus? =
      try {
        partyRepository
            .observeMembers(reservationId)
            .first()
            .firstOrNull { it.uid == explorerUid }
            ?.status
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        null
      }

  /** The party whose reservation id [reservationId] gives, or why it can't be joined. */
  private suspend fun lookUp(reservationId: suspend () -> String): JoinPartyUiState.Lookup =
      try {
        val preview = reservationRepository.getPartyPreview(reservationId()).getOrThrow()
        when (preview.reservation.status) {
          ReservationStatus.APPROVED -> refused(JoinPartyUiState.Error.Closed)
          ReservationStatus.REJECTED,
          ReservationStatus.CANCELLED -> refused(JoinPartyUiState.Error.Ended)
          ReservationStatus.PENDING -> openParty(preview)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        refusalFor(e)
      }

  /**
   * The pending party of [preview], found if the explorer isn't in it and it has room, with its
   * quest and venue.
   */
  private suspend fun openParty(preview: PartyPreview): JoinPartyUiState.Lookup {
    val reservation = preview.reservation
    val quest = questRepository.getQuest(reservation.questId)
    val venue = venueRepository.getVenue(reservation.venueId)
    val membership = membershipOf(reservation.id)
    return when {
      quest == null || venue == null -> refused(JoinPartyUiState.Error.Ended)
      membership == PartyMemberStatus.JOINED -> refused(JoinPartyUiState.Error.AlreadyJoined)
      membership == PartyMemberStatus.WITHDRAWN -> refused(JoinPartyUiState.Error.Left)
      preview.joinedCount >= quest.partyCapacity ->
          refused(JoinPartyUiState.Error.Full(preview.joinedCount, quest.partyCapacity))
      else -> {
        val counts = mapOf(reservation.id to preview.joinedCount)
        val row = overviewRows(listOf(reservation to quest), listOf(venue), counts, emptyMap())
        JoinPartyUiState.Lookup.Found(row.single())
      }
    }
  }

  /** The refusal a failed read or join stands for: no such party, offline, or neither. */
  private suspend fun refusalFor(failure: Throwable): JoinPartyUiState.Lookup.Refused =
      refused(
          when {
            failure is NoSuchElementException -> JoinPartyUiState.Error.NotFound
            !connectivityRepository.isOnline.first() -> JoinPartyUiState.Error.Offline
            else -> JoinPartyUiState.Error.Failed
          }
      )

  private fun refused(error: JoinPartyUiState.Error) = JoinPartyUiState.Lookup.Refused(error)

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
