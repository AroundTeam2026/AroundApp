// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.ScriptedQuestRepository
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests how [MapViewModel] reports venues that fail to load, and loads them again. */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelVenuesErrorTest {

  private val dispatcher = StandardTestDispatcher()

  /** Central Lausanne, holding both venues. */
  private val lausanneView = GeoBounds(south = 46.50, west = 6.60, north = 46.55, east = 6.66)

  private val cafe =
      testVenue(id = "cafe", name = "Café Lumen", location = Location(46.5220, 6.6330))
  private val bar =
      testVenue(id = "bar", name = "Bar Nocturne", location = Location(46.5235, 6.6365))

  private val quests =
      MutableStateFlow<List<Quest>>(listOf(testQuest(id = "cafe-1", venueId = "cafe")))
  private val venueRepository = FlakyVenueRepository(listOf(cafe, bar))

  private val viewModel by lazy {
    MapViewModel(
        FakeLocationRepository(null),
        ScriptedQuestRepository.of(quests),
        venueRepository,
        clock = { START },
    )
  }

  private val state
    get() = viewModel.uiState.value

  private val pinIds
    get() = state.pins.map { it.venueId }.toSet()

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  /** Starts the view model and shows it central Lausanne, as the map does once it has drawn. */
  private fun TestScope.showing() {
    viewModel.onVisibleAreaChanged(lausanneView)
    runCurrent()
  }

  @Test
  fun loadedVenuesShowNoError() = test {
    showing()

    assertFalse(state.venuesFailed)
    assertEquals(setOf("cafe"), pinIds)
  }

  @Test
  fun anAreaWithoutQuestsShowsNoError() = test {
    // Empty, as when the venues fail, but nothing went wrong
    quests.value = emptyList()
    showing()

    assertFalse(state.venuesFailed)
    assertEquals(emptyList<VenuePin>(), state.pins)
  }

  @Test
  fun failingVenuesShowAnErrorAndNoPins() = test {
    venueRepository.failing = true
    showing()

    assertTrue(state.venuesFailed)
    assertEquals(emptyList<VenuePin>(), state.pins)
  }

  @Test
  fun tryingAgainLoadsTheVenuesAndClearsTheError() = test {
    venueRepository.failing = true
    showing()

    venueRepository.failing = false
    viewModel.onRetryVenues()
    runCurrent()

    assertFalse(state.venuesFailed)
    assertEquals(setOf("cafe"), pinIds)
  }

  @Test
  fun tryingAgainWhileTheyStillFailKeepsTheError() = test {
    venueRepository.failing = true
    showing()

    viewModel.onRetryVenues()
    runCurrent()

    assertTrue(state.venuesFailed)
    assertEquals(2, venueRepository.requests)
  }

  @Test
  fun tryingAgainWithoutAFailureDoesNotListenAgain() = test {
    // Each new listener costs Firestore reads
    showing()

    viewModel.onRetryVenues()
    runCurrent()

    assertEquals(1, venueRepository.requests)
  }

  @Test
  fun newVenuesThatLoadClearTheErrorWithoutTryingAgain() = test {
    venueRepository.failing = true
    showing()

    venueRepository.failing = false
    quests.value = quests.value + testQuest(id = "bar-1", venueId = "bar")
    runCurrent()

    assertFalse(state.venuesFailed)
    assertEquals(setOf("cafe", "bar"), pinIds)
  }

  private companion object {
    /** The tests' clock, in epoch milliseconds. */
    const val START = 1_800_000_000_000L
  }
}

/**
 * Serves [venues] like [FakeVenueRepository] while [failing] is false; while it is true, each new
 * request fails. Counts the [requests].
 */
internal class FlakyVenueRepository(
    venues: List<Venue>,
    private val delegate: FakeVenueRepository = FakeVenueRepository(venues),
) : VenueRepository by delegate {
  var failing = false
  var requests = 0
    private set

  override fun observeVenues(venueIds: Set<String>): Flow<List<Venue>> {
    requests++
    return if (failing) flow { throw IOException("offline") } else delegate.observeVenues(venueIds)
  }
}
