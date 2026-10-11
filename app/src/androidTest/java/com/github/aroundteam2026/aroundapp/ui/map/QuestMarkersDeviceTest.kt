// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.location.LocationRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.marker.MarkerDefaults
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.CameraPositionState
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the quest markers on a device, where the Maps SDK draws them and reports their taps. */
@RunWith(AndroidJUnit4::class)
class QuestMarkersDeviceTest {

  @get:Rule
  val permissions: GrantPermissionRule =
      GrantPermissionRule.grant(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val here = Location(47.3769, 8.5417)
  // Venues of the test's own, in Zürich: the demo data's are in Lausanne
  private val cafe = quest("cafe", "Café Zürich", here)
  // About 3 km south: the café's open card grows upwards and covers most of a small screen's width,
  // so a pin beside the café would be under it
  private val bar = quest("bar", "Bar Zürich", Location(47.3499, 8.5417))
  private val geneva = quest("geneva", "Atelier Genève", Location(46.2044, 6.1432))

  private val viewModel =
      MapViewModel(
          object : LocationRepository {
            override suspend fun currentLocation() = here
          },
          FakeQuestRepository(initialQuests = listOf(cafe, bar, geneva)),
          FakeVenueRepository(),
      )
  private val camera = CameraPositionState()
  // Written on the main thread, read on the test thread
  private val opened = CopyOnWriteArrayList<Pair<String, String>>()
  /** The map's height, or null for the whole screen; tests change it to resize the map. */
  private var mapHeight by mutableStateOf<Dp?>(null)

  private val state
    get() = composeTestRule.runOnUiThread { viewModel.uiState.value }

  @Before
  fun showTheMapAroundHere() {
    composeTestRule.setContent {
      val height = mapHeight
      Box(if (height == null) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(height)) {
        MapScreen(viewModel, camera, onOpenVenue = { id, name -> opened += id to name })
      }
    }
    composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) {
      composeTestRule.runOnUiThread { camera.position.target.isNear(here) && !camera.isMoving }
    }
  }

