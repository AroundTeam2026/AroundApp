// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.ui.map.FrameArea
import com.github.aroundteam2026.aroundapp.ui.map.mapStyle
import com.github.aroundteam2026.aroundapp.ui.map.toLatLng
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme
import com.github.aroundteam2026.aroundapp.ui.venue.rememberDraggableMarker
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

/**
 * The still map alone, read out as [description]: the pin of the venue or its quest at [center] and
 * the circle of [radiusMeters] around it, with [overlay] on top. A quest away from its venue also
 * shows the venue's own pin, small and round, at [venue].
 *
 * With [onMoved], tapping the map or dragging the pin calls it with where the pin goes, and the map
 * frames the pin again once [center] follows; it still can't be panned or zoomed.
 */
@Composable
internal fun StillRadiusMap(
    center: Location,
    radiusMeters: Int,
    description: String,
    modifier: Modifier = Modifier,
    onMoved: ((Location) -> Unit)? = null,
    venue: Location? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
  BoxWithConstraints(
      modifier.fillMaxWidth().height(MAP_HEIGHT).clip(MaterialTheme.shapes.medium).semantics {
        contentDescription = description
      }
  ) {
    RadiusMapContent(
        center,
        radiusMeters,
        constraints.maxWidth,
        constraints.maxHeight,
        onMoved,
        venue,
    )
    overlay()
  }
}

/**
 * The map itself, framed on the circle; it can't be panned, as it illustrates the radius. The
 * circle follows the pin while it is dragged.
 */
@Composable
private fun RadiusMapContent(
    center: Location,
    radiusMeters: Int,
    width: Int,
    height: Int,
    onMoved: ((Location) -> Unit)?,
    venue: Location?,
) {
  val resources = LocalResources.current
  val darkTheme = isSystemInDarkTheme()
  val style = remember(resources, darkTheme) { MapStyleOptions(mapStyle(resources, darkTheme)) }
  val dash = with(LocalDensity.current) { DASH.toPx() }
  val stroke = with(LocalDensity.current) { CIRCLE_STROKE.toPx() }
  val pin =
      if (onMoved != null) rememberDraggableMarker(center, onMoved)
      else rememberUpdatedMarkerState(center.toLatLng())
  val camera = rememberCameraPositionState()
  GoogleMap(
      modifier = Modifier.fillMaxSize(),
      cameraPositionState = camera,
      properties = MapProperties(mapStyleOptions = style),
      uiSettings = STILL_MAP,
      mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
      onMapClick =
          onMoved?.let { move -> { tap: LatLng -> move(Location(tap.latitude, tap.longitude)) } },
  ) {
    Circle(
        center = pin.position,
        radius = radiusMeters.toDouble(),
        fillColor = AroundTheme.colors.sage.copy(alpha = CIRCLE_FILL_ALPHA),
        strokeColor = MaterialTheme.colorScheme.primary,
        strokeWidth = stroke,
        strokePattern = listOf(Dash(dash), Gap(dash)),
    )
    venue?.let { VenueMarker(it) }
    MarkerComposable(state = pin, draggable = onMoved != null) {
      Image(painterResource(R.drawable.ic_venue_pin), contentDescription = null)
    }
    // Some margin around the circle, as in the design
    FrameArea(
        center.boundsWithin(radiusMeters * FRAME_MARGIN),
        camera,
        width,
        height,
        onFramed = {},
    )
  }
}

/** The venue's own pin, small and round, while the quest is somewhere else. */
@Composable
@GoogleMapComposable
private fun VenueMarker(location: Location) {
  MarkerComposable(
      state = rememberUpdatedMarkerState(location.toLatLng()),
      anchor = CENTER_ANCHOR,
  ) {
    Box(
        Modifier.size(VENUE_MARKER_SIZE)
            .background(MaterialTheme.colorScheme.tertiary, CircleShape)
            .border(VENUE_MARKER_RING, MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          painterResource(R.drawable.ic_storefront),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onTertiary,
          modifier = Modifier.size(VENUE_ICON_SIZE),
      )
    }
  }
}

private val STILL_MAP =
    MapUiSettings(
        compassEnabled = false,
        mapToolbarEnabled = false,
        myLocationButtonEnabled = false,
        rotationGesturesEnabled = false,
        scrollGesturesEnabled = false,
        tiltGesturesEnabled = false,
        zoomControlsEnabled = false,
        zoomGesturesEnabled = false,
    )

private val MAP_HEIGHT: Dp = 140.dp
private val CIRCLE_STROKE: Dp = 2.dp
private val DASH: Dp = 4.dp

private val VENUE_MARKER_SIZE: Dp = 26.dp
private val VENUE_MARKER_RING: Dp = 2.dp
private val VENUE_ICON_SIZE: Dp = 14.dp

/** Pins the venue's marker by its centre, as it is round. */
private val CENTER_ANCHOR = Offset(0.5f, 0.5f)

private const val CIRCLE_FILL_ALPHA = 0.25f

/** How much wider than the circle the map shows: the design's 140 dp map around a 92 dp circle. */
private const val FRAME_MARGIN = 1.5
