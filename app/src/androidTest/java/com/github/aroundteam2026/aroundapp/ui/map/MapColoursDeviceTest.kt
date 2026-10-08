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
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.location.LocationRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import kotlin.math.abs
import org.json.JSONArray
import org.junit.AfterClass
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
    val viewModel = MapViewModel(LocatedAt(LAUSANNE), FakeQuestRepository(), FakeVenueRepository())
    composeTestRule.setContent { MapScreen(viewModel) }

    composeTestRule.awaitMapColours(lightPalette, "the light style's land and water") { pixels ->
      pixels.share { it.isOneOf(lightPalette.land) } >= 0.15 &&
          pixels.share { it.isOneOf(lightPalette.water) } >= 0.25
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
  fun inDarkModeTheMapIsDrawnInTheNightColours() {
    // A style draws on every Maps renderer, so this runs on CI's legacy renderer too
    val viewModel = MapViewModel(LocatedAt(LAUSANNE), FakeQuestRepository(), FakeVenueRepository())
    composeTestRule.setContent { MapScreen(viewModel) }

    composeTestRule.awaitMapColours(
        darkPalette,
        "the dark style's land and water, and none of the light style",
    ) { pixels ->
      pixels.share { it.isOneOf(darkPalette.land) } >= 0.15 &&
          pixels.share { it.isOneOf(darkPalette.water) } >= 0.25 &&
          pixels.share { it.isOneOf(lightPalette.land) || it.isOneOf(lightPalette.water) } < 0.05
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

/** Turns the device's dark mode on or off; activities started afterwards follow it. */
private fun setNightMode(on: Boolean) {
  InstrumentationRegistry.getInstrumentation()
      .uiAutomation
      .executeShellCommand("cmd uimode night ${if (on) "yes" else "no"}")
      .close()
}

/** A style's own colours for the land and the water, unlike Google's or any near-white. */
private class Palette(val land: List<Int>, val water: List<Int>)

/** The colours the map's style for [darkTheme] gives [featureTypes], read from the style itself. */
private fun styleColours(darkTheme: Boolean, vararg featureTypes: String): List<Int> {
  val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
  val rules = JSONArray(mapStyle(resources, darkTheme))
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

private fun palette(darkTheme: Boolean) =
    Palette(
        land = styleColours(darkTheme, "landscape", "landscape.man_made"),
        water = styleColours(darkTheme, "water"),
    )

private val lightPalette by lazy { palette(darkTheme = false) }
private val darkPalette by lazy { palette(darkTheme = true) }

/** Whether this colour is one of [colours], give or take the screen's rounding. */
private fun Int.isOneOf(colours: List<Int>) = colours.any { colour ->
  abs(Color.red(this) - Color.red(colour)) <= 6 &&
      abs(Color.green(this) - Color.green(colour)) <= 6 &&
      abs(Color.blue(this) - Color.blue(colour)) <= 6
}

private fun List<Int>.share(predicate: (Int) -> Boolean) =
    if (isEmpty()) 0.0 else count(predicate).toDouble() / size

private fun percent(pixels: List<Int>, predicate: (Int) -> Boolean) =
    (pixels.share(predicate) * 100).toInt()

/**
 * Waits until the colours of the middle of the screen, where only the map is, meet [expected]; the
 * map draws its tiles a while after it appears. Fails with [what] it expected and how much of
 * [palette]'s land and water it saw.
 */
private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.awaitMapColours(
    palette: Palette,
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
        "Expected the map to be $what, but ${percent(last) { it.isOneOf(palette.land) }}% was " +
            "that style's land, ${percent(last) { it.isOneOf(palette.water) }}% its water, and " +
            "${percent(last) { it.isOneOf(lightPalette.land) }}% the light style's land",
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
