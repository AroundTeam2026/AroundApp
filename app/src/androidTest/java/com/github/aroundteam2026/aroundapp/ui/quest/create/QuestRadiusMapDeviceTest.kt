// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.MAP_TIMEOUT_MILLIS
import com.github.aroundteam2026.aroundapp.ui.map.awaitGoogleMap
import com.github.aroundteam2026.aroundapp.ui.map.isNear
import com.github.aroundteam2026.aroundapp.ui.map.tapMapUntil
import com.github.aroundteam2026.aroundapp.ui.quest.StillRadiusMap
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The quest form's map in a scrolling screen, as in the form: the venue drags and pinch-zooms it
 * while picking the quest's location, and the quest details' map stays still.
 */
@RunWith(AndroidJUnit4::class)
class QuestRadiusMapDeviceTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val questLocation = Location(46.5220, 6.6330)
  private lateinit var scroll: ScrollState
  private val moves = mutableListOf<Location>()
  // Follows the pin, as the form's view model does
  private var location by mutableStateOf(questLocation)

  /** Shows [map] below some blank space and above more, so the screen around it can scroll. */
  private fun showInScrollingScreen(map: @Composable () -> Unit) {
    composeTestRule.setContent {
      scroll = rememberScrollState()
      AroundAppTheme {
        Column(Modifier.verticalScroll(scroll)) {
          Spacer(Modifier.height(SPACE_ABOVE))
          map()
          Spacer(Modifier.height(SPACE_BELOW))
        }
      }
    }
  }

  private fun showPicker() = showInScrollingScreen {
    QuestRadiusMap(
        location,
        RADIUS_METERS,
        WALK_MINUTES,
        venue = null,
        venueName = VENUE_NAME,
        onMoved = { to ->
          moves.add(to)
          location = to
        },
    )
  }

  private fun showDetailsMap() = showInScrollingScreen {
    StillRadiusMap(
        questLocation,
        RADIUS_METERS,
        DETAILS_MAP_DESCRIPTION,
        Modifier.testTag(C.Tag.QUEST_DETAIL_RADIUS_MAP),
    )
  }

  /** The camera, once the map has framed the circle around the quest's location. */
  private fun GoogleMap.awaitFramed(): CameraPosition {
    composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) {
      composeTestRule.runOnUiThread { cameraPosition.target.isNear(questLocation) }
    }
    return composeTestRule.runOnUiThread { cameraPosition }
  }

  /** Repeats [gesture] on the map tagged [tag] until [done], as the map ignores it until drawn. */
  private fun gestureUntil(tag: String, gesture: () -> Unit, done: () -> Boolean) {
    val deadline = System.currentTimeMillis() + MAP_TIMEOUT_MILLIS
    while (!done() && System.currentTimeMillis() < deadline) {
      composeTestRule.onNodeWithTag(tag).assertExists()
      gesture()
      composeTestRule.waitForIdle()
    }
  }

  @Test
  fun draggingThePickerMovesTheMapRatherThanTheScreen() {
    showPicker()
    val map = composeTestRule.awaitGoogleMap()
    val framed = map.awaitFramed()

    val tag = C.Tag.CREATE_QUEST_RADIUS_MAP
    gestureUntil(
        tag,
        { composeTestRule.onNodeWithTag(tag).performTouchInput { swipeUp() } },
        { composeTestRule.runOnUiThread { map.cameraPosition.target != framed.target } },
    )

    assertFalse(
        "The map didn't move",
        composeTestRule.runOnUiThread { map.cameraPosition.target == framed.target },
    )
    assertEquals("The screen scrolled", 0, composeTestRule.runOnUiThread { scroll.value })
  }

  @Test
  fun pinchingThePickerZoomsTheMap() {
    showPicker()
    val map = composeTestRule.awaitGoogleMap()
    val framed = map.awaitFramed()

    val tag = C.Tag.CREATE_QUEST_RADIUS_MAP
    gestureUntil(
        tag,
        {
          composeTestRule.onNodeWithTag(tag).performTouchInput {
            // Two fingers spread apart from near the centre, as a user zooms in
            val near = Offset(width * PINCH_START, 0f)
            val far = Offset(width * PINCH_END, 0f)
            pinch(center - near, center - far, center + near, center + far)
          }
        },
        { composeTestRule.runOnUiThread { map.cameraPosition.zoom > framed.zoom } },
    )

    assertTrue(
        "The map didn't zoom in",
        composeTestRule.runOnUiThread { map.cameraPosition.zoom > framed.zoom },
    )
    assertEquals("The screen scrolled", 0, composeTestRule.runOnUiThread { scroll.value })
  }

  @Test
  fun movingThePinOnTheMapKeepsTheCameraWhereTheVenueLeftIt() {
    showPicker()
    val map = composeTestRule.awaitGoogleMap()
    val framed = map.awaitFramed()
    composeTestRule.runOnUiThread {
      map.moveCamera(CameraUpdateFactory.zoomTo(framed.zoom + ZOOM_IN))
    }
    val zoomed = composeTestRule.runOnUiThread { map.cameraPosition }

    // Off the pin, which would take the tap
    composeTestRule.tapMapUntil(
        C.Tag.CREATE_QUEST_RADIUS_MAP,
        at = { Offset(width * OFF_PIN, centerY) },
    ) {
      moves.isNotEmpty()
    }
    composeTestRule.waitForIdle()

    assertTrue("The pin didn't move", moves.isNotEmpty())
    assertEquals(
        "The camera framed the circle again",
        zoomed,
        composeTestRule.runOnUiThread { map.cameraPosition },
    )
  }

  @Test
  fun theQuestDetailsMapStaysStillAndLetsTheScreenScroll() {
    showDetailsMap()
    val map = composeTestRule.awaitGoogleMap()
    val framed = map.awaitFramed()
    val settings = composeTestRule.runOnUiThread { map.uiSettings }

    assertFalse("drag", composeTestRule.runOnUiThread { settings.isScrollGesturesEnabled })
    assertFalse("pinch-zoom", composeTestRule.runOnUiThread { settings.isZoomGesturesEnabled })

    composeTestRule.onNodeWithTag(C.Tag.QUEST_DETAIL_RADIUS_MAP).performTouchInput { swipeUp() }
    composeTestRule.waitForIdle()

    assertTrue("The screen didn't scroll", composeTestRule.runOnUiThread { scroll.value } > 0)
    assertEquals(framed, composeTestRule.runOnUiThread { map.cameraPosition })
  }

  private companion object {
    const val VENUE_NAME = "Café du Lac"
    const val DETAILS_MAP_DESCRIPTION = "Within 200 m of Café du Lac"
    const val RADIUS_METERS = 200
    const val WALK_MINUTES = 3
    const val ZOOM_IN = 2f
    const val OFF_PIN = 0.2f
    // How far each finger of the pinch is from the centre, as a fraction of the map's width
    const val PINCH_START = 0.05f
    const val PINCH_END = 0.4f
    val SPACE_ABOVE = 100.dp
    val SPACE_BELOW = 2000.dp
  }
}
