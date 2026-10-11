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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests the nearby quests of [MapViewModel]: the list follows quests, venues and the explorer. */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelNearbyTest {

  private val dispatcher = StandardTestDispatcher()
  /** The test's clock: [START] plus the virtual time the test has advanced. */
  private val clock = { START + dispatcher.scheduler.currentTime }

  /** Where the explorer is: central Lausanne. */
  private val here = Location(46.5197, 6.6323)
  // About 260 m, 500 m and 1.3 km from here, and Geneva about 50 km away
  private val cafeLocation = Location(46.5220, 6.6330)
  private val barLocation = Location(46.5235, 6.6365)
  private val museumLocation = Location(46.5150, 6.6200)
  private val genevaLocation = Location(46.2044, 6.1432)

  private val cafeQuest =
      testQuest(id = "cafe-1", venueId = "cafe", venueName = "Café Lumen", location = cafeLocation)
  private val barQuest =
      testQuest(id = "bar-1", venueId = "bar", venueName = "Bar Nocturne", location = barLocation)
  private val genevaQuest =
      testQuest(
          id = "geneva-1",
          venueId = "geneva",
          venueName = "Atelier Genève",
          location = genevaLocation,
      )
  private val museumQuest =
      testQuest(
          id = "museum-1",
          venueId = "museum",
          venueName = "Musée",
          location = museumLocation,
      )

  private val quests = MutableStateFlow(listOf(cafeQuest, barQuest, genevaQuest))
  private var questRepository = ScriptedQuestRepository.of(quests)
  private var venueRepository: VenueRepository = FakeVenueRepository()
  private val locationRepository = FakeLocationRepository(here)

  private val viewModel by lazy {
    MapViewModel(locationRepository, questRepository, venueRepository, clock)
  }

  private val nearby
    get() = viewModel.uiState.value.nearby

  private val nearbyIds
    get() = nearby.map { it.questId }

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  /** Starts the view model, before the explorer's position is known. */
  private fun TestScope.started() {
    viewModel
    runCurrent()
  }

  /** Starts the view model and locates the explorer [here]. */
  private fun TestScope.located() {
    viewModel.onLocationPermissionResult(granted = true)
    runCurrent()
  }

  @Test
  fun untilTheExplorerIsLocatedEveryValidQuestIsListedByVenueName() = test {
    started()

    assertEquals(listOf("geneva-1", "bar-1", "cafe-1"), nearbyIds)
    assertTrue(nearby.all { it.distanceMeters == null })
  }

  @Test
  fun anUnknownPositionKeepsTheListByVenueName() = test {
    locationRepository.location = null

    located()

    assertEquals(listOf("geneva-1", "bar-1", "cafe-1"), nearbyIds)
  }

  @Test
  fun locatingTheExplorerSortsByDistanceAndDropsFarQuests() = test {
    started()

    located()

    assertEquals(listOf("cafe-1", "bar-1"), nearbyIds)
    assertEquals(here.distanceTo(cafeLocation), nearby[0].distanceMeters!!, 1e-6)
    assertEquals(here.distanceTo(barLocation), nearby[1].distanceMeters!!, 1e-6)
  }

  @Test
  fun theListDoesNotDependOnWhatIsOnScreen() = test {
    // Unlike the pins, the list is about the explorer, not about where the map looks
    located()
    val before = nearby

    viewModel.onVisibleAreaChanged(
        GeoBounds(south = 46.19, west = 6.12, north = 46.22, east = 6.16)
    )
    runCurrent()

    assertEquals(listOf("cafe-1", "bar-1"), before.map { it.questId })
    assertEquals(before, nearby)
  }

  @Test
  fun aNewQuestTakesItsPlaceByDistance() = test {
    located()

    quests.value += museumQuest
    runCurrent()
    quests.value += testQuest(id = "cafe-2", venueId = "cafe", location = cafeLocation)
    runCurrent()

    assertEquals(listOf("cafe-1", "cafe-2", "bar-1", "museum-1"), nearbyIds)
  }

  @Test
  fun aQuestThatEndsLeavesTheList() = test {
    located()

    quests.value = listOf(cafeQuest.copy(status = QuestStatus.ARCHIVED), barQuest, genevaQuest)
    runCurrent()

    assertEquals(listOf("bar-1"), nearbyIds)
  }

  @Test
  fun aRewardThatExpiresWhileTheMapIsOpenLeavesTheListThen() = test {
    quests.value = listOf(cafeQuest.copy(reward = rewardExpiringAt(START + 60_000)), barQuest)
    located()

    advanceTimeBy(59_999)
    runCurrent()
    assertEquals(listOf("cafe-1", "bar-1"), nearbyIds)

    advanceTimeBy(1)
    runCurrent()
    assertEquals(listOf("bar-1"), nearbyIds)
  }

  @Test
  fun aVenueThatMovesFurtherAwayMovesDownTheList() = test {
    val venues = FakeVenueRepository(listOf(testVenue(id = "cafe", location = cafeLocation)))
    venueRepository = venues
    located()

    venues.setArea("cafe", museumLocation.copy(lat = museumLocation.lat - 0.002), 50)
    runCurrent()

    assertEquals(listOf("bar-1", "cafe-1"), nearbyIds)
  }

  @Test
  fun aVenueThatMovesOutOfTheRadiusTakesItsQuestsWithIt() = test {
    val venues = FakeVenueRepository(listOf(testVenue(id = "cafe", location = cafeLocation)))
    venueRepository = venues
    located()

    venues.setArea("cafe", genevaLocation, 50)
    runCurrent()

    assertEquals(listOf("bar-1"), nearbyIds)
  }

  @Test
  fun aRenamedVenueIsListedUnderItsNewName() = test {
    val venues =
        FakeVenueRepository(
            listOf(testVenue(id = "cafe", name = "Alpage", location = cafeLocation))
        )
    venueRepository = venues
    started()

    // As "Café Lumen", which its quest copied, it would come last
    assertEquals(listOf("cafe-1", "geneva-1", "bar-1"), nearbyIds)
    assertEquals("Alpage", nearby.first().venueName)
  }

  @Test
  fun theListComesFromTheSameQuestListenerAsThePins() = test {
    // So narrowing what the repository listens to, e.g. to the explorer's area, narrows both
    var listeners = 0
    questRepository = ScriptedQuestRepository(activeQuests = quests.onStart { listeners++ })
    located()
    viewModel.onVisibleAreaChanged(
        GeoBounds(south = 46.50, west = 6.60, north = 46.55, east = 6.66)
    )
    runCurrent()

    quests.value = listOf(cafeQuest)
    runCurrent()

    assertEquals(1, listeners)
    assertEquals(listOf("cafe-1"), nearbyIds)
    assertEquals(setOf("cafe"), viewModel.uiState.value.pins.map { it.venueId }.toSet())
  }

  @Test
  fun questsThatFailEmptyTheList() = test {
    // The list starts empty, so it must be full before the failure for the test to mean anything
    val failure = CompletableDeferred<Unit>()
    questRepository =
        ScriptedQuestRepository(
            activeQuests =
                flow {
                  emit(listOf(cafeQuest, barQuest))
                  failure.await()
                  throw IOException("offline")
                }
        )
    located()
    assertEquals(listOf("cafe-1", "bar-1"), nearbyIds)

    failure.complete(Unit)
    runCurrent()

    assertEquals(emptyList<NearbyQuest>(), nearby)
  }

  @Test
  fun failingVenuesStillListTheQuests() = test {
    venueRepository =
        object : VenueRepository by FakeVenueRepository() {
          override fun observeVenues(venueIds: Set<String>) =
              flow<List<Venue>> { throw IOException("offline") }
        }

    located()

    assertEquals(listOf("cafe-1", "bar-1"), nearbyIds)
  }

  private companion object {
    /** When the tests start, in epoch milliseconds. */
    const val START = 1_800_000_000_000L
  }
}
