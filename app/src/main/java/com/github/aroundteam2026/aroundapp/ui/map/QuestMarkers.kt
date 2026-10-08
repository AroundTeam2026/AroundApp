// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.ui.map.marker.MarkerStyle
import com.github.aroundteam2026.aroundapp.ui.map.marker.QuestPin
import com.google.android.gms.maps.model.LatLng
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
 * Each pin is drawn as an image, which is redrawn only when its icon or [style] stops being equal.
 */
@Composable
@GoogleMapComposable
fun QuestMarkers(pins: List<VenuePin>, style: MarkerStyle) {
  pins.forEach { pin ->
    key(pin.venueId) {
      // Real metres on the ground, so the area grows as the explorer zooms in
      val area = pin.area(style)
      Circle(
          center = area.center,
          radius = area.radiusMeters,
          fillColor = area.fill,
          strokeWidth = 0f,
      )
      // Keyed on what the image shows: a change to the pin's quests alone keeps it
      MarkerComposable(
          pin.icon,
          style,
          state = rememberUpdatedMarkerState(pin.location.toLatLng()),
          contentDescription = pin.description(),
          // Handled: no info window, and the camera stays put
          onClick = { true },
      ) {
        QuestPin(pin.icon, style = style)
      }
    }
  }
}

/**
 * A pin's area on the map: a circle of [radiusMeters] on the ground around [center], filled with
 * [fill].
 */
internal data class PinArea(val center: LatLng, val radiusMeters: Double, val fill: Color)

/** The area the map draws around this pin, at the venue's real radius. */
internal fun VenuePin.area(style: MarkerStyle): PinArea =
    PinArea(location.toLatLng(), areaRadiusMeters.toDouble(), style.colors.area)

/** What screen readers say for this pin: its venue and featured quest. */
@Composable
internal fun VenuePin.description(): String =
    stringResource(R.string.map_pin_description, venueName, featuredQuest.title)

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
