// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.ScriptedQuestRepository
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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

/** Tests how [MapViewModel] reports quests that fail to load, and loads them again. */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelQuestsErrorTest {

  private val dispatcher = StandardTestDispatcher()

  /** Central Lausanne, holding the venue. */
  private val lausanneView = GeoBounds(south = 46.50, west = 6.60, north = 46.55, east = 6.66)

  private val cafe =
      testVenue(id = "cafe", name = "Café Lumen", location = Location(46.5220, 6.6330))

  private val loaded: Flow<List<Quest>> = flowOf(listOf(testQuest(id = "cafe-1", venueId = "cafe")))
  private val failing: Flow<List<Quest>> = flow { throw IOException("offline") }

  private val questRepository = ScriptedQuestRepository(activeQuests = failing)
  private val venueRepository = FlakyVenueRepository(listOf(cafe))

  private val viewModel by lazy {
    MapViewModel(FakeLocationRepository(null), questRepository, venueRepository, clock = { START })
  }

  private val state
    get() = viewModel.uiState.value

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  /** Starts the view model and shows it central Lausanne, as the map does once it has drawn. */
  private fun TestScope.showing() {
    viewModel.onVisibleAreaChanged(lausanneView)
    runCurrent()
  }

  /** Taps "Try again" and lets the view model answer. */
  private fun TestScope.retry() {
    viewModel.onRetry()
    runCurrent()
  }

  @Test
  fun failingQuestsShowAnErrorAndNoPins() = test {
    showing()

    assertTrue(state.loadFailed)
    assertEquals(emptyList<VenuePin>(), state.pins)
    assertEquals(emptyList<NearbyQuest>(), state.nearby)
  }

  @Test
  fun tryingAgainLoadsTheQuestsAndClearsTheError() = test {
    showing()

    questRepository.activeQuests = loaded
    retry()

    assertFalse(state.loadFailed)
    assertEquals(listOf("cafe"), state.pins.map { it.venueId })
    assertEquals(2, questRepository.activeQuestsCollections)
  }

  @Test
  fun tryingAgainWhileTheyStillFailKeepsTheError() = test {
    showing()

    retry()

    assertTrue(state.loadFailed)
    assertFalse(state.retrying)
    assertEquals(2, questRepository.activeQuestsCollections)
  }

  @Test
  fun aRetryWaitsForTheQuestsToAnswer() = test {
    showing()

    questRepository.activeQuests = flow { awaitCancellation() }
    retry()

    assertTrue(state.retrying)
    assertTrue(state.loadFailed)
  }

  @Test
  fun aSecondTapWhileRetryingDoesNotListenAgain() = test {
    showing()

    questRepository.activeQuests = flow { awaitCancellation() }
    retry()
    retry()

    assertEquals(2, questRepository.activeQuestsCollections)
  }

  @Test
  fun tryingAgainAfterTheVenuesFailedListensOnlyToTheVenues() = test {
    // The quests loaded: listening to them again would cost Firestore reads for nothing
    questRepository.activeQuests = loaded
    venueRepository.failing = true
    showing()

    venueRepository.failing = false
    retry()

    assertFalse(state.loadFailed)
    assertEquals(1, questRepository.activeQuestsCollections)
    assertEquals(2, venueRepository.requests)
  }

  private companion object {
    /** The tests' clock, in epoch milliseconds. */
    const val START = 1_800_000_000_000L
  }
}
