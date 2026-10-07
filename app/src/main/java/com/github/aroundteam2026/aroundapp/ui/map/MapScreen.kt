// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.aroundteam2026.aroundapp.resources.C
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings

/**
 * The Explorer's map. It asks for the location permission if needed, then frames
 * [MapViewModel.NEARBY_RADIUS_METERS] around the explorer.
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

  BoxWithConstraints(Modifier.fillMaxSize().testTag(C.Tag.MAP_SCREEN)) {
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    GoogleMap(
        modifier = Modifier.fillMaxSize().testTag(C.Tag.MAP),
        cameraPositionState = cameraPositionState,
        // Both need the permission, or the Maps SDK throws a SecurityException
        properties = MapProperties(isMyLocationEnabled = state.showsUserLocation),
        uiSettings = MapUiSettings(myLocationButtonEnabled = state.showsUserLocation),
    ) {
      FrameArea(state.areaToFrame, cameraPositionState, width, height, viewModel::onAreaFramed)
    }
  }
}
