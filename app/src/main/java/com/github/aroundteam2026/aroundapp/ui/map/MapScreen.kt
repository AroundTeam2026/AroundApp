// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.aroundteam2026.aroundapp.resources.C
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
 * @param cameraPositionState Where the camera is; tests pass their own to read it.
 */
@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel(factory = MapViewModel.factory),
    cameraPositionState: CameraPositionState = rememberMapCamera(),
) {
  val state by viewModel.uiState.collectAsState()
  RequestLocationPermission(viewModel::onLocationPermissionResult)
  val context = LocalContext.current
  // The light style would glare in dark mode, which has its own
  val darkTheme = isSystemInDarkTheme()
  val mapStyle =
      remember(context, darkTheme) { MapStyleOptions(mapStyle(context.resources, darkTheme)) }

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
    ) {
      FrameArea(state.areaToFrame, cameraPositionState, width, height, viewModel::onAreaFramed)
    }
  }
}
