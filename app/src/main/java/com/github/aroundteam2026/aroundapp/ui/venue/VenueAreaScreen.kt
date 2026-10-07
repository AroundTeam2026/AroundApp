// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits
import com.github.aroundteam2026.aroundapp.ui.map.FrameArea
import com.github.aroundteam2026.aroundapp.ui.map.RequestLocationPermission
import com.github.aroundteam2026.aroundapp.ui.map.rememberMapCamera
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

/** Test tags of [VenueAreaScreen]. */
internal object VenueAreaTags {
  const val SCREEN = "venue_area_screen"
  const val MAP = "venue_area_map"
  const val RADIUS = "venue_area_radius"
  const val SLIDER = "venue_area_slider"
  const val USE_MY_LOCATION = "venue_area_use_my_location"
}

/** Text of [VenueAreaScreen]. */
internal object VenueAreaText {
  const val TITLE = "Set your venue's location"
  const val HINT = "Tap the map or drag the pin to your entrance."
  const val RADIUS_LABEL = "Visit radius"
  const val USE_MY_LOCATION = "Use my location"

  /** [meters] as shown next to the slider. */
  fun meters(meters: Int) = "$meters m"
}

/**
 * Where a venue places its marker, by tapping the map or dragging the marker, and sets the radius
 * in which a visit counts. It asks for the location permission like the Map tab; with it, the map
 * shows the device's position and frames it.
 *
 * @param cameraPositionState Where the camera is; tests pass their own to read it.
 */
@Composable
fun VenueAreaScreen(
    viewModel: VenueAreaViewModel = viewModel(factory = VenueAreaViewModel.factory),
    cameraPositionState: CameraPositionState = rememberMapCamera(),
) {
  val state by viewModel.uiState.collectAsState()
  RequestLocationPermission(viewModel::onLocationPermissionResult)

  Column(Modifier.fillMaxSize().testTag(VenueAreaTags.SCREEN)) {
    Box(Modifier.weight(1f).fillMaxWidth()) {
      AreaMap(state, viewModel, cameraPositionState)
      if (state.showsUserLocation) {
        UseMyLocationButton(
            onClick = viewModel::onUseMyLocation,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
      }
    }
    RadiusPanel(state.radiusMeters, viewModel::onRadiusChanged)
  }
}

/** The map with the marker and the circle around it; tapping the map places the marker. */
@Composable
private fun AreaMap(
    state: VenueAreaUiState,
    viewModel: VenueAreaViewModel,
    cameraPositionState: CameraPositionState,
) {
  // Outside the map's content, which only runs once the map exists
  val markerState = state.marker?.let { rememberDraggableMarker(it, viewModel::onMarkerPlaced) }
  val color = MaterialTheme.colorScheme.primary
  // Until the map first draws, the Maps SDK places taps on its starting view, far from the camera
  var loaded by remember { mutableStateOf(false) }
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    GoogleMap(
        modifier = Modifier.fillMaxSize().testTag(VenueAreaTags.MAP),
        cameraPositionState = cameraPositionState,
        // Needs the permission, or the Maps SDK throws a SecurityException
        properties = MapProperties(isMyLocationEnabled = state.showsUserLocation),
        // The "Use my location" button replaces the Maps SDK's own, and its zoom buttons would sit
        // under it; pinching still zooms
        uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false),
        onMapLoaded = { loaded = true },
        onMapClick = { if (loaded) viewModel.onMarkerPlaced(it.toLocation()) },
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
      FrameArea(state.areaToFrame, cameraPositionState, width, height, viewModel::onAreaFramed)
    }
  }
}

/** Frames the device's position, without moving the marker. */
@Composable
private fun UseMyLocationButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  Surface(
      onClick = onClick,
      modifier = modifier.testTag(VenueAreaTags.USE_MY_LOCATION),
      shape = CircleShape,
      shadowElevation = 4.dp,
  ) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      // The label already says what the button does, so the icon is decorative
      Icon(painterResource(R.drawable.ic_my_location), contentDescription = null)
      Spacer(Modifier.width(8.dp))
      Text(VenueAreaText.USE_MY_LOCATION, style = MaterialTheme.typography.labelLarge)
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
