// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

/**
 * The still map alone, read out as [description]: the venue's pin at [center] and the circle of
 * [radiusMeters] around it, with [overlay] on top.
 */
@Composable
internal fun StillRadiusMap(
    center: Location,
    radiusMeters: Int,
    description: String,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
  BoxWithConstraints(
      modifier.fillMaxWidth().height(MAP_HEIGHT).clip(MaterialTheme.shapes.medium).semantics {
        contentDescription = description
      }
  ) {
    RadiusMapContent(center, radiusMeters, constraints.maxWidth, constraints.maxHeight)
    overlay()
  }
}

/** The map itself, framed on the circle; it can't be moved, as it only illustrates the radius. */
@Composable
private fun RadiusMapContent(center: Location, radiusMeters: Int, width: Int, height: Int) {
  val resources = LocalResources.current
  val darkTheme = isSystemInDarkTheme()
  val style = remember(resources, darkTheme) { MapStyleOptions(mapStyle(resources, darkTheme)) }
  val dash = with(LocalDensity.current) { DASH.toPx() }
  val stroke = with(LocalDensity.current) { CIRCLE_STROKE.toPx() }
  val position = center.toLatLng()
  val camera = rememberCameraPositionState()
  GoogleMap(
      modifier = Modifier.fillMaxSize(),
      cameraPositionState = camera,
      properties = MapProperties(mapStyleOptions = style),
      uiSettings = STILL_MAP,
      mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
  ) {
    Circle(
        center = position,
        radius = radiusMeters.toDouble(),
        fillColor = AroundTheme.colors.sage.copy(alpha = CIRCLE_FILL_ALPHA),
        strokeColor = MaterialTheme.colorScheme.primary,
        strokeWidth = stroke,
        strokePattern = listOf(Dash(dash), Gap(dash)),
    )
    MarkerComposable(state = rememberUpdatedMarkerState(position)) {
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

private const val CIRCLE_FILL_ALPHA = 0.25f

/** How much wider than the circle the map shows: the design's 140 dp map around a 92 dp circle. */
private const val FRAME_MARGIN = 1.5
