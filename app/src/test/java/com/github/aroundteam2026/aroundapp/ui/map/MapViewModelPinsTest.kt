// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.distanceTo
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.quest.ScriptedQuestRepository
import com.github.aroundteam2026.aroundapp.model.rewardExpiringAt
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests the quest pins of [MapViewModel]: which venues get one, and which one is open. */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelPinsTest {

  private val dispatcher = StandardTestDispatcher()
  /** The test's clock: [START] plus the virtual time the test has advanced. */
  private val clock = { START + dispatcher.scheduler.currentTime }

  private val cafeLocation = Location(46.5220, 6.6330)
  private val barLocation = Location(46.5235, 6.6365)
  private val genevaLocation = Location(46.2044, 6.1432)
  private val museumLocation = Location(46.5150, 6.6200)

  /**
   * What the explorer sees: central Lausanne, holding the café, the bar and the museum but not
   * Geneva.
   */
  private val lausanneView = GeoBounds(south = 46.50, west = 6.60, north = 46.55, east = 6.66)
  private val genevaView = GeoBounds(south = 46.19, west = 6.12, north = 46.22, east = 6.16)

  private val cafeQuest =
      testQuest(id = "cafe-1", venueId = "cafe", venueName = "Café Lumen", location = cafeLocation)
  private val barQuest =
      testQuest(id = "bar-1", venueId = "bar", venueName = "Bar Nocturne", location = barLocation)
  private val genevaQuest =
      testQuest(
          id = "geneva-1",
          venueId = "geneva",
          venueName = "Atelier",
          location = genevaLocation,
      )

  private val quests = MutableStateFlow(listOf(cafeQuest, barQuest, genevaQuest))
  private var questRepository = ScriptedQuestRepository.of(quests)
  private var venueRepository: VenueRepository = FakeVenueRepository()
  private val locationRepository = FakeLocationRepository(null)

  private val viewModel by lazy {
    MapViewModel(locationRepository, questRepository, venueRepository, clock)
  }

  private val state
    get() = viewModel.uiState.value

  /** The pin whose card is open, or null. */
  private val selectedPin
    get() = state.pins.find { it.venueId == state.selectedVenueId }

  private val pinIds
    get() = state.pins.map { it.venueId }.toSet()

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  /** Starts the view model and shows it [area], as the map does once it has drawn. */
  private fun TestScope.showing(area: GeoBounds = lausanneView) {
    viewModel.onVisibleAreaChanged(area)
    runCurrent()
  }

  @Test
  fun noPinsUntilTheMapSaysWhatItShows() = test {
    viewModel
    runCurrent()

    assertEquals(emptyList<VenuePin>(), state.pins)
  }

  @Test
  fun onlyVenuesInViewGetAPin() = test {
    showing(lausanneView)

    assertEquals(setOf("cafe", "bar"), pinIds)
  }

  @Test
  fun panningShowsTheVenuesThatCameIntoView() = test {
    showing(lausanneView)

    showing(genevaView)

    assertEquals(setOf("geneva"), pinIds)
  }

  @Test
  fun aPinCarriesItsVenuesQuest() = test {
    showing()

    val cafe = state.pins.single { it.venueId == "cafe" }
    assertEquals("Café Lumen", cafe.venueName)
    assertEquals(cafeLocation, cafe.location)
    assertEquals(cafeQuest, cafe.featuredQuest)
    assertEquals(0, cafe.otherQuestCount)
  }

  @Test
  fun aNewQuestShowsUpWithoutMovingTheMap() = test {
    showing()

    quests.value += testQuest(id = "cafe-2", venueId = "cafe", location = cafeLocation)
    runCurrent()

    assertEquals(1, state.pins.single { it.venueId == "cafe" }.otherQuestCount)
  }

  @Test
  fun aVenueWhoseLastQuestEndsLosesItsPin() = test {
    showing()

    quests.value = listOf(barQuest, cafeQuest.copy(status = QuestStatus.ARCHIVED))
    runCurrent()

    assertEquals(setOf("bar"), pinIds)
  }

  @Test
  fun aRewardThatExpiresWhileTheMapIsOpenDropsItsQuestThen() = test {
    val expiring = cafeQuest.copy(id = "cafe-expiring", reward = rewardExpiringAt(START + 60_000))
    quests.value = listOf(cafeQuest, expiring, barQuest)
    showing()
    assertEquals(1, state.pins.single { it.venueId == "cafe" }.otherQuestCount)

    advanceTimeBy(59_999)
    runCurrent()
    assertEquals(1, state.pins.single { it.venueId == "cafe" }.otherQuestCount)

    advanceTimeBy(1)
    runCurrent()
    assertEquals(0, state.pins.single { it.venueId == "cafe" }.otherQuestCount)
  }

  @Test
  fun aVenueWhoseOnlyRewardExpiresWhileTheMapIsOpenLosesItsPin() = test {
    quests.value = listOf(cafeQuest.copy(reward = rewardExpiringAt(START + 1_000)), barQuest)
    showing()
    assertEquals(setOf("cafe", "bar"), pinIds)

    advanceTimeBy(1_000)
    runCurrent()

    assertEquals(setOf("bar"), pinIds)
  }

  @Test
  fun rewardsExpiringOneAfterAnotherAreEachDroppedOnTime() = test {
    // After the first expiry, the map must wait for the next one too
    quests.value =
        listOf(
            cafeQuest.copy(reward = rewardExpiringAt(START + 1_000)),
            barQuest.copy(reward = rewardExpiringAt(START + 5_000)),
        )
    showing()

    advanceTimeBy(1_000)
    runCurrent()
    assertEquals(setOf("bar"), pinIds)

    advanceTimeBy(4_000)
    runCurrent()
    assertEquals(emptySet<String>(), pinIds)
  }

  @Test
  fun theVenuesPickComesFromTheVenueRepository() = test {
    val newer = cafeQuest.copy(id = "cafe-newer", createdAt = cafeQuest.createdAt + 1)
    quests.value = listOf(cafeQuest, newer)
    venueRepository =
        FakeVenueRepository(
            listOf(testVenue(id = "cafe", location = cafeLocation, featuredQuestId = "cafe-1"))
        )
    showing()

    assertEquals("cafe-1", state.pins.single().featuredQuest.id)
  }

  @Test
  fun aVenueThatMovesTakesItsPinWithIt() = test {
    val venues = FakeVenueRepository(listOf(testVenue(id = "cafe", location = cafeLocation)))
    venueRepository = venues
    showing()

    venues.setArea("cafe", Location(46.53, 6.64), radiusMeters = 50)
    runCurrent()

    assertEquals(Location(46.53, 6.64), state.pins.single { it.venueId == "cafe" }.location)
  }

  @Test
  fun onlyTheVenuesOfTheQuestsAreAskedFor() = test {
    // Firestore reads venues by id; reading every venue would cost a read each
    val asked = mutableListOf<Set<String>>()
    venueRepository = RecordingVenueRepository(asked)
    showing()

    assertEquals(setOf("cafe", "bar", "geneva"), asked.last())
  }

  @Test
  fun aQuestChangeAtTheSameVenuesKeepsListeningToThem() = test {
    // On Firestore, asking again would restart the venue listeners and pay for their reads again
    val asked = mutableListOf<Set<String>>()
    venueRepository = RecordingVenueRepository(asked)
    showing()

    quests.value += testQuest(id = "cafe-2", venueId = "cafe", location = cafeLocation)
    runCurrent()
    quests.value = quests.value.filterNot { it.id == "bar-1" } + barQuest.copy(title = "Renamed")
    runCurrent()

    assertEquals(listOf(setOf("cafe", "bar", "geneva")), asked)
    // The pins still follow the quests
    assertEquals(1, state.pins.single { it.venueId == "cafe" }.otherQuestCount)
    assertEquals("Renamed", state.pins.single { it.venueId == "bar" }.featuredQuest.title)
  }

  @Test
  fun aQuestAtAnotherVenueAsksForTheNewSetOfVenues() = test {
    val asked = mutableListOf<Set<String>>()
    venueRepository = RecordingVenueRepository(asked)
    showing()

    quests.value += testQuest(id = "museum-1", venueId = "museum", location = museumLocation)
    runCurrent()
    quests.value = quests.value.filterNot { it.venueId == "geneva" }
    runCurrent()

    assertEquals(
        listOf(
            setOf("cafe", "bar", "geneva"),
            setOf("cafe", "bar", "geneva", "museum"),
            setOf("cafe", "bar", "museum"),
        ),
        asked,
    )
    assertEquals(setOf("cafe", "bar", "museum"), pinIds)
  }

  @Test
  fun theQuestsAreListenedToOnce() = test {
    // Listening to them once for the pins and again for their venues would double the reads
    var listeners = 0
    questRepository = ScriptedQuestRepository(activeQuests = quests.onStart { listeners++ })
    showing()

    quests.value += testQuest(id = "museum-1", venueId = "museum", location = museumLocation)
    runCurrent()

    assertEquals(1, listeners)
  }

  @Test
  fun aQuestArrivingWhileARewardIsAboutToExpireShowsAtOnceAndTheRewardStillExpires() = test {
    quests.value = listOf(cafeQuest.copy(reward = rewardExpiringAt(START + 1_000)), barQuest)
    showing()

    advanceTimeBy(400)
    runCurrent()
    quests.value += testQuest(id = "museum-1", venueId = "museum", location = museumLocation)
    runCurrent()
    assertEquals(setOf("cafe", "bar", "museum"), pinIds)

    advanceTimeBy(599)
    runCurrent()
    assertEquals(setOf("cafe", "bar", "museum"), pinIds)

    advanceTimeBy(1)
    runCurrent()
    assertEquals(setOf("bar", "museum"), pinIds)
  }

  @Test
  fun aQuestArrivingWithASoonerExpiryIsDroppedOnTime() = test {
    // The map was waiting for the bar's reward; the new one ends first
    quests.value = listOf(cafeQuest, barQuest.copy(reward = rewardExpiringAt(START + 5_000)))
    showing()

    advanceTimeBy(400)
    runCurrent()
    quests.value +=
        testQuest(
            id = "museum-1",
            venueId = "museum",
            location = museumLocation,
            reward = rewardExpiringAt(START + 1_000),
        )
    runCurrent()
    assertEquals(setOf("cafe", "bar", "museum"), pinIds)

    advanceTimeBy(600)
    runCurrent()
    assertEquals(setOf("cafe", "bar"), pinIds)

    advanceTimeBy(4_000)
    runCurrent()
    assertEquals(setOf("cafe"), pinIds)
  }

  @Test
  fun failingQuestsShowNoPinsButTheMapStillWorks() = test {
    questRepository = ScriptedQuestRepository(activeQuests = flow { throw IOException("offline") })
    viewModel.onLocationPermissionResult(granted = true)
    showing()

    assertEquals(emptyList<VenuePin>(), state.pins)
    assertTrue(state.showsUserLocation)
  }

  @Test
  fun failingVenuesStillShowEveryPinWithItsNewestQuest() = test {
    val newer = cafeQuest.copy(id = "cafe-newer", createdAt = cafeQuest.createdAt + 1)
    quests.value = listOf(cafeQuest, newer, barQuest)
    venueRepository = RecordingVenueRepository(mutableListOf(), fail = true)
    showing()

    assertEquals(setOf("cafe", "bar"), pinIds)
    assertEquals("cafe-newer", state.pins.single { it.venueId == "cafe" }.featuredQuest.id)
  }

  @Test
  fun pinsHaveNoDistanceUntilTheExplorerIsLocated() = test {
    locationRepository.location = Location(46.5197, 6.6323)
    showing()

    assertTrue(state.pins.isNotEmpty())
    assertTrue(state.pins.all { it.distanceMeters == null })
  }

  @Test
  fun locatingTheExplorerGivesEachPinItsDistance() = test {
    val here = Location(46.5197, 6.6323)
    locationRepository.location = here
    showing()

    viewModel.onLocationPermissionResult(granted = true)
    runCurrent()

    val cafe = state.pins.single { it.venueId == "cafe" }
    assertEquals(here.distanceTo(cafeLocation), cafe.distanceMeters!!, 1e-6)
  }

  @Test
  fun anUnknownPositionLeavesPinsWithoutADistance() = test {
    showing()

    viewModel.onLocationPermissionResult(granted = true)
    runCurrent()

    assertTrue(state.pins.all { it.distanceMeters == null })
  }

  @Test
  fun nothingIsSelectedAtFirst() = test {
    showing()

    assertNull(state.selectedVenueId)
    assertNull(selectedPin)
  }

  @Test
  fun tappingAPinOpensItsCard() = test {
    showing()

    viewModel.onPinClick("cafe")
    runCurrent()

    assertEquals("cafe", state.selectedVenueId)
    assertEquals("cafe", selectedPin?.venueId)
  }

  @Test
  fun tappingAnotherPinSwitchesTheCard() = test {
    // Only one card is open at a time
    showing()
    viewModel.onPinClick("cafe")
    runCurrent()

    viewModel.onPinClick("bar")
    runCurrent()

    assertEquals("bar", state.selectedVenueId)
  }

  @Test
  fun tappingTheMapClosesTheCard() = test {
    showing()
    viewModel.onPinClick("cafe")
    runCurrent()

    viewModel.onMapClick()
    runCurrent()

    assertNull(state.selectedVenueId)
    assertEquals(setOf("cafe", "bar"), pinIds)
  }

  @Test
  fun tappingAPinThatIsGoneChangesNothing() = test {
    // A tap can arrive just after the pin's last quest ended, before the map drops the pin
    showing()
    viewModel.onPinClick("bar")
    runCurrent()
    quests.value = listOf(barQuest, genevaQuest)
    runCurrent()

    viewModel.onPinClick("cafe")
    runCurrent()

    assertEquals("bar", state.selectedVenueId)
  }

  @Test
  fun theCardClosesWhenItsVenueHasNoValidQuestLeft() = test {
    showing()
    viewModel.onPinClick("cafe")
    runCurrent()

    quests.value = listOf(barQuest)
    runCurrent()

    assertNull(state.selectedVenueId)
    assertNull(selectedPin)

    // A new quest brings the pin back, but the explorer must tap it to open it again
    quests.value = listOf(barQuest, cafeQuest)
    runCurrent()

    assertEquals(setOf("cafe", "bar"), pinIds)
    assertNull(state.selectedVenueId)
  }

  @Test
  fun theCardFollowsChangesToItsVenuesQuests() = test {
    showing()
    viewModel.onPinClick("cafe")
    runCurrent()

    quests.value += testQuest(id = "cafe-2", venueId = "cafe", location = cafeLocation)
    runCurrent()

    assertEquals("cafe", state.selectedVenueId)
    assertEquals(1, selectedPin?.otherQuestCount)
  }

  @Test
  fun theOpenCardStaysWhenItsVenueLeavesTheView() = test {
    // The card is much bigger than the pin: it can still be on screen when the venue isn't
    showing(lausanneView)
    viewModel.onPinClick("cafe")
    runCurrent()

    showing(genevaView)

    assertEquals(setOf("geneva", "cafe"), pinIds)
    assertEquals("cafe", selectedPin?.venueId)
  }

  @Test
  fun aVenueLeftOutOfViewIsDroppedOnceItsCardCloses() = test {
    showing(lausanneView)
    viewModel.onPinClick("cafe")
    runCurrent()
    showing(genevaView)

    viewModel.onMapClick()
    runCurrent()

    assertEquals(setOf("geneva"), pinIds)
  }

  @Test
  fun aTapThatRacedAPanStillOpensTheCard() = test {
    // Venues out of view are known, so a tap that raced a pan still opens the card
    showing(lausanneView)

    viewModel.onPinClick("geneva")
    runCurrent()

    assertEquals("geneva", state.selectedVenueId)
  }

  private companion object {
    /** When the tests start, in epoch milliseconds. */
    const val START = 1_800_000_000_000L
  }
}

/** Records which venues it is asked for; with [fail], every request fails. */
private class RecordingVenueRepository(
    private val asked: MutableList<Set<String>>,
    private val fail: Boolean = false,
) : VenueRepository {
  override fun observeVenues(venueIds: Set<String>): Flow<List<Venue>> {
    asked += venueIds
    return flow {
      if (fail) throw IOException("offline")
      emit(emptyList())
    }
  }

  override fun observeVenue(venueId: String): Flow<Venue?> = flow { emit(null) }

  override suspend fun getVenue(venueId: String): Venue? = null

  override suspend fun createVenue(venue: Venue) = Result.success(Unit)

  override suspend fun setArea(venueId: String, location: Location, radiusMeters: Int) =
      Result.success(Unit)
}
