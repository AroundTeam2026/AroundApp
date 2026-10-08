// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.DEFAULT_MAP_CENTER
import com.github.aroundteam2026.aroundapp.ui.map.MAP_TIMEOUT_MILLIS
import com.github.aroundteam2026.aroundapp.ui.map.isNear
import com.github.aroundteam2026.aroundapp.ui.map.toLatLng
import com.github.aroundteam2026.aroundapp.ui.map.toLatLngBounds
import com.github.aroundteam2026.aroundapp.ui.venue.VenueAreaViewModel.Companion.FRAMED_RADIUS_METERS
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the venue area screen on a device, where the Maps SDK really draws. */
@RunWith(AndroidJUnit4::class)
class VenueAreaScreenDeviceTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val here = DEFAULT_MAP_CENTER

  private val viewModel = VenueAreaViewModel()
  private val camera = CameraPositionState()

  /** Shows the screen and waits until it framed its starting area, as it does when opened. */
  private fun show() {
    composeTestRule.setContent { VenueAreaScreen(viewModel, camera) }
    awaitCameraAt(here)
  }

  @Test
  fun opensFramingTheAreaAroundTheDefaultCenter() {
    show()

    val area = here.boundsWithin(FRAMED_RADIUS_METERS).toLatLngBounds()
    val visible = composeTestRule.runOnUiThread { camera.projection!!.visibleRegion.latLngBounds }
    // The area fills the map's narrow side: none of it is cut off, and the map shows no more,
    // allowing for rounding at that edge
    val heightRatio = visible.latitudeSpan() / area.latitudeSpan()
    val widthRatio = visible.longitudeSpan() / area.longitudeSpan()
    assertEquals("visible $visible, area $area", 1.0, minOf(heightRatio, widthRatio), 0.01)
  }

  /** Taps the middle of the map until the tap places the marker, and returns where it is. */
  private fun placeMarkerByTapping(): Location {
    // Taps before the map has drawn are ignored, so tap again until one counts, as a user would.
    // Taps closer together than a double tap would zoom instead.
    composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) {
      composeTestRule.onNodeWithTag(C.Tag.VENUE_AREA_MAP).performTouchInput { click(center) }
      Thread.sleep(500)
      viewModel.uiState.value.marker != null
    }
    return viewModel.uiState.value.marker!!
  }

  @Test
  fun tappingTheMapPlacesTheMarkerWhereTapped() {
    show()

    val marker = placeMarkerByTapping()

    // The map's center is where the camera points
    assertTrue("The marker is at $marker", marker.toLatLng().isNear(here))
  }

  /** Waits until the camera rests at [location]. */
  private fun awaitCameraAt(location: Location) {
    composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) {
      composeTestRule.runOnUiThread { camera.position.target.isNear(location) && !camera.isMoving }
    }
  }

  private fun LatLngBounds.latitudeSpan() = northeast.latitude - southwest.latitude

  private fun LatLngBounds.longitudeSpan() = northeast.longitude - southwest.longitude
}
