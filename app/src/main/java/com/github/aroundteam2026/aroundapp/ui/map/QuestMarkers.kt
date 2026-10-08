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
import androidx.compose.ui.unit.IntSize
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.ui.map.marker.MarkerStyle
import com.github.aroundteam2026.aroundapp.ui.map.marker.QuestCard
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

/** Above the closed pins, so an open card covers its neighbours rather than the other way round. */
private const val OPEN_CARD_Z_INDEX = 1f

/**
 * Marks each of [pins] on the map: a pin, or the card of the [selectedVenueId] venue, over the
 * venue's area. Tapping a pin calls [onPinClick] with its venue; tapping the card calls
 * [onCardClick].
 *
 * Each marker is drawn as an image, which is redrawn only when what it shows changes: a pin's icon
 * or [style], or for a card, its whole pin. So the whole marker is one tap target.
 */
@Composable
@GoogleMapComposable
fun QuestMarkers(
    pins: List<VenuePin>,
    selectedVenueId: String?,
    style: MarkerStyle,
    onPinClick: (venueId: String) -> Unit,
    onCardClick: (VenuePin) -> Unit,
) {
  pins.forEach { pin ->
    key(pin.venueId) {
      val open = pin.venueId == selectedVenueId
      // Real metres on the ground, so the area grows as the explorer zooms in
      val area = pin.area(style)
      Circle(
          center = area.center,
          radius = area.radiusMeters,
          fillColor = area.fill,
          strokeWidth = 0f,
      )
      // Keyed on what the image shows: a pin's icon, or the whole pin for its card
      val keys: Array<Any> = if (open) arrayOf(pin, style) else arrayOf(pin.icon, style)
      MarkerComposable(
          *keys,
          state = rememberUpdatedMarkerState(pin.location.toLatLng()),
          contentDescription = pin.description(),
          zIndex = if (open) OPEN_CARD_Z_INDEX else 0f,
          onClick = {
            if (open) onCardClick(pin) else onPinClick(pin.venueId)
            // Handled: no info window, and the camera stays put
            true
          },
      ) {
        if (open) QuestCard(pin, style = style) else QuestPin(pin.icon, style = style)
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
 * moving, and each time the map changes size, as in split-screen, which shows more or less of the
 * world without moving the camera. Call it from a map's content, which only runs once the map
 * exists.
 *
 * @param mapSize The map's size in pixels.
 */
@Composable
fun ReportVisibleArea(
    cameraPositionState: CameraPositionState,
    mapSize: IntSize,
    onVisibleAreaChanged: (GeoBounds) -> Unit,
) {
  val currentOnVisibleAreaChanged by rememberUpdatedState(onVisibleAreaChanged)
  val currentMapSize by rememberUpdatedState(mapSize)
  LaunchedEffect(cameraPositionState) {
    snapshotFlow {
      Triple(cameraPositionState.isMoving, cameraPositionState.position, currentMapSize)
    }
        .filter { (moving, _, _) -> !moving }
        .mapNotNull { cameraPositionState.projection?.visibleRegion?.latLngBounds?.toGeoBounds() }
        .distinctUntilChanged()
        .collect { currentOnVisibleAreaChanged(it) }
  }
}
