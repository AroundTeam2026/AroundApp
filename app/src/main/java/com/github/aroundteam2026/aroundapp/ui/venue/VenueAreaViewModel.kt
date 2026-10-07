// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

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
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits
import com.github.aroundteam2026.aroundapp.ui.map.MapViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the venue area screen shows.
 *
 * @property marker Where the venue placed its marker, or null until it places one.
 * @property radiusMeters Radius of the circle around [marker], always within [VenueLimits].
 * @property areaToFrame The area the camera must move to, or null once the map framed it.
 * @property showsUserLocation Whether to draw the device's position, which needs the permission.
 */
data class VenueAreaUiState(
    val marker: Location? = null,
    val radiusMeters: Int = VenueLimits.DEFAULT_RADIUS_METERS,
    val areaToFrame: GeoBounds? =
        MapViewModel.DEFAULT_CENTER.boundsWithin(VenueAreaViewModel.FRAMED_RADIUS_METERS),
    val showsUserLocation: Boolean = false,
)

/**
 * Holds the marker a venue places on the map and the radius in which a visit counts. The map starts
 * on [MapViewModel.DEFAULT_CENTER], then frames the device once its position is known, unless a
 * marker is already placed.
 */
class VenueAreaViewModel(private val locationRepository: LocationRepository) : ViewModel() {
  private val _uiState = MutableStateFlow(VenueAreaUiState())
  /** What the screen shows now; the screen observes it. */
  val uiState: StateFlow<VenueAreaUiState> = _uiState.asStateFlow()

  private var openingLookup: Job? = null
  private var myLocationLookup: Job? = null
  private var located = false

  /** Called when the venue taps the map, or drops the dragged marker, at [location]. */
  fun onMarkerPlaced(location: Location) {
    _uiState.update { it.copy(marker = location) }
  }

  /**
   * Called when the venue moves the slider. [radiusMeters] is clamped to
   * [VenueLimits.MIN_RADIUS_METERS]..[VenueLimits.MAX_RADIUS_METERS], never rejected.
   */
  fun onRadiusChanged(radiusMeters: Int) {
    _uiState.update {
      it.copy(
          radiusMeters =
              radiusMeters.coerceIn(VenueLimits.MIN_RADIUS_METERS, VenueLimits.MAX_RADIUS_METERS)
      )
    }
  }

  /**
   * Called with the answer to the location permission request, or with `true` when it was already
   * granted. The device is located once per visit, and framed only while no marker is placed, so
   * the venue's marker stays in view.
   */
  fun onLocationPermissionResult(granted: Boolean) {
    _uiState.update { it.copy(showsUserLocation = granted) }
    if (!granted || located || openingLookup?.isActive == true) return
    openingLookup = frameDevice(overMarker = false)
  }

  /**
   * Frames the device's position, even when a marker is placed. The marker does not move: the
   * position is only accurate to about a city block. Does nothing while a previous call is still
   * locating, or when the position is unknown.
   */
  fun onUseMyLocation() {
    if (myLocationLookup?.isActive == true) return
    myLocationLookup = frameDevice(overMarker = true)
  }

  /**
   * Called once the camera shows [area]. A newer area that arrived while the camera moved is kept,
   * so it still gets framed.
   */
  fun onAreaFramed(area: GeoBounds) {
    _uiState.update { if (it.areaToFrame == area) it.copy(areaToFrame = null) else it }
  }

  private fun frameDevice(overMarker: Boolean): Job = viewModelScope.launch {
    val here = locationRepository.currentLocation() ?: return@launch
    located = true
    val area = here.boundsWithin(FRAMED_RADIUS_METERS)
    _uiState.update { if (overMarker || it.marker == null) it.copy(areaToFrame = area) else it }
  }

  companion object {
    /**
     * The map frames this distance around a point: the largest visit radius, close enough to tell
     * an entrance from its neighbours.
     */
    const val FRAMED_RADIUS_METERS = VenueLimits.MAX_RADIUS_METERS.toDouble()

    /** Builds the [VenueAreaViewModel] with the app's [LocationRepository]. */
    val factory: ViewModelProvider.Factory = viewModelFactory {
      initializer {
        VenueAreaViewModel(LocationRepositoryProvider.repository(this[APPLICATION_KEY]!!))
      }
    }
  }
}
