// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.core.view.children
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue

/** How long the Maps SDK may take to start, which is slow on a cold emulator. */
const val MAP_TIMEOUT_MILLIS = 30_000L

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
