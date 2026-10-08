// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.R
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests what the map's style says that the map's pixels can't easily show: which of Google's icons
 * it hides, and that its colours are written so the Maps SDK reads them, as the SDK skips what it
 * can't read without failing. `MapColoursDeviceTest` checks the colours the map really draws.
 */
@RunWith(AndroidJUnit4::class)
class MapStyleTest {

  private val rules: List<JSONObject> =
      ApplicationProvider.getApplicationContext<Context>()
          .resources
          .openRawResource(R.raw.map_style)
          .bufferedReader()
          .use { JSONArray(it.readText()) }
          .let { array -> (0 until array.length()).map { array.getJSONObject(it) } }

  private fun stylersOf(featureType: String, elementType: String? = null) =
      rules
          .filter { it.optString("featureType", "all") == featureType }
          .filter { elementType == null || it.optString("elementType") == elementType }
          .flatMap { rule ->
            val stylers = rule.getJSONArray("stylers")
            (0 until stylers.length()).map { stylers.getJSONObject(it) }
          }

  @Test
  fun everyColourIsAnRgbHexCode() {
    val colours =
        rules
            .flatMap { rule ->
              val stylers = rule.getJSONArray("stylers")
              (0 until stylers.length()).map { stylers.getJSONObject(it) }
            }
            .map { it.optString("color") }
            .filter(String::isNotEmpty)

    assertTrue(colours.isNotEmpty())
    colours.forEach { assertTrue("$it isn't #RRGGBB", it.matches(Regex("#[0-9a-fA-F]{6}"))) }
  }

  @Test
  fun hidesEveryPlacesIconSoNoneSitsUnderAQuestPin() {
    // Google's landmark and place icons look like pins, and sit right under the venues' own
    val icons = stylersOf("poi", "labels.icon").map { it.optString("visibility") }

    assertEquals(listOf("off"), icons.filter(String::isNotEmpty))
  }

  @Test
  fun hidesBusinessesSoOnlyQuestVenuesStandOut() {
    // Google's own shop and restaurant icons would compete with the quest pins
    val visibility = stylersOf("poi.business").map { it.optString("visibility") }

    assertEquals(listOf("off"), visibility.filter(String::isNotEmpty))
  }
}
