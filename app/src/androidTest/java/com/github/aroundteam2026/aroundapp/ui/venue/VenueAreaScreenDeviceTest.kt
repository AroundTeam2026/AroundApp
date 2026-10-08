// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import android.graphics.Point
import android.graphics.PointF
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.DEFAULT_MAP_CENTER
import com.github.aroundteam2026.aroundapp.ui.map.MAP_TIMEOUT_MILLIS
import com.github.aroundteam2026.aroundapp.ui.map.awaitGoogleMap
import com.github.aroundteam2026.aroundapp.ui.map.toLatLngBounds
import com.github.aroundteam2026.aroundapp.ui.venue.VenueAreaViewModel.Companion.FRAMED_RADIUS_METERS
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import kotlin.math.abs
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

  @Test
  fun draggingTheMarkerMovesItWhereItIsDropped() {
    show()
    val start = placeMarkerByTapping()
    val map =
        composeTestRule.onNodeWithTag(C.Tag.VENUE_AREA_MAP).fetchSemanticsNode().boundsInWindow
    // The tap put the marker's tip in the middle of the map. A one-pixel press on the tip can miss
    // the pin on low-density screens, so it presses just above it, as a fingertip would. The drag
    // is a share of the map's width, so it stays on the map on any screen size.
    val density = composeTestRule.activity.resources.displayMetrics.density
    val press = PointF(map.center.x, map.center.y - PRESS_ABOVE_TIP_DP * density)
    val release = PointF(press.x + map.width * DRAG_SHARE_OF_MAP_WIDTH, press.y)
    val googleMap = composeTestRule.awaitGoogleMap()
    val releasedOver = composeTestRule.runOnUiThread {
      googleMap.projection.fromScreenLocation(
          Point((release.x - map.left).toInt(), (release.y - map.top).toInt())
      )
    }

    dragOnScreen(from = press, to = release)

    composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) { viewModel.uiState.value.marker != start }
    // It lands where the finger let go, east to west. Its latitude isn't checked: older Maps
    // renderers lift a dragged marker above the finger, so it drops a little north of it.
    val dropped = viewModel.uiState.value.marker!!
    val dragged = releasedOver.longitude - start.lng
    assertEquals(
        "The marker went from $start to $dropped, released over $releasedOver",
        releasedOver.longitude,
        dropped.lng,
        dragged * 0.25,
    )
  }

  /**
   * Presses at [from], holds long enough for the Maps SDK to pick up a marker there, moves to [to]
   * while still pressing, and lets go, as a finger does. Each event is delivered before the next.
   */
  private fun dragOnScreen(from: PointF, to: PointF) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val downTime = SystemClock.uptimeMillis()
    fun send(action: Int, at: PointF) {
      val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, at.x, at.y, 0)
      event.source = InputDevice.SOURCE_TOUCHSCREEN
      instrumentation.sendPointerSync(event)
      event.recycle()
    }
    send(MotionEvent.ACTION_DOWN, from)
    Thread.sleep(ViewConfiguration.getLongPressTimeout() * 3L)
    for (step in 1..DRAG_STEPS) {
      val fraction = step.toFloat() / DRAG_STEPS
      send(
          MotionEvent.ACTION_MOVE,
          PointF(from.x + (to.x - from.x) * fraction, from.y + (to.y - from.y) * fraction),
      )
      Thread.sleep(10)
    }
    send(MotionEvent.ACTION_UP, to)
  }

  /** Waits until the camera rests at [location]. */
  private fun awaitCameraAt(location: Location) {
    composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) {
      composeTestRule.runOnUiThread { camera.position.target.isNear(location) && !camera.isMoving }
    }
  }

  private fun Location.toLatLng() = LatLng(lat, lng)

  private fun LatLng.isNear(location: Location) =
      abs(latitude - location.lat) < 1e-3 && abs(longitude - location.lng) < 1e-3

  private fun LatLngBounds.latitudeSpan() = northeast.latitude - southwest.latitude

  private fun LatLngBounds.longitudeSpan() = northeast.longitude - southwest.longitude

  private companion object {
    /** Moves a drag is split into, so the Maps SDK sees the marker travel. */
    const val DRAG_STEPS = 20

    /** How far the marker is dragged, as a share of the map's width. */
    const val DRAG_SHARE_OF_MAP_WIDTH = 0.25f

    /** How far above the marker's tip the drag presses, in dp. */
    const val PRESS_ABOVE_TIP_DP = 1
  }
}
