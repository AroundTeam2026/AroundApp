// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.quest.ScriptedQuestRepository
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests the nearby quest overlay of [MapViewModel]: when it opens, and how it and a card share. */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelOverlayTest {

  private val dispatcher = StandardTestDispatcher()

  private val cafe =
      testQuest(
          id = "cafe-1",
          venueId = "cafe",
          venueName = "Café Lumen",
          location = Location(46.5220, 6.6330),
      )
  private val lausanne = GeoBounds(south = 46.50, west = 6.60, north = 46.55, east = 6.66)
  private val quests = MutableStateFlow(listOf(cafe))
  // The venue moved, off screen, after its quest copied its location: its pin follows the venue
  private val venueLocation = Location(46.6000, 6.7000)
  /**
   * The explorer, away from the map's default centre but near the venue; tests hold their position
   * back.
   */
  private val locating =
      FakeLocationRepository(Location(46.5800, 6.6800)).apply { gate = CompletableDeferred() }

  private val viewModel by lazy {
    MapViewModel(
        locating,
        ScriptedQuestRepository.of(quests),
        FakeVenueRepository(listOf(testVenue(id = "cafe", location = venueLocation))),
        clock = { 1_000L },
    )
  }

  private val state
    get() = viewModel.uiState.value

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) =
      runTest(dispatcher) {
        viewModel.onVisibleAreaChanged(lausanne)
        runCurrent()
        body()
      }

  @Test
  fun theOverlayOpensOnlyWhenAsked() = test {
    assertFalse("closed when the map opens", state.showsNearby)

    viewModel.onNearbyOpen()
    runCurrent()

    assertTrue(state.showsNearby)
  }

  @Test
  fun closingTheOverlayHidesIt() = test {
    viewModel.onNearbyOpen()
    runCurrent()

    viewModel.onNearbyClose()
    runCurrent()

    assertFalse(state.showsNearby)
  }

  @Test
  fun openingTheOverlayClosesTheOpenCard() = test {
    viewModel.onPinClick("cafe")
    runCurrent()
    assertEquals("cafe", state.selectedVenueId)

    viewModel.onNearbyOpen()
    runCurrent()

    assertNull(state.selectedVenueId)
  }

  @Test
  fun openingACardClosesTheOverlay() = test {
    viewModel.onNearbyOpen()
    runCurrent()

    viewModel.onPinClick("cafe")
    runCurrent()

    assertFalse(state.showsNearby)
    assertEquals("cafe", state.selectedVenueId)
  }

  @Test
  fun tappingTheMapLeavesTheOverlayOpen() = test {
    // The map stays usable behind the overlay; only a card closes on a tap beside the pins
    viewModel.onNearbyOpen()
    runCurrent()

    viewModel.onMapClick()
    runCurrent()

    assertTrue(state.showsNearby)
  }

  @Test
  fun tappingAPageOpensItsVenuesCardInPlaceOfTheOverlay() = test {
    viewModel.onNearbyOpen()
    runCurrent()
    assertTrue("the venue is off screen", state.pins.isEmpty())

    viewModel.onNearbyQuestClick(state.nearby.single())
    runCurrent()

    assertFalse(state.showsNearby)
    assertEquals("cafe", state.selectedVenueId)
    // The open venue keeps its pin until the camera gets there
    assertEquals(listOf("cafe"), state.pins.map { it.venueId })
  }

  @Test
  fun tappingAPageFramesItsVenueWhereItsPinIs() = test {
    viewModel.onNearbyOpen()
    runCurrent()

    viewModel.onNearbyQuestClick(state.nearby.single())
    runCurrent()

    assertEquals(
        venueLocation.boundsWithin(MapViewModel.VENUE_FRAME_RADIUS_METERS),
        state.areaToFrame,
    )
  }

  @Test
  fun aPageWhoseVenueLostItsQuestsChangesNothing() = test {
    viewModel.onNearbyOpen()
    runCurrent()
    val page = state.nearby.single()
    quests.value = emptyList()
    runCurrent()
    val before = state

    viewModel.onNearbyQuestClick(page)
    runCurrent()

    assertEquals(before, state)
    assertTrue("the overlay stays open", state.showsNearby)
  }

  @Test
  fun aPageTappedBeforeTheExplorerIsLocatedKeepsItsVenueFramed() = pickBeforeLocated {
    viewModel.onNearbyQuestClick(state.nearby.single())
  }

  @Test
  fun aPinTappedBeforeTheExplorerIsLocatedKeepsTheCameraWhereItIs() = pickBeforeLocated {
    viewModel.onPinClick("cafe")
  }

  /**
   * Picks a venue with [pick] while the explorer's position is on its way, then lets it arrive: the
   * camera must keep the picked venue in view rather than fly to the explorer.
   */
  private fun pickBeforeLocated(pick: () -> Unit) = test {
    viewModel.onLocationPermissionResult(granted = true)
    viewModel.onNearbyOpen()
    runCurrent()
    pick()
    runCurrent()
    val framed = state.areaToFrame

    locating.gate!!.complete(Unit)
    runCurrent()

    assertEquals(framed, state.areaToFrame)
    assertEquals("cafe", state.selectedVenueId)
    // The explorer is located all the same, so the list measures from them
    assertNotNull(state.nearby.single().distanceMeters)
  }
}
