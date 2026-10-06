// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.location.LocationPermissions
import com.github.aroundteam2026.aroundapp.resources.C
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState

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
      // The content only runs once the map exists, which camera updates need
      val area = state.areaToFrame
      LaunchedEffect(area) {
        if (area == null) return@LaunchedEffect
        cameraPositionState.move(
            CameraUpdateFactory.newLatLngBounds(area.toLatLngBounds(), width, height, 0)
        )
        viewModel.onAreaFramed(area)
      }
    }
  }
}

/**
 * The map's camera, saved with the screen: switching tabs or rotating keeps it where the explorer
 * left it.
 */
@Composable internal fun rememberMapCamera(): CameraPositionState = rememberCameraPositionState()

/**
 * Reports whether a location permission is granted, asking the user first when none is. It asks
 * once per visit to the map: after a refusal, switching tabs or rotating doesn't show the dialog
 * again, but a permission granted in the settings meanwhile is still picked up. Back pops the map
 * with its state, so the next visit asks again.
 */
@Composable
private fun RequestLocationPermission(onResult: (granted: Boolean) -> Unit) {
  val context = LocalContext.current
  // Saved with the screen, so it survives switching tabs and rotating, but not Back
  var asked by rememberSaveable { mutableStateOf(false) }
  // A dismissed dialog answers with no grants at all, which counts as a refusal
  val launcher =
      rememberLauncherForActivityResult(RequestMultiplePermissions()) { grants ->
        onResult(grants.values.any { it })
      }
  LaunchedEffect(Unit) {
    when {
      LocationPermissions.isGranted(context) -> onResult(true)
      asked -> onResult(false)
      else -> {
        asked = true
        launcher.launch(LocationPermissions.ALL)
      }
    }
  }
}

/** The Maps SDK's version of these bounds; it also wraps across the antimeridian. */
fun GeoBounds.toLatLngBounds(): LatLngBounds {
  // LatLng turns longitude 180 into -180, which would collapse bounds spanning every longitude, as
  // near a pole, to no width at all; the largest longitude below 180 keeps them whole.
  val eastEdge = if (west == -180.0 && east == 180.0) Math.nextDown(180.0) else east
  return LatLngBounds(LatLng(south, west), LatLng(north, eastEdge))
}
