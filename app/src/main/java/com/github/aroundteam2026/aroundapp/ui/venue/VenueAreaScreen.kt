// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.FrameArea
import com.github.aroundteam2026.aroundapp.ui.map.ReportVisibleArea
import com.github.aroundteam2026.aroundapp.ui.map.RequestLocationPermission
import com.github.aroundteam2026.aroundapp.ui.map.rememberMapCamera
import com.github.aroundteam2026.aroundapp.ui.map.toLatLng
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
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

/**
 * Where a venue places its marker, by tapping the map, dragging the marker or searching its
 * address, and sets the radius in which a visit counts. It asks for the location permission once
 * per visit; with it, the map shows the device's position and frames it.
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
  val focusManager = LocalFocusManager.current

  Box(Modifier.fillMaxSize().testTag(C.Tag.VENUE_AREA_SCREEN)) {
    Column(Modifier.fillMaxSize()) {
      Box(Modifier.weight(1f).fillMaxWidth()) {
        AreaMap(
            state = state,
            cameraPositionState = cameraPositionState,
            onMarkerPlaced = viewModel::onMarkerPlaced,
            onAreaFramed = viewModel::onAreaFramed,
            onVisibleAreaChanged = viewModel::onVisibleAreaChanged,
            // Turning to the map closes the keyboard and the suggestions
            onMapTapped = {
              focusManager.clearFocus()
              viewModel.onAddressSearchDismissed()
            },
        )
        if (state.showsUserLocation) {
          UseMyLocationButton(
              onClick = viewModel::onUseMyLocation,
              modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
          )
        }
      }
      RadiusPanel(state.radiusMeters, viewModel::onRadiusChanged)
    }
    // Over the whole screen and above the keyboard, so the suggestions scroll rather than hide
    // behind it. Taps beside the bar reach the map.
    Box(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding()
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp)
    ) {
      AddressSearchBar(
          query = viewModel.addressQuery,
          state = state.addressSearch,
          onQueryChange = viewModel::onAddressQueryChanged,
          onSearch = viewModel::onAddressSearch,
          onClear = viewModel::onAddressCleared,
          onPick = viewModel::onAddressPicked,
      )
    }
  }
}

/**
 * The map with the marker and the circle around it; tapping the map calls [onMapTapped], then
 * places the marker. It reports what it shows to [onVisibleAreaChanged] each time it stops moving.
 */
@Composable
private fun AreaMap(
    state: VenueAreaUiState,
    cameraPositionState: CameraPositionState,
    onMarkerPlaced: (Location) -> Unit,
    onAreaFramed: (GeoBounds) -> Unit,
    onVisibleAreaChanged: (GeoBounds) -> Unit,
    onMapTapped: () -> Unit,
) {
  // Outside the map's content, which only runs once the map exists
  val markerState = state.marker?.let { rememberDraggableMarker(it, onMarkerPlaced) }
  val color = MaterialTheme.colorScheme.primary
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    GoogleMap(
        modifier = Modifier.fillMaxSize().testTag(C.Tag.VENUE_AREA_MAP),
        cameraPositionState = cameraPositionState,
        // Needs the permission, or the Maps SDK throws a SecurityException
        properties = MapProperties(isMyLocationEnabled = state.showsUserLocation),
        // The "Use my location" button replaces the Maps SDK's own, and its zoom buttons and
        // toolbar
        // would sit under it; pinching still zooms
        uiSettings =
            MapUiSettings(
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
            ),
        onMapClick = { tap ->
          onMapTapped()
          val visible = cameraPositionState.projection?.visibleRegion?.latLngBounds
          if (isOnVisibleMap(tap, visible)) onMarkerPlaced(tap.toLocation())
        },
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
      FrameArea(state.areaToFrame, cameraPositionState, width, height, onAreaFramed)
      // The address search prefers what the map shows
      ReportVisibleArea(cameraPositionState, IntSize(width, height), onVisibleAreaChanged)
    }
  }
}

/**
 * Whether a tap at [tap] counts: it must lie on the part of the map the camera shows, [visible], or
 * null before the map exists. Until the map first draws, the Maps SDK reports taps against its
 * starting view, far from the camera; those don't count.
 */
internal fun isOnVisibleMap(tap: LatLng, visible: LatLngBounds?): Boolean =
    visible?.contains(tap) == true

/** Frames the device's position, without moving the marker. */
@Composable
private fun UseMyLocationButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  ExtendedFloatingActionButton(
      onClick = onClick,
      modifier = modifier.testTag(C.Tag.VENUE_AREA_USE_MY_LOCATION),
      // The label already says what the button does, so the icon is decorative
      icon = { Icon(painterResource(R.drawable.ic_my_location), contentDescription = null) },
      text = { Text(stringResource(R.string.use_my_location)) },
  )
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

/** The title, then the visit radius with its value, above a slider bounded by [VenueLimits]. */
@Composable
private fun RadiusPanel(radiusMeters: Int, onRadiusChanged: (Int) -> Unit) {
  Surface(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), shadowElevation = 8.dp) {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
      Text(
          stringResource(R.string.venue_area_title),
          style = MaterialTheme.typography.headlineSmall,
      )
      Text(
          stringResource(R.string.venue_area_hint),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(24.dp))
      val label = stringResource(R.string.venue_area_radius)
      val value = stringResource(R.string.venue_area_meters, radiusMeters)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(
            value,
            modifier = Modifier.testTag(C.Tag.VENUE_AREA_RADIUS),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
        )
      }
      Slider(
          value = radiusMeters.toFloat(),
          onValueChange = { onRadiusChanged(it.roundToInt()) },
          valueRange =
              VenueLimits.MIN_RADIUS_METERS.toFloat()..VenueLimits.MAX_RADIUS_METERS.toFloat(),
          // TalkBack would otherwise read an unnamed slider, in percent
          modifier =
              Modifier.testTag(C.Tag.VENUE_AREA_SLIDER).semantics {
                contentDescription = label
                stateDescription = value
              },
      )
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(VenueLimits.MIN_RADIUS_METERS, VenueLimits.MAX_RADIUS_METERS).forEach {
          Text(
              stringResource(R.string.venue_area_meters, it),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

private fun LatLng.toLocation() = Location(latitude, longitude)
