// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
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
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
import com.github.aroundteam2026.aroundapp.model.venue.Venue
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
  /** The venues [quest] placed, by id. */
  private val venues = mutableMapOf<String, Venue>()
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
          FakeVenueRepository(venues.values.toList()),
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

  /** Where on the map [dpAbove] above [location] is. */
  private fun pointAbove(location: Location, dpAbove: Float): Offset {
    val point = screenPoint(location)
    val density = composeTestRule.activity.resources.displayMetrics.density
    return Offset(point.x.toFloat(), point.y - dpAbove * density)
  }

  /** Taps the map [dpAbove] above where [location] is on screen. */
  private fun tapAbove(location: Location, dpAbove: Float) {
    val point = pointAbove(location, dpAbove)
    composeTestRule.onNodeWithTag(C.Tag.MAP).performTouchInput { click(point) }
  }

  /** Taps an open card just above its pointer. */
  private fun tapCard(quest: Quest) {
    tapAbove(quest.location(), (MarkerDefaults.dimensions.pointerHeight.value + 16f))
  }

  /** Taps the middle of [quest]'s round pin, which stands on its location, until its card opens. */
  private fun openCard(quest: Quest) {
    val dimensions = MarkerDefaults.dimensions
    val pin =
        pointAbove(quest.location(), (dimensions.pointerHeight + dimensions.pinDiameter / 2).value)
    composeTestRule.tapMapUntil(C.Tag.MAP, { pin }) { state.selectedVenueId == quest.venueId }
    awaitSelected(quest.venueId)
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
      camera.move(CameraUpdateFactory.newLatLngZoom(geneva.location().toLatLng(), 13f))
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

  /** Where [Quest]'s venue, and so its pin, is. */
  private fun Quest.location() = checkNotNull(venues.getValue(venueId).location)

  /** A quest of a venue named [venueName] at [location], which it adds to [venues]. */
  private fun quest(venueId: String, venueName: String, location: Location): Quest {
    venues[venueId] =
        Venue(venueId, venueName, location, radiusMeters = 50, address = null, createdAt = 0L)
    return Quest(
        id = "$venueId-quest",
        venueId = venueId,
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
}
