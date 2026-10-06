// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.model.location.LocationRepository
import com.github.aroundteam2026.aroundapp.model.location.LocationRepositoryProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the map shows.
 *
 * @property areaToFrame The area the camera must move to, or null once the map framed it.
 * @property showsUserLocation Whether to draw the explorer's position, which needs the permission.
 */
data class MapUiState(val areaToFrame: GeoBounds?, val showsUserLocation: Boolean = false)

/**
 * Holds the map's state. The map starts on [DEFAULT_CENTER], then frames [NEARBY_RADIUS_METERS]
 * around the explorer once their position is known.
 */
class MapViewModel(private val locationRepository: LocationRepository) : ViewModel() {
  private val _uiState =
      MutableStateFlow(MapUiState(areaToFrame = DEFAULT_CENTER.boundsWithin(NEARBY_RADIUS_METERS)))
  /** What the map shows now; the screen observes it. */
  val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

  private var lookup: Job? = null
  private var located = false

  /**
   * Called with the answer to the location permission request, or with `true` when it was already
   * granted. The explorer is located once per visit to the map: switching back to the Map tab keeps
   * the camera where they left it, unless no position was found yet.
   */
  fun onLocationPermissionResult(granted: Boolean) {
    _uiState.update { it.copy(showsUserLocation = granted) }
    if (!granted || located || lookup?.isActive == true) return
    lookup = viewModelScope.launch {
      val here = locationRepository.currentLocation() ?: return@launch
      located = true
      _uiState.update { it.copy(areaToFrame = here.boundsWithin(NEARBY_RADIUS_METERS)) }
    }
  }

  /**
   * Called once the camera shows [area]. A newer area that arrived while the camera moved is kept,
   * so it still gets framed.
   */
  fun onAreaFramed(area: GeoBounds) {
    _uiState.update { if (it.areaToFrame == area) it.copy(areaToFrame = null) else it }
  }

  companion object {
    /** Where the map starts, and stays when the explorer's position is unknown: Lausanne. */
    val DEFAULT_CENTER = Location(46.5197, 6.6323)

    /** The map frames this distance around the explorer. */
    const val NEARBY_RADIUS_METERS = 5_000.0

    /** Builds the [MapViewModel] with the app's [LocationRepository]. */
    val factory: ViewModelProvider.Factory = viewModelFactory {
      initializer { MapViewModel(LocationRepositoryProvider.repository(this[APPLICATION_KEY]!!)) }
    }
  }
}
