// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.aroundteam2026.aroundapp.model.location.LocationPermissions
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.marker.MarkerDefaults
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings

/**
 * The Explorer's map, in the app's colours: a light style in light mode and a night one in dark
 * mode, both hiding Google's place icons (see [mapStyle]). It asks for the location permission if
 * needed, then frames [MapViewModel.NEARBY_RADIUS_METERS] around the explorer.
 *
 * It marks each venue on screen that has valid quests with a pin. Tapping a pin opens it into a
 * card, tapping the card opens the venue's page, and tapping the map closes the card.
 *
 * @param cameraPositionState Where the camera is; tests pass their own to read it.
 * @param onOpenVenue Opens the page of the venue with this id and name, when its card is tapped.
 *   Nothing opens yet: the venue page comes in its own task.
 */
@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel(factory = MapViewModel.factory),
    cameraPositionState: CameraPositionState = rememberMapCamera(),
    onOpenVenue: (venueId: String, venueName: String) -> Unit = { _, _ -> },
) {
  val state by viewModel.uiState.collectAsState()
  RequestLocationPermission(viewModel::onLocationPermissionResult)
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
        uiSettings = MapUiSettings(myLocationButtonEnabled = state.showsUserLocation),
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
          selectedVenueId = state.selectedVenueId,
          style = markerStyle,
          onPinClick = viewModel::onPinClick,
          onCardClick = { onOpenVenue(it.venueId, it.venueName) },
      )
    }
  }
}

/**
 * Reports whether a location permission is granted, asking the user first when none is. It checks
 * each time the map resumes, so a permission granted in Android's settings counts as soon as the
 * explorer comes back. It asks once per visit to the map: after a refusal, resuming, switching tabs
 * or rotating doesn't show the dialog again. The map is the start tab, so Back from it leaves the
 * app, and the next launch asks again.
 */
@Composable
private fun RequestLocationPermission(onResult: (granted: Boolean) -> Unit) {
  val context = LocalContext.current
  // The effect below outlives a composition; this keeps it calling the latest callback
  val currentOnResult by rememberUpdatedState(onResult)
  // Saved with the screen, so it survives switching tabs and rotating, but not Back
  var asked by rememberSaveable { mutableStateOf(false) }
  // A dismissed dialog answers with no grants at all, which counts as a refusal
  val launcher =
      rememberLauncherForActivityResult(RequestMultiplePermissions()) { grants ->
        onResult(grants.values.any { it })
      }
  // The permission dialog pauses and resumes the app too; `asked` keeps that from asking again
  LifecycleResumeEffect(Unit) {
    when {
      LocationPermissions.isGranted(context) -> currentOnResult(true)
      asked -> currentOnResult(false)
      else -> {
        asked = true
        launcher.launch(LocationPermissions.ALL)
      }
    }
    onPauseOrDispose {}
  }
}
