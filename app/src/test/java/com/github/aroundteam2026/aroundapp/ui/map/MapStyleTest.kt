// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests what the map's styles say that the map's pixels can't easily show: which of Google's icons
 * they hide in either theme, and that their colours are written so the Maps SDK reads them, as the
 * SDK skips what it can't read without failing. `MapColoursDeviceTest` checks the colours the map
 * really draws.
 */
@RunWith(AndroidJUnit4::class)
class MapStyleTest {

  private val resources = ApplicationProvider.getApplicationContext<Context>().resources

  private val themes = listOf(false, true)

  private fun themeName(darkTheme: Boolean) = if (darkTheme) "dark" else "light"

  private fun rules(darkTheme: Boolean): List<JSONObject> =
      JSONArray(mapStyle(resources, darkTheme)).let { array ->
        (0 until array.length()).map { array.getJSONObject(it) }
      }

  private fun JSONObject.stylers(): List<JSONObject> {
    val stylers = getJSONArray("stylers")
    return (0 until stylers.length()).map { stylers.getJSONObject(it) }
  }

  /** The stylers the [darkTheme]'s style applies to [featureType] and, if given, [elementType]. */
  private fun stylersOf(darkTheme: Boolean, featureType: String, elementType: String? = null) =
      rules(darkTheme)
          .filter { it.optString("featureType", "all") == featureType }
          .filter { elementType == null || it.optString("elementType") == elementType }
          .flatMap { it.stylers() }

  /** The visibility the [darkTheme]'s style gives these features, ignoring colour-only rules. */
  private fun visibilityOf(darkTheme: Boolean, featureType: String, elementType: String? = null) =
      stylersOf(darkTheme, featureType, elementType)
          .map { it.optString("visibility") }
          .filter(String::isNotEmpty)

  /** The colour the [darkTheme]'s style gives the geometry of [featureType]. */
  private fun geometryColour(darkTheme: Boolean, featureType: String): String =
      stylersOf(darkTheme, featureType, "geometry").map { it.optString("color") }.single()

  private fun luminance(colour: String): Double {
    val c = Color.parseColor(colour)
    return (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)) / 255
  }

  @Test
  fun everyColourIsAnRgbHexCode() {
    themes.forEach { dark ->
      val colours =
          rules(dark)
              .flatMap { it.stylers() }
              .map { it.optString("color") }
              .filter(String::isNotEmpty)

      assertTrue("The ${themeName(dark)} style has no colours", colours.isNotEmpty())
      colours.forEach {
        assertTrue("$it, in the ${themeName(dark)} style, isn't #RRGGBB", it.matches(HEX_COLOUR))
      }
    }
  }

  @Test
  fun bothThemesHideEveryPlacesIconSoNoneSitsUnderAQuestPin() {
    // Google's landmark and place icons look like pins, and sit right under the venues' own
    themes.forEach { dark ->
      assertEquals(themeName(dark), listOf("off"), visibilityOf(dark, "poi", "labels.icon"))
    }
  }

  @Test
  fun bothThemesHideBusinessesSoOnlyQuestVenuesStandOut() {
    // Google's own shop and restaurant icons would compete with the quest pins
    themes.forEach { dark ->
      assertEquals(themeName(dark), listOf("off"), visibilityOf(dark, "poi.business"))
    }
  }

  @Test
  fun bothThemesHideRoadIcons() {
    // Highway shields and transit icons look like pins too
    themes.forEach { dark ->
      assertEquals(themeName(dark), listOf("off"), visibilityOf(dark, "road", "labels.icon"))
    }
  }

  @Test
  fun noRuleShowsAHiddenFeatureAgain() {
    // The Maps SDK applies rules in order, so a later "on" would undo the hiding above
    themes.forEach { dark ->
      val visibilities =
          rules(dark).flatMap { it.stylers() }.map { it.optString("visibility") }.toSet()

      assertEquals(themeName(dark), setOf("", "off"), visibilities + "")
    }
  }

  @Test
  fun inDarkModeTheLandIsTheDesignsNightGrey() {
    assertEquals(NIGHT_LAND, geometryColour(darkTheme = true, featureType = "landscape"))
  }

  @Test
  fun inDarkModeNothingIsDrawnBright() {
    // A single light road or lake would glare on a dark screen
    val geometry =
        rules(darkTheme = true)
            .filter { it.optString("elementType").startsWith("geometry") }
            .flatMap { it.stylers() }
            .map { it.optString("color") }
            .filter(String::isNotEmpty)

    assertTrue("The dark style colours no geometry", geometry.isNotEmpty())
    geometry.forEach { assertTrue("$it is too bright for dark mode", luminance(it) < 0.35) }
  }

  @Test
  fun inLightModeTheLandAndWaterStayLight() {
    listOf("landscape", "water").forEach {
      val colour = geometryColour(darkTheme = false, featureType = it)
      assertTrue("$it is $colour, too dark for light mode", luminance(colour) > 0.6)
    }
  }

  private companion object {
    val HEX_COLOUR = Regex("#[0-9a-fA-F]{6}")

    /** The dark map's land colour in the Figma design. */
    const val NIGHT_LAND = "#2d2d2d"
  }
}
