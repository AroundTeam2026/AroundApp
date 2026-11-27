// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.visibility

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.connectivity.ConnectivityRepository
import com.github.aroundteam2026.aroundapp.model.location.LocationRepository
import com.github.aroundteam2026.aroundapp.model.presence.ExplorerPresence
import com.github.aroundteam2026.aroundapp.model.presence.PresenceRepository
import com.github.aroundteam2026.aroundapp.ui.observeOffline
import com.github.aroundteam2026.aroundapp.ui.observeOnlineNow
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lets the explorer [uid] be seen by other explorers for an hour, two hours or until tonight, or
 * hides them again.
 *
 * Showing reads the phone's current location and gives it to [PresenceRepository.show], which
 * rounds it before storing it. The state follows [PresenceRepository.observeOwn], and turns hidden
 * by itself once the chosen time passes, as Firestore's TTL policy can take a day to delete the
 * document. Showing and hiding need a connection.
 *
 * @param clock The current time in epoch milliseconds.
 * @param zone The time zone "tonight" is in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VisibilityViewModel(
    private val uid: String,
    private val presence: PresenceRepository,
    private val locations: LocationRepository,
    connectivity: ConnectivityRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
  /** Whether the phone is online now, so an action can't start from the first moment offline. */
  private val online = connectivity.observeOnlineNow(viewModelScope, TAG)
  private val loads = MutableStateFlow(0)
  private val sending = MutableStateFlow(Sending())

  /** The action that failed last, which [retry] runs again. */
  private var failed: (() -> Unit)? = null

  /**
   * The card's state. It changes on its own when the explorer stops being visible and at
   * [TONIGHT_HOUR]:00, when "until tonight" stops being offered.
   */
  val state: StateFlow<VisibilityUiState> =
      combine(ownVisibility(), connectivity.observeOffline(TAG), sending, ::Triple)
          .transformLatest { (own, offline, sending) ->
            while (true) {
              val now = clock()
              emit(stateAt(now, own, offline, sending))
              // ponytail: once both moments passed it stops; "tonight" is back on the next screen
              val next =
                  listOfNotNull((own as? Own.Read)?.until, tonight(now))
                      .filter { it > now }
                      .minOrNull() ?: break
              delay(next - now)
            }
          }
          .stateIn(viewModelScope, SharingStarted.Eagerly, VisibilityUiState())

  /**
   * Shows the explorer at their current location until the time [choice] gives. Does nothing
   * offline, while another action is being sent, or for a choice that isn't offered anymore.
   */
  fun show(choice: VisibilityChoice) {
    if (untilFor(choice, clock()) == null) return
    send(VisibilityError.SHOW_FAILED, again = { show(choice) }) {
      val until = untilFor(choice, clock()) ?: return@send null
      val here = locations.currentLocation() ?: return@send VisibilityError.NO_LOCATION
      presence.show(uid, here, until).getOrThrow()
      null
    }
  }

  /** Hides the explorer. Does nothing offline or while another action is being sent. */
  fun hide() {
    send(VisibilityError.HIDE_FAILED, again = ::hide) {
      presence.hide(uid).getOrThrow()
      null
    }
  }

  /** Runs the action that failed again, or reads the explorer's visibility again. */
  fun retry() {
    val again = failed
    if (again != null) again() else loads.update { it + 1 }
  }

  /**
   * Runs [action] unless one is already being sent or the phone is offline. [action] returns the
   * error to show, or null when it worked; a thrown exception shows [failure].
   */
  private fun send(
      failure: VisibilityError,
      again: () -> Unit,
      action: suspend () -> VisibilityError?,
  ) {
    if (sending.value.busy || !online.value) return
    sending.value = Sending(busy = true)
    viewModelScope.launch {
      val error =
          try {
            action()
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (e: Exception) {
            Log.w(TAG, "Changing the explorer's visibility failed", e)
            failure
          }
      failed = again.takeIf { error != null }
      sending.value = Sending(error = error)
    }
  }

  // NOSONAR below: Sonar's S1874 takes Flow.catch for deprecated code, but it isn't deprecated
  private fun ownVisibility(): Flow<Own> = loads.flatMapLatest {
    presence
        .observeOwn(uid)
        .map<ExplorerPresence?, Own> { Own.Read(it?.availableUntil) }
        .onStart { emit(Own.Loading) }
        .catch { // NOSONAR
          Log.w(TAG, "Reading the explorer's visibility failed", it)
          emit(Own.Failed)
        }
  }

  private fun stateAt(now: Long, own: Own, offline: Boolean, sending: Sending) =
      VisibilityUiState(
          loading = own == Own.Loading,
          visibleUntil = (own as? Own.Read)?.until?.takeIf { it > now },
          choices = VisibilityChoice.entries.filter { untilFor(it, now) != null },
          offline = offline,
          busy = sending.busy,
          error = sending.error ?: VisibilityError.LOAD_FAILED.takeIf { own == Own.Failed },
      )

  /** When [choice] ends if picked at [now], or null when it isn't offered then. */
  private fun untilFor(choice: VisibilityChoice, now: Long): Long? =
      when (choice) {
        VisibilityChoice.ONE_HOUR -> now + HOUR_MS
        VisibilityChoice.TWO_HOURS -> now + 2 * HOUR_MS
        VisibilityChoice.TONIGHT -> tonight(now).takeIf { it > now }
      }

  /** [TONIGHT_HOUR]:00 on the day of [now], in [zone]. */
  private fun tonight(now: Long): Long =
      Instant.ofEpochMilli(now)
          .atZone(zone)
          .toLocalDate()
          .atTime(TONIGHT_HOUR, 0)
          .atZone(zone)
          .toInstant()
          .toEpochMilli()

  /** The explorer's own visibility as last read: [Read.until] is null while hidden. */
  private sealed interface Own {
    data object Loading : Own

    data object Failed : Own

    data class Read(val until: Long?) : Own
  }

  /** Whether a show or hide is being sent, and what the last one failed with. */
  private data class Sending(val busy: Boolean = false, val error: VisibilityError? = null)

  companion object {
    /** The hour "until tonight" ends at. */
    const val TONIGHT_HOUR = 22
    private const val HOUR_MS = 3_600_000L
    private const val TAG = "Visibility"

    fun factory(
        uid: String,
        presence: PresenceRepository,
        locations: LocationRepository,
        connectivity: ConnectivityRepository,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { VisibilityViewModel(uid, presence, locations, connectivity) }
    }
  }
}
