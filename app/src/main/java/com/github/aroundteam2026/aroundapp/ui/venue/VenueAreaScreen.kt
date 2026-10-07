// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits
import com.github.aroundteam2026.aroundapp.ui.map.MapViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

/** Test tags of [VenueAreaScreen]. */
internal object VenueAreaTags {
  const val SCREEN = "venue_area_screen"
  const val RADIUS = "venue_area_radius"
  const val SLIDER = "venue_area_slider"
}

/** Text of [VenueAreaScreen]. */
internal object VenueAreaText {
  const val TITLE = "Set your venue's location"
  const val HINT = "Tap the map or drag the pin to your entrance."
  const val RADIUS_LABEL = "Visit radius"

  /** [meters] as shown next to the slider. */
  fun meters(meters: Int) = "$meters m"
}

/** Map zoom at which a venue can tell its entrance from its neighbours'. */
private const val STREET_ZOOM = 16f

/**
 * Where a venue places its marker, by tapping the map or dragging the marker, and sets the radius
 * in which a visit counts. The map starts on [MapViewModel.DEFAULT_CENTER].
 */
@Composable
fun VenueAreaScreen(viewModel: VenueAreaViewModel = viewModel()) {
  val state by viewModel.uiState.collectAsState()

  Column(Modifier.fillMaxSize().testTag(VenueAreaTags.SCREEN)) {
    AreaMap(state, viewModel::onMarkerPlaced, Modifier.weight(1f))
    RadiusPanel(state.radiusMeters, viewModel::onRadiusChanged)
  }
}

/** The map with the marker and the circle around it; tapping the map places the marker. */
@Composable
private fun AreaMap(
    state: VenueAreaUiState,
    onMarkerPlaced: (Location) -> Unit,
    modifier: Modifier,
) {
  val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(MapViewModel.DEFAULT_CENTER.toLatLng(), STREET_ZOOM)
  }
  // Outside the map's content, which only runs once the map exists
  val markerState = state.marker?.let { rememberDraggableMarker(it, onMarkerPlaced) }
  val color = MaterialTheme.colorScheme.primary
  GoogleMap(
      modifier = modifier.fillMaxWidth(),
      cameraPositionState = cameraPositionState,
      onMapClick = { onMarkerPlaced(it.toLocation()) },
  ) {
    if (markerState != null) {
      // Centered on the marker's state, so the circle follows the marker while it is dragged
      Circle(
          center = markerState.position,
          radius = state.radiusMeters.toDouble(),
          fillColor = color.copy(alpha = 0.3f),
          strokeColor = color,
          strokeWidth = 2f,
      )
      Marker(state = markerState, draggable = true)
    }
  }
}

/**
 * The state of a draggable marker at [location]. It moves when [location] changes, and tells
 * [onDropped] where each drag ends.
 */
@Composable
internal fun rememberDraggableMarker(
    location: Location,
    onDropped: (Location) -> Unit,
): MarkerState {
  val markerState = remember { MarkerState(location.toLatLng()) }
  // The effect below outlives a composition; this keeps it calling the latest callback
  val currentOnDropped by rememberUpdatedState(onDropped)
  LaunchedEffect(location) { markerState.position = location.toLatLng() }
  LaunchedEffect(markerState) {
    reportDrops({ markerState.isDragging }, { markerState.position }) { currentOnDropped(it) }
  }
  return markerState
}

/**
 * Calls [onDropped] with [position] each time [isDragging] turns false, that is when a drag ends.
 * Both are read as Compose state, as [MarkerState] exposes them. Runs until cancelled.
 */
internal suspend fun reportDrops(
    isDragging: () -> Boolean,
    position: () -> LatLng,
    onDropped: (Location) -> Unit,
) {
  snapshotFlow(isDragging)
      // The first value is the state when collecting starts, not the end of a drag
      .drop(1)
      .filter { dragging -> !dragging }
      .collect { onDropped(position().toLocation()) }
}

/** "Visit radius" with its value, above a slider bounded by [VenueLimits] and its limits. */
@Composable
private fun RadiusPanel(radiusMeters: Int, onRadiusChanged: (Int) -> Unit) {
  Surface(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), shadowElevation = 8.dp) {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
      Text(VenueAreaText.TITLE, style = MaterialTheme.typography.headlineSmall)
      Text(
          VenueAreaText.HINT,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(24.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(VenueAreaText.RADIUS_LABEL, style = MaterialTheme.typography.titleSmall)
        Text(
            VenueAreaText.meters(radiusMeters),
            modifier = Modifier.testTag(VenueAreaTags.RADIUS),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
        )
      }
      Slider(
          value = radiusMeters.toFloat(),
          onValueChange = { onRadiusChanged(it.roundToInt()) },
          valueRange =
              VenueLimits.MIN_RADIUS_METERS.toFloat()..VenueLimits.MAX_RADIUS_METERS.toFloat(),
          modifier = Modifier.testTag(VenueAreaTags.SLIDER),
      )
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(VenueLimits.MIN_RADIUS_METERS, VenueLimits.MAX_RADIUS_METERS).forEach {
          Text(
              VenueAreaText.meters(it),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

private fun Location.toLatLng() = LatLng(lat, lng)

private fun LatLng.toLocation() = Location(latitude, longitude)
