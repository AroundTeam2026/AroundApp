// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.ui.map.marker.MarkerDefaults
import com.github.aroundteam2026.aroundapp.ui.map.marker.VenueAvatar
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests what the map draws for each pin. The map itself only runs on a device, so these check what
 * [QuestMarkers] hands it.
 */
@RunWith(AndroidJUnit4::class)
class QuestMarkersTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val pin =
      VenuePin(
          venueId = "cafe",
          venueName = "Café Lumen",
          location = Location(46.5220, 6.6330),
          icon = VenueAvatar.QuestFlag,
          featuredQuest = testQuest(title = "Find the hidden fox"),
          otherQuestCount = 0,
          areaRadiusMeters = 120,
      )

  @Test
  fun theAreaIsACircleAtTheVenuesRealRadius() {
    val area = pin.area(MarkerDefaults.style())

    assertEquals(LatLng(46.5220, 6.6330), area.center)
    assertEquals(120.0, area.radiusMeters, 0.0)
  }

  @Test
  fun theAreaTakesTheStylesColour() {
    val colors = MarkerDefaults.colors().copy(area = Color.Magenta)

    assertEquals(Color.Magenta, pin.area(MarkerDefaults.style(colors = colors)).fill)
  }

  @Test
  fun aPinTellsScreenReadersItsVenueAndQuest() {
    var description = ""
    composeTestRule.setContent { description = pin.description() }
    composeTestRule.waitForIdle()

    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals(
        context.getString(R.string.map_pin_description, "Café Lumen", "Find the hidden fox"),
        description,
    )
  }
}
