// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.marker.MarkerDefaults
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings

/**
 * The Explorer's map, styled for light and dark mode without Google's place icons (see [mapStyle]),
 * framing [MapViewModel.NEARBY_RADIUS_METERS] around the explorer.
 *
 * It asks for the location permission if needed; while the location is off, a banner's "Turn on"
 * asks again or opens the settings, and while the venues fail to load, another's "Try again" loads
 * them again. Each venue on screen with valid quests gets a pin that opens into a card; the card
 * opens the venue's page and tapping the map closes it. A "Nearby quests" pill opens the nearby
 * quests over the map, closed by Back or by opening a card. Tapping a quest frames its venue and
 * opens its card, "View quest" opens its page, and filters narrow the list, not the pins.
 *
 * @param cameraPositionState Where the camera is; tests pass their own to read it.
 * @param onOpenVenue Opens the page of the venue with this id and name, when its card or "View
 *   quest" is tapped.
 */
@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel(factory = MapViewModel.factory),
    cameraPositionState: CameraPositionState = rememberMapCamera(),
    onOpenVenue: (venueId: String, venueName: String) -> Unit,
) {
  val state by viewModel.uiState.collectAsState()
  RequestLocationPermission(viewModel::onLocationPermissionResult)
  val turnOnLocation = rememberTurnOnLocation(viewModel::onLocationPermissionResult)
  // Unlike the context's, these follow configuration changes
  val resources = LocalResources.current
  // The light style would glare in dark mode, which has its own
  val darkTheme = isSystemInDarkTheme()
  val mapStyle = remember(resources, darkTheme) { MapStyleOptions(mapStyle(resources, darkTheme)) }
  val markerStyle = remember { MarkerDefaults.style() }

  BoxWithConstraints(Modifier.fillMaxSize().testTag(C.Tag.MAP_SCREEN)) {
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    GoogleMap(
        modifier = Modifier.fillMaxSize().testTag(C.Tag.MAP),
        cameraPositionState = cameraPositionState,
        // Both need the permission, or the Maps SDK throws a SecurityException
        properties =
            MapProperties(
                isMyLocationEnabled = state.showsUserLocation,
                mapStyleOptions = mapStyle,
            ),
        uiSettings = mapUiSettings(state.showsUserLocation),
        // Whatever the style leaves uncoloured follows the theme too, where the device's Maps
        // renderer has dark colours; it uses the light ones unless told otherwise
        mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
        onMapClick = { viewModel.onMapClick() },
        // A tap on a place of the base map closes the card too, like any tap beside the pins
        onPOIClick = { viewModel.onMapClick() },
    ) {
      FrameArea(state.areaToFrame, cameraPositionState, width, height, viewModel::onAreaFramed)
      ReportVisibleArea(
          cameraPositionState,
          IntSize(width, height),
          viewModel::onVisibleAreaChanged,
      )
      QuestMarkers(
          pins = state.pins,
          selectedPinKey = state.selectedPinKey,
          style = markerStyle,
          onPinClick = viewModel::onPinClick,
          onCardClick = { onOpenVenue(it.venueId, it.venueName) },
      )
    }
    if (state.showsNearby) {
      NearbyQuestOverlay(
          state.nearby,
          locationOn = state.showsUserLocation,
          onQuestClick = viewModel::onNearbyQuestClick,
          // The quests stay open, so Back from the venue's page finds them where they were
          onViewQuest = { onOpenVenue(it.venueId, it.venueName) },
          onClose = viewModel::onNearbyClose,
          filters = state.filters,
          onFiltersChange = viewModel::onFiltersChange,
          modifier = Modifier.align(Alignment.BottomCenter),
      )
    } else {
      NearbyQuestsButton(
          viewModel::onNearbyOpen,
          Modifier.align(Alignment.BottomEnd).padding(AroundSpacing.Large),
      )
    }
    // Both can show at once: the failure first, as the map has no pins until it is fixed
    Column(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(AroundSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
    ) {
      if (state.loadFailed) VenuesErrorBanner(viewModel::onRetry, retrying = state.retrying)
      if (state.locationRefused) LocationBanner(turnOnLocation)
    }
  }
  BackHandler(enabled = state.showsNearby, onBack = viewModel::onNearbyClose)
}

/**
 * The map's own buttons: the my-location one only with [showsUserLocation], which needs the
 * permission, and no zoom buttons, which would sit under the "Nearby quests" pill. Pinching and
 * double-tapping still zoom.
 */
internal fun mapUiSettings(showsUserLocation: Boolean) =
    MapUiSettings(myLocationButtonEnabled = showsUserLocation, zoomControlsEnabled = false)
