// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.model.location.LocationRepository
import com.github.aroundteam2026.aroundapp.ui.navigation.AroundApp
import com.github.aroundteam2026.aroundapp.ui.navigation.Tab
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the map screen on a device, where the Maps SDK really draws. */
@RunWith(AndroidJUnit4::class)
class MapScreenDeviceTest {

  @get:Rule
  val permissions: GrantPermissionRule =
      GrantPermissionRule.grant(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun thisBuildCarriesAGoogleMapsApiKey() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val key =
        context.packageManager
            .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            .metaData
            .getString("com.google.android.geo.API_KEY")

    // Google API keys are 39 characters starting with "AIza"; anything else is the placeholder
    assertTrue(
        "No Maps API key in this build: set MAPS_API_KEY (see the README), got $key",
        key != null && key.startsWith("AIza") && key.length == 39,
    )
  }

  @Test
  fun framesFiveKilometresAroundTheExplorer() {
    val camera = CameraPositionState()
    val viewModel = MapViewModel(StaticLocation(here))
    composeTestRule.setContent { MapScreen(viewModel, camera) }

    // The camera starts on the default center, so reaching the explorer means it framed them
    composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) {
      composeTestRule.runOnUiThread { camera.position.target.isNear(here) && !camera.isMoving }
    }

    val area = here.boundsWithin(MapViewModel.NEARBY_RADIUS_METERS).toLatLngBounds()
    val visible = composeTestRule.runOnUiThread { camera.projection!!.visibleRegion.latLngBounds }
    val heightRatio = visible.latitudeSpan() / area.latitudeSpan()
    val widthRatio = visible.longitudeSpan() / area.longitudeSpan()
    val spans = "visible $visible, area $area"
    // The whole 5 km fits on screen...
    assertTrue("Part of the area is cut off: $spans", minOf(heightRatio, widthRatio) > 0.99)
    // ...and fills the screen's narrow side, so the map isn't zoomed out past it
    assertEquals(
        "The map shows more than the area: $spans",
        1.0,
        minOf(heightRatio, widthRatio),
        0.05,
    )
  }

  @Test
  fun switchingTabsKeepsTheCameraWhereTheExplorerLeftIt() {
    val viewModel = MapViewModel(StaticLocation(here))
    // The camera MapScreen makes by default, made where the app makes it: inside the Map tab, which
    // saves it. This covers rememberMapCamera(), not that MapScreen's default argument still calls
    // it.
    var camera: CameraPositionState? = null
    composeTestRule.setContent {
      AroundApp(
          screen = { tab ->
            if (tab == Tab.MAP) {
              val mapCamera = rememberMapCamera()
              SideEffect { camera = mapCamera }
              MapScreen(viewModel, mapCamera)
            } else {
              Text("")
            }
          }
      )
    }
    openTab(Tab.MAP)
    awaitCameraAt(here) { camera }

    val elsewhere = Location(47.45, 8.70)
    composeTestRule.runOnUiThread {
      camera!!.move(CameraUpdateFactory.newLatLng(LatLng(elsewhere.lat, elsewhere.lng)))
    }
    // move() only updates the camera's state once the map reports it idle; leaving before then
    // would hand the next map the old position
    awaitCameraAt(elsewhere) { camera }
    openTab(Tab.PROFILE)
    openTab(Tab.MAP)

    // A new map: framing the explorer again, or a camera that wasn't saved, would move it away
    val map = composeTestRule.awaitGoogleMap()
    composeTestRule.waitForIdle()
    val target = composeTestRule.runOnUiThread { map.cameraPosition.target }
    assertTrue("The camera moved to $target", target.isNear(elsewhere))
  }

  @Test
  fun theExplorersPositionIsOnlyDrawnWithThePermission() {
    val viewModel = MapViewModel(StaticLocation(here))
    val camera = CameraPositionState()
    composeTestRule.setContent { MapScreen(viewModel, camera) }
    val map = composeTestRule.awaitGoogleMap()

    // Granted by the rule above, so the map draws the position and its button
    composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) {
      composeTestRule.runOnUiThread { map.isMyLocationEnabled }
    }
    assertTrue(composeTestRule.runOnUiThread { map.uiSettings.isMyLocationButtonEnabled })

    // Revoked: drawing it anyway makes the Maps SDK throw a SecurityException. A test can't revoke
    // its own app's permission without killing itself, so it reports the refusal directly.
    composeTestRule.runOnUiThread { viewModel.onLocationPermissionResult(granted = false) }
    composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) {
      composeTestRule.runOnUiThread { !map.isMyLocationEnabled }
    }
    assertFalse(composeTestRule.runOnUiThread { map.uiSettings.isMyLocationButtonEnabled })
  }

  private fun openTab(tab: Tab) {
    composeTestRule.onNodeWithTag(tab.tabTag).performClick()
    composeTestRule.waitForIdle()
  }

  /** Waits until the camera rests at [location]; [camera] gives the current one, or null. */
  private fun awaitCameraAt(location: Location, camera: () -> CameraPositionState?) {
    composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) {
      composeTestRule.runOnUiThread {
        camera()?.let { it.position.target.isNear(location) && !it.isMoving } == true
      }
    }
  }

  private companion object {
    val here = Location(47.3769, 8.5417)
  }
}

private class StaticLocation(private val location: Location) : LocationRepository {
  override suspend fun currentLocation() = location
}

private fun LatLng.isNear(location: Location) =
    abs(latitude - location.lat) < 1e-3 && abs(longitude - location.lng) < 1e-3

private fun LatLngBounds.latitudeSpan() = northeast.latitude - southwest.latitude

private fun LatLngBounds.longitudeSpan() = northeast.longitude - southwest.longitude
