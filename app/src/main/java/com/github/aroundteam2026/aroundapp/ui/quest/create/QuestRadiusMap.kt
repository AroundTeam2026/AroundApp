// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.FrameArea
import com.github.aroundteam2026.aroundapp.ui.map.mapStyle
import com.github.aroundteam2026.aroundapp.ui.map.toLatLng
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
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
 * A still map of the unlock radius: the venue's pin at [center], the circle of [radiusMeters]
 * around it, and about how many minutes, [walkMinutes], it takes to walk across it.
 */
@Composable
internal fun QuestRadiusMap(center: Location, radiusMeters: Int, walkMinutes: Int) {
  val description = stringResource(R.string.create_quest_radius_map, radiusMeters)
  BoxWithConstraints(
      Modifier.fillMaxWidth()
          .height(MAP_HEIGHT)
          .clip(MaterialTheme.shapes.medium)
          .border(BORDER, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
          .semantics { contentDescription = description }
          .testTag(C.Tag.CREATE_QUEST_RADIUS_MAP)
  ) {
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    RadiusMapContent(center, radiusMeters, width, height)
    WalkChip(walkMinutes, Modifier.align(Alignment.TopStart).padding(AroundSpacing.CompactGap))
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

/** The pill in the map's corner: about how long it takes to walk across the radius. */
@Composable
private fun WalkChip(minutes: Int, modifier: Modifier = Modifier) {
  Surface(
      shape = AroundPillShape,
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = CHIP_ELEVATION,
      modifier = modifier,
  ) {
    Row(
        Modifier.padding(
            horizontal = AroundSpacing.CompactGap,
            vertical = AroundSpacing.SegmentGap,
        ),
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.SegmentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          painterResource(R.drawable.ic_clock),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(CLOCK_SIZE),
      )
      Text(
          stringResource(R.string.create_quest_walk_time, minutes),
          style = AroundTextStyles.Badge,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.testTag(C.Tag.CREATE_QUEST_WALK_TIME),
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
private val BORDER: Dp = 1.dp
private val CIRCLE_STROKE: Dp = 2.dp
private val DASH: Dp = 4.dp
private val CHIP_ELEVATION: Dp = 2.dp
private val CLOCK_SIZE: Dp = 14.dp

private const val CIRCLE_FILL_ALPHA = 0.25f

/** How much wider than the circle the map shows: the design's 140 dp map around a 92 dp circle. */
private const val FRAME_MARGIN = 1.5
