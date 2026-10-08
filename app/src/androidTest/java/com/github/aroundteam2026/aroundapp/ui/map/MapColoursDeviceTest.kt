// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.location.LocationRepository
import com.google.android.gms.maps.MapsInitializer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.json.JSONArray
import org.junit.AfterClass
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the map on a device in light mode, and checks what colours it really draws. */
@RunWith(AndroidJUnit4::class)
class MapColoursDeviceTest {
  @get:Rule
  val permissions: GrantPermissionRule =
      GrantPermissionRule.grant(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun theMapIsDrawnInTheAppsColours() {
    // Lausanne: the town and the lake fill the screen, in the style's land and water colours
    val viewModel = MapViewModel(LocatedAt(LAUSANNE))
    composeTestRule.setContent { MapScreen(viewModel) }

    composeTestRule.awaitMapColours("the style's land and water") { pixels ->
      pixels.share { it.isOneOf(styleLand) } >= 0.15 &&
          pixels.share { it.isOneOf(styleWater) } >= 0.25
    }
  }

  companion object {
    @JvmStatic @BeforeClass fun lightMode() = setNightMode(false)
  }
}

/** Runs the map on a device in dark mode, where the app's light style would glare. */
@RunWith(AndroidJUnit4::class)
class DarkMapColoursDeviceTest {
  @get:Rule
  val permissions: GrantPermissionRule =
      GrantPermissionRule.grant(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun inDarkModeTheMapIsGooglesDarkMap() {
    // There is no night version of the style yet, so the map takes Google's own dark colours.
    // Only the current Maps renderer has them; older Play services, as on CI's image, can't.
    assumeTrue("This device's Maps renderer has no dark map", hasLatestMapsRenderer())
    val viewModel = MapViewModel(LocatedAt(LAUSANNE))
    composeTestRule.setContent { MapScreen(viewModel) }

    composeTestRule.awaitMapColours("mostly dark, and none of the light style") { pixels ->
      pixels.share { it.luminance() < 0.35 } >= 0.5 && pixels.share { it.isStyleColour() } < 0.05
    }
  }

  companion object {
    @JvmStatic @BeforeClass fun darkMode() = setNightMode(true)

    @JvmStatic @AfterClass fun backToLightMode() = setNightMode(false)
  }
}

private val LAUSANNE = Location(46.5197, 6.6323)

private class LocatedAt(private val location: Location) : LocationRepository {
  override suspend fun currentLocation() = location
}

/** Whether the Maps SDK draws with its current renderer here, rather than the legacy one. */
private fun hasLatestMapsRenderer(): Boolean {
  val instrumentation = InstrumentationRegistry.getInstrumentation()
  val renderer = AtomicReference<MapsInitializer.Renderer>()
  val ready = CountDownLatch(1)
  instrumentation.runOnMainSync {
    MapsInitializer.initialize(instrumentation.targetContext, MapsInitializer.Renderer.LATEST) {
      renderer.set(it)
      ready.countDown()
    }
  }
  ready.await(MAP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
  return renderer.get() == MapsInitializer.Renderer.LATEST
}

/** Turns the device's dark mode on or off; activities started afterwards follow it. */
private fun setNightMode(on: Boolean) {
  InstrumentationRegistry.getInstrumentation()
      .uiAutomation
      .executeShellCommand("cmd uimode night ${if (on) "yes" else "no"}")
      .close()
}

/** The colours the style gives [featureTypes], read from the style itself. */
private fun styleColours(vararg featureTypes: String): List<Int> {
  val context = InstrumentationRegistry.getInstrumentation().targetContext
  val rules =
      context.resources.openRawResource(R.raw.map_style).bufferedReader().use {
        JSONArray(it.readText())
      }
  return (0 until rules.length())
      .map { rules.getJSONObject(it) }
      .filter { it.optString("featureType") in featureTypes }
      .filter { it.optString("elementType").startsWith("geometry") }
      .flatMap { rule ->
        val stylers = rule.getJSONArray("stylers")
        (0 until stylers.length()).map { stylers.getJSONObject(it).optString("color") }
      }
      .filter { it.isNotEmpty() }
      .map(Color::parseColor)
}

/** The style's own colours for the land and the water, unlike Google's or any near-white. */
private val styleLand by lazy { styleColours("landscape", "landscape.man_made") }
private val styleWater by lazy { styleColours("water") }

/** Whether this colour is one of [colours], give or take the screen's rounding. */
private fun Int.isOneOf(colours: List<Int>) = colours.any { colour ->
  abs(Color.red(this) - Color.red(colour)) <= 6 &&
      abs(Color.green(this) - Color.green(colour)) <= 6 &&
      abs(Color.blue(this) - Color.blue(colour)) <= 6
}

/** Whether this colour is the style's land or water. */
private fun Int.isStyleColour() = isOneOf(styleLand) || isOneOf(styleWater)

private fun Int.luminance() =
    (0.2126 * Color.red(this) + 0.7152 * Color.green(this) + 0.0722 * Color.blue(this)) / 255

private fun List<Int>.share(predicate: (Int) -> Boolean) =
    if (isEmpty()) 0.0 else count(predicate).toDouble() / size

private fun percent(pixels: List<Int>, predicate: (Int) -> Boolean) =
    (pixels.share(predicate) * 100).toInt()

/**
 * Waits until the colours of the middle of the screen, where only the map is, meet [expected]; the
 * map draws its tiles a while after it appears. Fails with [what] it expected and what it saw.
 */
private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.awaitMapColours(
    what: String,
    expected: (List<Int>) -> Boolean,
) {
  var last = emptyList<Int>()
  try {
    waitUntil(MAP_TIMEOUT_MILLIS) {
      last = screenMiddle()
      expected(last)
    }
  } catch (e: ComposeTimeoutException) {
    throw AssertionError(
        "Expected the map to be $what, but ${percent(last) { it.isOneOf(styleLand) }}% was the " +
            "style's land, ${percent(last) { it.isOneOf(styleWater) }}% its water and " +
            "${percent(last) { it.luminance() < 0.35 }}% dark",
        e,
    )
  }
}

/**
 * Colours sampled across the middle of the screen, as the device shows it. The map draws on its own
 * surface, which only a screenshot of the device sees; the corners hold the map's buttons.
 */
private fun screenMiddle(): List<Int> {
  val bitmap =
      InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
          ?: return emptyList()
  val pixels =
      (bitmap.height / 4 until bitmap.height * 3 / 4 step 8).flatMap { y ->
        (bitmap.width / 5 until bitmap.width * 4 / 5 step 8).map { x -> bitmap.getPixel(x, y) }
      }
  bitmap.recycle()
  return pixels
}
