// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import androidx.lifecycle.ViewModel
import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits
import com.github.aroundteam2026.aroundapp.ui.map.DEFAULT_MAP_CENTER
import com.github.aroundteam2026.aroundapp.ui.map.afterFraming
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the venue area screen shows.
 *
 * @property marker Where the venue placed its marker, or null until it places one.
 * @property radiusMeters Radius of the circle around [marker], always within [VenueLimits].
 * @property areaToFrame The area the camera must move to, or null once the map framed it.
 */
data class VenueAreaUiState(
    val marker: Location? = null,
    val radiusMeters: Int = VenueLimits.DEFAULT_RADIUS_METERS,
    val areaToFrame: GeoBounds? =
        DEFAULT_MAP_CENTER.boundsWithin(VenueAreaViewModel.FRAMED_RADIUS_METERS),
)

/**
 * Holds the marker a venue places on the map and the radius in which a visit counts. The map starts
 * on [DEFAULT_MAP_CENTER].
 */
class VenueAreaViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(VenueAreaUiState())
  /** What the screen shows now; the screen observes it. */
  val uiState: StateFlow<VenueAreaUiState> = _uiState.asStateFlow()

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

  /** Called once the camera shows [area]; any other area still to frame is kept. */
  fun onAreaFramed(area: GeoBounds) {
    _uiState.update { it.copy(areaToFrame = it.areaToFrame.afterFraming(area)) }
  }

  companion object {
    /**
     * The map frames this distance around a point: the largest visit radius, close enough to tell
     * an entrance from its neighbours.
     */
    const val FRAMED_RADIUS_METERS = VenueLimits.MAX_RADIUS_METERS.toDouble()
  }
}
