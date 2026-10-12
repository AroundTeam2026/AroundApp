// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.children
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.junit.Assert.assertTrue

/** How long the Maps SDK may take to start, which is slow on a cold emulator. */
const val MAP_TIMEOUT_MILLIS = 30_000L

/** Whether this point is within about 100 m of [location]: where a camera counts as being there. */
fun LatLng.isNear(location: Location) =
    abs(latitude - location.lat) < 1e-3 && abs(longitude - location.lng) < 1e-3

/** The first [MapView] in this view's hierarchy, or null if it has none. */
fun View.findMapView(): MapView? =
    when (this) {
      is MapView -> this
      is ViewGroup -> children.firstNotNullOfOrNull { it.findMapView() }
      else -> null
    }

/**
 * The [GoogleMap] of the activity's map, once the Maps SDK provides it; fails if it never does.
 * Each new [MapView], like the one shown after returning to the Map tab, has its own.
 */
fun AndroidComposeTestRule<*, out ComponentActivity>.awaitGoogleMap(): GoogleMap {
  waitUntil(MAP_TIMEOUT_MILLIS) {
    runOnUiThread { activity.window.decorView.findMapView() } != null
  }
  val map = AtomicReference<GoogleMap>()
  val ready = CountDownLatch(1)
  runOnUiThread {
    activity.window.decorView.findMapView()!!.getMapAsync {
      map.set(it)
      ready.countDown()
    }
  }
  assertTrue("The Maps SDK never provided the map", ready.await(MAP_TIMEOUT_MILLIS, MILLISECONDS))
  return map.get()
}

/**
 * Taps the map tagged [tag] at [at] until [done], as a user would. The Maps SDK ignores taps until
 * it has drawn, and the view model can list a marker before the map draws it, so the first taps may
 * land on a bare map. Between taps it waits out the double-tap timeout, and after the last one too,
 * since the caller's next tap may be on the same spot.
 *
 * Gives up after [MAP_TIMEOUT_MILLIS] without failing: the caller asserts [done], with a message
 * that says what it expected.
 */
fun ComposeTestRule.tapMapUntil(
    tag: String,
    at: TouchInjectionScope.() -> Offset = { center },
    done: () -> Boolean,
) {
  val deadline = System.currentTimeMillis() + MAP_TIMEOUT_MILLIS
  while (!done() && System.currentTimeMillis() < deadline) {
    onNodeWithTag(tag).performTouchInput { click(at()) }
    waitOutDoubleTap(done)
  }
  waitOutDoubleTap()
}

/**
 * Waits out the double-tap timeout, or until [done]: the Maps SDK turns two taps on one spot within
 * it into a zoom, and never hands the second to a marker. Frames keep coming meanwhile, so the map
 * redraws.
 */
private fun ComposeTestRule.waitOutDoubleTap(done: () -> Boolean = { false }) {
  val end = System.currentTimeMillis() + ViewConfiguration.getDoubleTapTimeout() + 200
  waitUntil(MAP_TIMEOUT_MILLIS) { done() || System.currentTimeMillis() >= end }
}
