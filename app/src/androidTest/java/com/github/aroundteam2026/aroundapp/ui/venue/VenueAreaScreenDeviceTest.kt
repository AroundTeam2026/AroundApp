// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.model.location.LocationRepository
import com.github.aroundteam2026.aroundapp.ui.map.MAP_TIMEOUT_MILLIS
import com.github.aroundteam2026.aroundapp.ui.map.awaitGoogleMap
import com.github.aroundteam2026.aroundapp.ui.map.toLatLngBounds
import com.github.aroundteam2026.aroundapp.ui.venue.VenueAreaViewModel.Companion.FRAMED_RADIUS_METERS
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the venue area screen on a device, where the Maps SDK really draws. */
@RunWith(AndroidJUnit4::class)
class VenueAreaScreenDeviceTest {

  @get:Rule
  val permissions: GrantPermissionRule =
      GrantPermissionRule.grant(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val here = Location(47.3769, 8.5417)
  private val elsewhere = Location(47.45, 8.70)

  private val viewModel =
      VenueAreaViewModel(
          object : LocationRepository {
            override suspend fun currentLocation() = here
          }
      )
  private val camera = CameraPositionState()

  /** Shows the screen and waits until it framed the device, as it does when opened. */
  private fun show() {
    composeTestRule.setContent { VenueAreaScreen(viewModel, camera) }
    awaitCameraAt(here)
  }

  @Test
  fun opensFramingTheAreaAroundTheDevice() {
    show()

    val area = here.boundsWithin(FRAMED_RADIUS_METERS).toLatLngBounds()
    val visible = composeTestRule.runOnUiThread { camera.projection!!.visibleRegion.latLngBounds }
    assertTrue(
        "Part of $area is cut off from $visible",
        visible.contains(area.southwest) && visible.contains(area.northeast),
    )
  }

  @Test
  fun tappingTheMapPlacesTheMarkerWhereTapped() {
    show()

    composeTestRule.onNodeWithTag(VenueAreaTags.MAP).performTouchInput { click(center) }
    composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) { viewModel.uiState.value.marker != null }

    // The map's center is where the camera points
    val marker = viewModel.uiState.value.marker!!
    assertTrue("The marker is at $marker", LatLng(marker.lat, marker.lng).isNear(here))
  }

  @Test
  fun useMyLocationFramesTheDeviceAgainWithoutMovingTheMarker() {
    show()
    composeTestRule.runOnUiThread {
      viewModel.onMarkerPlaced(elsewhere)
      camera.move(CameraUpdateFactory.newLatLng(LatLng(elsewhere.lat, elsewhere.lng)))
    }
    awaitCameraAt(elsewhere)

    composeTestRule.onNodeWithTag(VenueAreaTags.USE_MY_LOCATION).performClick()

    awaitCameraAt(here)
    assertEquals(elsewhere, viewModel.uiState.value.marker)
  }

  @Test
  fun theDevicesPositionIsOnlyDrawnWithThePermission() {
    show()
    val map = composeTestRule.awaitGoogleMap()

    // Granted by the rule above; the "Use my location" button replaces the Maps SDK's own
    composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) {
      composeTestRule.runOnUiThread { map.isMyLocationEnabled }
    }
    assertFalse(composeTestRule.runOnUiThread { map.uiSettings.isMyLocationButtonEnabled })

    // A test can't revoke its own app's permission without killing itself, so it reports the
    // refusal directly
    composeTestRule.runOnUiThread { viewModel.onLocationPermissionResult(granted = false) }
    composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) {
      composeTestRule.runOnUiThread { !map.isMyLocationEnabled }
    }
  }

  /** Waits until the camera rests at [location]. */
  private fun awaitCameraAt(location: Location) {
    composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) {
      composeTestRule.runOnUiThread { camera.position.target.isNear(location) && !camera.isMoving }
    }
  }

  private fun LatLng.isNear(location: Location) =
      abs(latitude - location.lat) < 1e-3 && abs(longitude - location.lng) < 1e-3
}
