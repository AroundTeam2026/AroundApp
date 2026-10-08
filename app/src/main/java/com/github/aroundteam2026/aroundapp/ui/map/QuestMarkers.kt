// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.ui.map.marker.MarkerStyle
import com.github.aroundteam2026.aroundapp.ui.map.marker.QuestPin
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull

/**
 * Marks each of [pins] on the map with a pin over the venue's area.
 *
 * Each pin is drawn as an image, which is redrawn only when the pin or [style] changes.
 */
@Composable
@GoogleMapComposable
fun QuestMarkers(pins: List<VenuePin>, style: MarkerStyle) {
  pins.forEach { pin ->
    key(pin.venueId) {
      // Real metres on the ground, so the area grows as the explorer zooms in
      Circle(
          center = pin.location.toLatLng(),
          radius = pin.areaRadiusMeters.toDouble(),
          fillColor = style.colors.area,
          strokeWidth = 0f,
      )
      MarkerComposable(
          pin,
          style,
          state = rememberUpdatedMarkerState(pin.location.toLatLng()),
          contentDescription = "${pin.venueName}: ${pin.featuredQuest.title}",
          // Handled: no info window, and the camera stays put
          onClick = { true },
      ) {
        QuestPin(pin.icon, style = style)
      }
    }
  }
}

/**
 * Reports the part of the world on screen to [onVisibleAreaChanged] each time the camera stops
 * moving. Call it from a map's content, which only runs once the map exists.
 */
@Composable
fun ReportVisibleArea(
    cameraPositionState: CameraPositionState,
    onVisibleAreaChanged: (GeoBounds) -> Unit,
) {
  val currentOnVisibleAreaChanged by rememberUpdatedState(onVisibleAreaChanged)
  LaunchedEffect(cameraPositionState) {
    snapshotFlow { cameraPositionState.isMoving to cameraPositionState.position }
        .filter { (moving, _) -> !moving }
        .mapNotNull { cameraPositionState.projection?.visibleRegion?.latLngBounds?.toGeoBounds() }
        .distinctUntilChanged()
        .collect { currentOnVisibleAreaChanged(it) }
  }
}