  private fun awaitPins(vararg venueIds: String) {
    composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) {
      state.pins.map { it.venueId }.toSet() == venueIds.toSet()
    }
  }

  /** Waits for the card of [venueId] to be open, or for none to be when null. */
  private fun awaitSelected(venueId: String?) {
    try {
      composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) { state.selectedVenueId == venueId }
    } catch (e: Throwable) {
      val pins = state.pins.map { it.venueId to screenPoint(it.location) }
      throw AssertionError(
          "Expected $venueId open, got ${state.selectedVenueId}; opened $opened; pins at $pins",
          e,
      )
    }
  }

  private fun screenPoint(location: Location) = composeTestRule.runOnUiThread {
    camera.projection!!.toScreenLocation(location.toLatLng())
  }

  /** Taps the map [dpAbove] above where [location] is on screen. */
  private fun tapAbove(location: Location, dpAbove: Float) {
    val point = screenPoint(location)
    val density = composeTestRule.activity.resources.displayMetrics.density
    composeTestRule.onNodeWithTag(C.Tag.MAP).performTouchInput {
      click(Offset(point.x.toFloat(), point.y - dpAbove * density))
    }
  }

  /** Taps the middle of a venue's round pin, which stands on its location. */
  private fun tapPin(quest: Quest) {
    val dimensions = MarkerDefaults.dimensions
    tapAbove(quest.location, (dimensions.pointerHeight + dimensions.pinDiameter / 2).value)
  }

  /** Taps an open card just above its pointer. */
  private fun tapCard(quest: Quest) {
    tapAbove(quest.location, (MarkerDefaults.dimensions.pointerHeight.value + 16f))
  }

  /**
   * Taps [quest]'s pin until its card opens. The view model lists a pin before the map has drawn
   * its marker, and a tap until then lands on the bare map; so the test taps again, after the pause
   * that keeps two taps from reading as a double-tap zoom.
   */
  private fun openCard(quest: Quest) {
    val deadline = System.currentTimeMillis() + MAP_TIMEOUT_MILLIS
    while (state.selectedVenueId != quest.venueId && System.currentTimeMillis() < deadline) {
      tapPin(quest)
      pauseBeforeTappingAgain { state.selectedVenueId == quest.venueId }
    }
    awaitSelected(quest.venueId)
    // The next tap may be on the same spot, like the card's
    pauseBeforeTappingAgain()
  }

  /**
   * Waits out the double-tap timeout, or until [done]: the Maps SDK turns two taps on one spot
   * within it into a zoom, and never hands the second to the marker. Frames keep coming meanwhile,
   * so the map redraws.
   */
  private fun pauseBeforeTappingAgain(done: () -> Boolean = { false }) {
    val end = System.currentTimeMillis() + ViewConfiguration.getDoubleTapTimeout() + 200
    composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) { done() || System.currentTimeMillis() >= end }
  }

  @Test
  fun onlyVenuesOnScreenGetAPin() {
    // Geneva's venue is far out of the 5 km around here
    awaitPins("cafe", "bar")
  }

  @Test
  fun tappingAPinOpensItsCard() {
    openCard(cafe)

    assertEquals("cafe", state.selectedVenueId)
  }

  @Test
  fun tappingAPinLeavesTheCameraWhereItIs() {
    // The Maps SDK moves the camera to a tapped marker unless the tap is handled; the bar is 3 km
    // from where the camera is
    openCard(bar)
    composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) {
      composeTestRule.runOnUiThread { !camera.isMoving }
    }

    val target = composeTestRule.runOnUiThread { camera.position.target }
    assertTrue("The camera moved to $target", target.isNear(here))
  }

  @Test
  fun tappingAnotherPinSwitchesTheCard() {
    openCard(cafe)

    openCard(bar)

    assertEquals("bar", state.selectedVenueId)
  }

  @Test
  fun tappingTheCardOpensTheVenuePage() {
    openCard(cafe)

    tapCard(cafe)

    composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) { opened.isNotEmpty() }
    assertEquals(listOf("cafe" to "Café Zürich"), opened)
  }

  @Test
  fun tappingAPinDoesNotOpenTheVenuePage() {
    openCard(cafe)
    composeTestRule.waitForIdle()

    assertTrue(opened.isEmpty())
  }

  @Test
  fun tappingTheMapClosesTheCard() {
    openCard(cafe)

    // About 3 km south-west of here: below the card, beside the bar, clear of the map's buttons
    tapAbove(Location(47.3500, 8.5000), 0f)

    awaitSelected(null)
  }

  @Test
  fun panningToAnotherCityShowsItsVenues() {
    composeTestRule.runOnUiThread {
      camera.move(CameraUpdateFactory.newLatLngZoom(geneva.location.toLatLng(), 13f))
    }

    awaitPins("geneva")
  }

  @Test
  fun growingTheMapShowsTheVenuesThatCameIntoView() {
    // As in split-screen: the map grows but its camera stays put, so only its size changes.
    // At this zoom a short map shows about 1 km north and south of the café, a tall one about 5
    composeTestRule.runOnUiThread { mapHeight = 120.dp }
    composeTestRule.waitForIdle()
    composeTestRule.runOnUiThread {
      camera.move(CameraUpdateFactory.newLatLngZoom(here.toLatLng(), 12.5f))
    }
    awaitPins("cafe")

    composeTestRule.runOnUiThread { mapHeight = null }

    awaitPins("cafe", "bar")
  }

  private fun quest(venueId: String, venueName: String, location: Location) =
      Quest(
          id = "$venueId-quest",
          venueId = venueId,
          venueName = venueName,
          location = location,
          radiusMeters = 50,
          title = "Order the secret menu",
          description = "Description",
          requirements = "Requirements",
          proofType = ProofType.PHOTO,
          reward = Reward.Other("Free coffee"),
          status = QuestStatus.ACTIVE,
          createdAt = 1_000L,
          updatedAt = 1_000L,
      )
}
