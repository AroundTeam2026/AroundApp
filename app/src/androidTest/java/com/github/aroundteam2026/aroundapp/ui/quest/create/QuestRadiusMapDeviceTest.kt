// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
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
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.MAP_TIMEOUT_MILLIS
import com.github.aroundteam2026.aroundapp.ui.map.awaitGoogleMap
import com.github.aroundteam2026.aroundapp.ui.map.isNear
import com.github.aroundteam2026.aroundapp.ui.map.tapMapUntil
import com.github.aroundteam2026.aroundapp.ui.quest.RadiusMap
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
  // An address picked in the form, about a kilometre away
  private val picked = Location(46.5300, 6.6400)
  // The venue itself, about a kilometre from the quest
  private val venueLocation = Location(46.5300, 6.6400)
  private lateinit var scroll: ScrollState
  private val moves = mutableListOf<Location>()
  // Follows the pin, as the form's view model does
  private var location by mutableStateOf(questLocation)
  private var radiusMeters by mutableStateOf(RADIUS_METERS)

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

  /** Shows the form's map, with the venue's own pin at [venue] for a quest away from it. */
  private fun showPicker(venue: Location? = null) = showInScrollingScreen {
    QuestRadiusMap(
        location,
        radiusMeters,
        WALK_MINUTES,
        venue = venue,
        venueName = VENUE_NAME,
        onMoved = { to ->
          moves.add(to)
          location = to
        },
    )
  }

  private fun showDetailsMap() = showInScrollingScreen {
    RadiusMap(
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

  /**
   * Spreads two fingers apart diagonally from near the centre of the node tagged [tag], as a user
   * zooms in. The first finger moves up, which a scrolling screen would take to scroll itself. The
   * Maps SDK doesn't zoom on a pinch from `performTouchInput`, so it goes through the system's
   * input, as a real one does.
   */
  private fun pinchOpen(tag: String) {
    val bounds = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    val root = IntArray(2)
    composeTestRule.runOnUiThread {
      composeTestRule.activity.window.decorView.getLocationOnScreen(root)
    }
    val center = Offset(root[0] + bounds.center.x, root[1] + bounds.center.y)
    val near = Offset(bounds.width, bounds.height) * PINCH_START
    val far = Offset(bounds.width, bounds.height) * PINCH_END
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val start = SystemClock.uptimeMillis()
    // Each finger at [spread] from the centre, both fingers when [both]
    fun event(time: Long, action: Int, spread: Offset, both: Boolean): MotionEvent {
      val fingers = if (both) 2 else 1
      val properties =
          Array(fingers) { finger ->
            MotionEvent.PointerProperties().apply {
              id = finger
              toolType = MotionEvent.TOOL_TYPE_FINGER
            }
          }
      val coords =
          Array(fingers) { finger ->
            MotionEvent.PointerCoords().apply {
              val at = if (finger == 0) center - spread else center + spread
              x = at.x
              y = at.y
              pressure = 1f
              size = 1f
            }
          }
      return MotionEvent.obtain(
          start,
          time,
          action,
          fingers,
          properties,
          coords,
          0,
          0,
          1f,
          1f,
          0,
          0,
          InputDevice.SOURCE_TOUCHSCREEN,
          0,
      )
    }
    val second = 1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT
    val events = buildList {
      add(event(start, MotionEvent.ACTION_DOWN, near, both = false))
      add(event(start, MotionEvent.ACTION_POINTER_DOWN or second, near, both = true))
      for (step in 1..PINCH_STEPS) {
        val time = start + PINCH_MILLIS * step / PINCH_STEPS
        val spread = near + (far - near) * (step.toFloat() / PINCH_STEPS)
        add(event(time, MotionEvent.ACTION_MOVE, spread, both = true))
      }
      val end = start + PINCH_MILLIS
      add(event(end, MotionEvent.ACTION_POINTER_UP or second, far, both = true))
      add(event(end, MotionEvent.ACTION_UP, far, both = false))
    }
    events.forEach {
      automation.injectInputEvent(it, true)
      it.recycle()
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
  fun pinchingThePickerMovesTheMapRatherThanTheScreen() {
    showPicker()
    val map = composeTestRule.awaitGoogleMap()
    val framed = map.awaitFramed()

    val tag = C.Tag.CREATE_QUEST_RADIUS_MAP
    gestureUntil(
        tag,
        { pinchOpen(tag) },
        { composeTestRule.runOnUiThread { map.cameraPosition != framed } },
    )

    assertFalse(
        "The map didn't move",
        composeTestRule.runOnUiThread { map.cameraPosition == framed },
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
  fun aNewRadiusOrAMoveFromElsewhereFramesThePinWhereItIs() {
    showPicker()
    val map = composeTestRule.awaitGoogleMap()
    val framed = map.awaitFramed()
    // Off the pin, which would take the tap; far enough from it to tell the two apart
    composeTestRule.tapMapUntil(
        C.Tag.CREATE_QUEST_RADIUS_MAP,
        at = { Offset(width * OFF_PIN, centerY) },
    ) {
      moves.isNotEmpty()
    }
    val tapped = moves.last()
    assertFalse("The pin didn't move away", framed.target.isNear(tapped))

    composeTestRule.runOnUiThread { radiusMeters = LARGER_RADIUS_METERS }
    composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) {
      composeTestRule.runOnUiThread { map.cameraPosition.target.isNear(tapped) }
    }

    composeTestRule.runOnUiThread { location = picked }
    composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) {
      composeTestRule.runOnUiThread { map.cameraPosition.target.isNear(picked) }
    }
  }

  @Test
  fun aQuestAwayFromItsVenueFramesItsCircleAndDrawsTheVenuesPin() {
    showPicker(venue = venueLocation)
    val map = composeTestRule.awaitGoogleMap()

    // awaitFramed only returns once the camera is on the quest's circle, so a map that framed
    // the venue's pin instead would time out here. The Maps SDK can't list markers, so the pin
    // itself is drawn but not checked.
    map.awaitFramed()
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
    const val LARGER_RADIUS_METERS = 400
    const val WALK_MINUTES = 3
    const val ZOOM_IN = 2f
    const val OFF_PIN = 0.2f
    // How far each finger of the pinch is from the centre, as a fraction of the map's width
    const val PINCH_START = 0.1f
    const val PINCH_END = 0.45f
    const val PINCH_STEPS = 30
    const val PINCH_MILLIS = 600L
    val SPACE_ABOVE = 100.dp
    val SPACE_BELOW = 2000.dp
  }
}
