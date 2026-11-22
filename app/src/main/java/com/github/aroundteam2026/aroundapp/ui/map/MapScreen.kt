// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.marker.MarkerDefaults
import com.github.aroundteam2026.aroundapp.ui.navigation.LocalShowOfflineBanner
import com.github.aroundteam2026.aroundapp.ui.navigation.OfflineBanner
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import kotlinx.coroutines.launch

/**
 * The Explorer's map, styled for light and dark mode without Google's place icons (see [mapStyle]),
 * framing [MapViewModel.NEARBY_RADIUS_METERS] around the explorer.
 *
 * It asks for the location permission if needed; while the location is off, a banner's "Turn on"
 * asks again or opens the settings, and while the venues fail to load, another's "Try again" loads
 * them again. Each venue on screen with valid quests gets a pin that opens into a card; the card
 * opens the venue's page and tapping the map closes it. A "Nearby quests" pill opens the nearby
 * quests over the map, closed by Back or by opening a card. Tapping a quest opens its details, its
 * pin frames its venue and opens its card, and filters narrow the list, not the pins.
 *
 * @param cameraPositionState Where the camera is; tests pass their own to read it.
 * @param onFocusQuest Opens the page of the venue with this id, scrolled to the quest with this id,
 *   which it picks without opening its details, when the quest's card is tapped.
 * @param onOpenQuest Opens the details of the quest with this id on its venue's page, when the
 *   quest is tapped in the nearby quests.
 * @param acceptViewModel Accepts the nearby quests from their "Accept quest"; without it, they
 *   offer none. It also says once, in a snackbar under the banners, that the server refused a
 *   booking the explorer hadn't been told about.
 * @param showOfflineBanner Overlays the shared offline banner at the top of the map; by default,
 *   while the shell says the device is offline.
 */
@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel(factory = MapViewModel.factory),
    cameraPositionState: CameraPositionState = rememberMapCamera(),
    onFocusQuest: (venueId: String, questId: String) -> Unit,
    onOpenQuest: (venueId: String, questId: String) -> Unit,
    acceptViewModel: NearbyAcceptViewModel? = null,
    showOfflineBanner: Boolean = LocalShowOfflineBanner.current,
) {
  val state by viewModel.uiState.collectAsState()
  val acceptState = acceptViewModel?.uiState?.collectAsState()?.value
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val refusedNotice = stringResource(R.string.accept_quest_refused_notice)
  LaunchedEffect(acceptState?.refusalNotice) {
    if (acceptState?.refusalNotice == true) {
      // Launched in the screen's scope, not in this effect: clearing the notice changes the
      // effect's key, which would cancel a snackbar shown from inside it
      scope.launch { snackbarHostState.showSnackbar(refusedNotice) }
      // Cleared at once, so the snackbar doesn't show again when the screen is recreated
      acceptViewModel?.onRefusalNoticeShown()
    }
  }
  RequestLocationPermission(viewModel::onLocationPermissionResult)
  val turnOnLocation = rememberTurnOnLocation(viewModel::onLocationPermissionResult)
  // Unlike the context's, these follow configuration changes
  val resources = LocalResources.current
  // The light style would glare in dark mode, which has its own
  val darkTheme = AroundTheme.isDark
  val mapStyle = remember(resources, darkTheme) { MapStyleOptions(mapStyle(resources, darkTheme)) }
  val markerColors = MarkerDefaults.colors()
  val markerStyle = remember(markerColors) { MarkerDefaults.style(markerColors) }
  val density = LocalDensity.current
  val direction = LocalLayoutDirection.current
  val markerHits =
      remember(markerStyle, density, direction) {
        QuestMarkerHitTest(markerStyle, density, direction)
      }
  val mapTaps = remember { MapTapPosition() }

  fun openMarker(pin: VenuePin) {
    if (pin.key == state.selectedPinKey) onFocusQuest(pin.venueId, pin.featuredQuest.id)
    else viewModel.onPinClick(pin.key)
  }

  fun onMapTap(sdkMarker: VenuePin? = null) {
    val projection = cameraPositionState.projection
    val project = projection?.let { map ->
      { pin: VenuePin -> map.toScreenLocation(pin.location.toLatLng()) }
    }
    val hit = markerHits.find(mapTaps.take(), state.pins, state.selectedPinKey, sdkMarker, project)
    if (hit == null) viewModel.onMapClick() else openMarker(hit)
  }

  BoxWithConstraints(Modifier.fillMaxSize().testTag(C.Tag.MAP_SCREEN)) {
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    GoogleMap(
        modifier = Modifier.fillMaxSize().testTag(C.Tag.MAP).observeMapTaps(mapTaps),
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
        onMapClick = { onMapTap() },
        onPOIClick = { onMapTap() },
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
          onMarkerClick = ::onMapTap,
          onCardSize = markerHits::recordCardSize,
      )
    }
    if (state.showsNearby) {
      NearbyQuestOverlay(
          state.nearby,
          locationOn = state.showsUserLocation,
          // The quests stay open, so Back from the details finds them where they were
          onOpenQuest = { onOpenQuest(it.venueId, it.questId) },
          onShowOnMap = viewModel::onShowOnMap,
          onClose = viewModel::onNearbyClose,
          filters = state.filters,
          onFiltersChange = viewModel::onFiltersChange,
          modifier = Modifier.align(Alignment.BottomCenter),
          acceptState = acceptState,
          onAccept = { acceptViewModel?.accept(it) },
      )
    } else {
      NearbyQuestsButton(
          viewModel::onNearbyOpen,
          Modifier.align(Alignment.BottomEnd).padding(AroundSpacing.Large),
      )
    }
    // The location banner comes last, under the offline or failure one, which says why pins may be
    // missing
    Column(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(AroundSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
    ) {
      if (showOfflineBanner) OfflineBanner()
      if (state.loadFailed) VenuesErrorBanner(viewModel::onRetry, retrying = state.retrying)
      if (state.locationRefused) LocationBanner(turnOnLocation)
      SnackbarHost(snackbarHostState)
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
