// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.model.location.LocationRepository
import com.github.aroundteam2026.aroundapp.model.presence.PresenceRepository
import com.github.aroundteam2026.aroundapp.model.user.UserRepository
import com.github.aroundteam2026.aroundapp.model.user.observeProfiles
import com.github.aroundteam2026.aroundapp.ui.map.MapViewModel.Companion.NEARBY_RADIUS_METERS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the search for explorers around the user stands. */
enum class ExplorersStatus {
  /** The user's position is being looked up, or the location permission isn't answered yet. */
  LOCATING,
  /** The user's position is unknown: the permission was refused, or no fix came. */
  NO_LOCATION,
  /** The explorers around the user are loading. */
  LOADING,
  /** [NearbyExplorersUiState.explorers] holds the explorers around the user, maybe none. */
  LOADED,
  /** The explorers failed to load, until [NearbyExplorersViewModel.onRetry] loads them again. */
  FAILED,
}

/**
 * What the map's Explorers mode shows, as its pins (E50.5 #731) and its list (E50.6 #732).
 *
 * @property status Where the search stands. Only [ExplorersStatus.LOADED] has [explorers].
 * @property explorers The explorers available within [NEARBY_RADIUS_METERS] of the user, nearest
 *   first, without the user themself.
 */
data class NearbyExplorersUiState(
    val status: ExplorersStatus = ExplorersStatus.LOCATING,
    val explorers: List<NearbyExplorer> = emptyList(),
)

/**
 * Holds the explorers the user [explorerUid] can meet around them.
 *
 * Once the user is located, it lists the explorers visible within [NEARBY_RADIUS_METERS], with the
 * names and pictures of their profiles, and keeps up as they show or hide themselves. An explorer
 * leaves the list when their time runs out, by [clock], without waiting for the repository: its
 * documents are only deleted later.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NearbyExplorersViewModel(
    private val explorerUid: String,
    private val locationRepository: LocationRepository,
    private val presenceRepository: PresenceRepository,
    private val userRepository: UserRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
  private val position = MutableStateFlow<Position>(Position.Locating)
  /** Each new value listens to the explorers again (see [onRetry]). */
  private val attempts = MutableStateFlow(0)
  private var lookup: Job? = null

  /** What the Explorers mode shows now; the map observes it. */
  val uiState: StateFlow<NearbyExplorersUiState> =
      combine(position, attempts) { at, _ -> at }
          .flatMapLatest { at ->
            when (at) {
              Position.Locating -> flowOf(NearbyExplorersUiState(ExplorersStatus.LOCATING))
              Position.Unknown -> flowOf(NearbyExplorersUiState(ExplorersStatus.NO_LOCATION))
              is Position.Known -> explorersAround(at.location)
            }
          }
          .stateIn(viewModelScope, SharingStarted.Eagerly, NearbyExplorersUiState())

  /**
   * Called with the answer to the location permission request, or with `true` when it was already
   * granted. The user is located once: a later `true` keeps the position found.
   */
  fun onLocationPermissionResult(granted: Boolean) {
    if (!granted) {
      lookup?.cancel()
      position.value = Position.Unknown
    } else if (position.value !is Position.Known && lookup?.isActive != true) {
      locate()
    }
  }

  /**
   * Looks for the user's position again if it is unknown, or else listens to the explorers again.
   */
  fun onRetry() {
    if (position.value == Position.Unknown) locate() else attempts.update { it + 1 }
  }

  private fun locate() {
    lookup = viewModelScope.launch {
      position.value = Position.Locating
      position.value =
          locationRepository.currentLocation()?.let { Position.Known(it) } ?: Position.Unknown
    }
  }

  // NOSONAR below: Sonar's S1874 takes Flow.catch for deprecated code, but it isn't deprecated
  private fun explorersAround(from: Location): Flow<NearbyExplorersUiState> =
      presenceRepository
          .observeNearby(from.boundsWithin(NEARBY_RADIUS_METERS), clock())
          .map { presences -> presences.filter { it.uid != explorerUid } }
          .flatMapLatest { others ->
            userRepository.observeProfiles(others.map { it.uid }).map { others to it }
          }
          .transformLatest { (others, profiles) ->
            // Lists them again each time someone's time runs out, so they leave the list on time
            while (true) {
              val now = clock()
              val explorers = nearbyExplorers(others, profiles, explorerUid, from, now)
              emit(NearbyExplorersUiState(ExplorersStatus.LOADED, explorers))
              val next = others.map { it.availableUntil }.filter { it > now }.minOrNull() ?: break
              delay(next - now)
            }
          }
          .onStart { emit(NearbyExplorersUiState(ExplorersStatus.LOADING)) }
          .catch { // NOSONAR
            Log.w(TAG, "Listening to the explorers around the user failed", it)
            emit(NearbyExplorersUiState(ExplorersStatus.FAILED))
          }

  /** The user's position, as far as the view model knows it. */
  private sealed interface Position {
    data object Locating : Position

    data object Unknown : Position

    data class Known(val location: Location) : Position
  }

  private companion object {
    const val TAG = "NearbyExplorersVM"
  }
}
