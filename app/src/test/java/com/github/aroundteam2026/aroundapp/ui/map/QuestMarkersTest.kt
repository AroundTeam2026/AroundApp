// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.content.Context
import android.graphics.Point
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.ui.map.marker.MarkerDefaults
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Tests what the map draws for each pin. The map itself only runs on a device, so these check what
 * [QuestMarkers] hands it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuestMarkersTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val pin =
      VenuePin(
          key = "cafe",
          venueId = "cafe",
          venueName = "Café Lumen",
          location = Location(46.5220, 6.6330),
          icon = VenueAvatar.QuestFlag,
          avatar = VenueAvatar.Initials("CL"),
          featuredQuest = testQuest(title = "Find the hidden fox"),
          otherQuestCount = 0,
          distanceMeters = null,
          areaRadiusMeters = 120,
      )

  private val neighbour = pin.copy(key = "bar", venueId = "bar")

  private fun hitTest(): QuestMarkerHitTest {
    lateinit var hits: QuestMarkerHitTest
    composeTestRule.setContent {
      hits = QuestMarkerHitTest(MarkerDefaults.style(), Density(1f), LayoutDirection.Ltr)
    }
    composeTestRule.waitForIdle()
    return hits
  }

  private fun project(candidate: VenuePin): Point =
      if (candidate == pin) Point(0, 0) else Point(NEIGHBOUR_ANCHOR_X, NEIGHBOUR_ANCHOR_Y)

  @Test
  fun closeFlagsUseThePaintedShapeAndTheVisibleStackOrder() {
    val hits = hitTest()
    val pins = listOf(pin, neighbour)

    // The neighbour's flag lies in the first bitmap's clear corner beside its pointer.
    assertEquals(neighbour, hits.find(NEIGHBOUR_FLAG, pins, null, project = ::project))
    // Here both pin bodies overlap, so the top pin owns the visible pixel.
    assertEquals(pin, hits.find(OVERLAPPING_FLAG_BODIES, pins, null, project = ::project))
    assertNull(hits.find(CLEAR_CORNER, pins, null, project = ::project))
  }

  @Test
  fun anOpenCardCoversThePinsOnlyWhereItsShapeIsPainted() {
    val hits = hitTest()
    val pins = listOf(pin, neighbour)
    hits.recordCardSize(pin.key, OPEN_CARD_SIZE)

    assertEquals(pin, hits.find(FLAG_UNDER_CARD_BODY, pins, pin.key, project = ::project))
    assertEquals(neighbour, hits.find(NEIGHBOUR_FLAG, pins, pin.key, project = ::project))
  }

  @Test
  fun activatingAMarkerWithoutATouchKeepsTheSdkSelection() {
    val hits = hitTest()
    val pins = listOf(pin, neighbour)

    assertEquals(neighbour, hits.find(null, pins, null, neighbour, ::project))
    assertNull(hits.find(null, pins, pin.key, project = ::project))
  }

  @Test
  fun theSdkSelectionStillWorksBeforeTheMapOrCardCanBeHitTested() {
    val hits = hitTest()
    val pins = listOf(pin, neighbour)

    assertEquals(pin, hits.find(NEIGHBOUR_FLAG, pins, null, pin, project = null))
    assertEquals(pin, hits.find(NEIGHBOUR_FLAG, pins, pin.key, pin, ::project))
    hits.recordCardSize(pin.key, OPEN_CARD_SIZE)
    assertEquals(neighbour, hits.find(NEIGHBOUR_FLAG, pins, pin.key, pin, ::project))
  }

  @Test
  fun theAreaIsACircleAtTheVenuesRealRadius() {
    lateinit var area: PinArea
    composeTestRule.setContent { area = pin.area(MarkerDefaults.style()) }

    assertEquals(LatLng(46.5220, 6.6330), area.center)
    assertEquals(120.0, area.radiusMeters, 0.0)
  }

  @Test
  fun theAreaTakesTheStylesColour() {
    lateinit var area: PinArea
    composeTestRule.setContent {
      val colors = MarkerDefaults.colors().copy(area = Color.Magenta)
      area = pin.area(MarkerDefaults.style(colors = colors))
    }

    assertEquals(Color.Magenta, area.fill)
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

  private companion object {
    // Offsets in pixels at density 1, relative to the café pointer at the origin.
    const val NEIGHBOUR_ANCHOR_X = 18
    const val NEIGHBOUR_ANCHOR_Y = 25
    val NEIGHBOUR_FLAG =
        Offset(
            NEIGHBOUR_ANCHOR_X.toFloat(),
            NEIGHBOUR_ANCHOR_Y -
                (MarkerDefaults.dimensions.pinDiameter / 2 +
                        MarkerDefaults.dimensions.pointerHeight)
                    .value,
        )
    val OVERLAPPING_FLAG_BODIES = Offset(10f, -20f)
    val CLEAR_CORNER = Offset(-20f, -5f)
    val FLAG_UNDER_CARD_BODY = Offset(18f, -14f)
    val OPEN_CARD_SIZE = IntSize(MarkerDefaults.dimensions.cardWidth.value.toInt(), 100)
  }
}
