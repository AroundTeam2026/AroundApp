// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.connectivity.FakeConnectivityRepository
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.ScriptedQuestRepository
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

/** Tests how [MapViewModel] keeps the venues it has offline, and loads them again once online. */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelOfflineTest {

  private val dispatcher = StandardTestDispatcher()

  /** Central Lausanne, holding both venues. */
  private val lausanneView = GeoBounds(south = 46.50, west = 6.60, north = 46.55, east = 6.66)

  private val cafe =
      testVenue(id = "cafe", name = "Café Lumen", location = Location(46.5220, 6.6330))
  private val bar =
      testVenue(id = "bar", name = "Bar Nocturne", location = Location(46.5235, 6.6365))

  private val quests =
      MutableStateFlow<List<Quest>>(listOf(testQuest(id = "cafe-1", venueId = "cafe")))
  private val questRepository = ScriptedQuestRepository.of(quests)
  private val venueRepository = FlakyVenueRepository(listOf(cafe, bar))
  private val connectivity = FakeConnectivityRepository()

  private val viewModel by lazy {
    MapViewModel(
        FakeLocationRepository(null),
        questRepository,
        venueRepository,
        connectivity,
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
    // A test that went offline first only sees the banner once the view model waited for it
    waitForTheOfflineBanner()
  }

  /** Takes the device offline, or back online with [online], and lets the view model answer. */
  private fun TestScope.goOnline(online: Boolean) {
    connectivity.online.value = online
    runCurrent()
    waitForTheOfflineBanner()
  }

  /**
   * Lets [MapViewModel.OFFLINE_BANNER_DELAY_MILLIS] pass, after which the map says it's offline.
   */
  private fun TestScope.waitForTheOfflineBanner() {
    advanceTimeBy(MapViewModel.OFFLINE_BANNER_DELAY_MILLIS)
    runCurrent()
  }

  @Test
  fun theMapSaysItIsOfflineOnlyAfterTheDelay() = test {
    showing()

    connectivity.online.value = false
    runCurrent()
    advanceTimeBy(MapViewModel.OFFLINE_BANNER_DELAY_MILLIS - 1)
    runCurrent()
    assertFalse(state.offline)

    advanceTimeBy(1)
    runCurrent()
    assertTrue(state.offline)
  }

  @Test
  fun aHandoverShorterThanTheDelayNeverSaysOffline() = test {
    // Switching between Wi-Fi and mobile data counts as offline until the new network is validated
    showing()
    val seen = mutableListOf<Boolean>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      viewModel.uiState.collect { seen += it.offline }
    }

    connectivity.online.value = false
    runCurrent()
    advanceTimeBy(MapViewModel.OFFLINE_BANNER_DELAY_MILLIS - 1)
    connectivity.online.value = true
    runCurrent()
    waitForTheOfflineBanner()

    assertFalse(true in seen)
  }

  @Test
  fun onlineTheMapIsNotOffline() = test {
    showing()

    assertFalse(state.offline)
  }

  @Test
  fun offlineWithCachedVenuesShowsThePinsAndNoError() = test {
    // Offline, Firestore answers from its cache
    goOnline(false)
    showing()

    assertTrue(state.offline)
    assertFalse(state.loadFailed)
    assertEquals(setOf("cafe"), pinIds)
  }

  @Test
  fun venuesFailingOfflineKeepThePinsLastLoaded() = test {
    showing()
    goOnline(false)

    // A new venue makes the venues listened to again, and that listener fails
    venueRepository.failing = true
    quests.value = quests.value + testQuest(id = "bar-1", venueId = "bar")
    runCurrent()

    assertTrue(state.offline)
    assertFalse(state.loadFailed)
    assertEquals(setOf("cafe"), pinIds)
  }

  @Test
  fun questsFailingOfflineKeepThePinsLastLoaded() = test {
    val answers = Channel<List<Quest>>(Channel.UNLIMITED)
    questRepository.activeQuests = answers.receiveAsFlow()
    answers.trySend(listOf(testQuest(id = "cafe-1", venueId = "cafe")))
    showing()
    goOnline(false)

    answers.close(IOException("dropped"))
    runCurrent()

    assertFalse(state.loadFailed)
    assertEquals(setOf("cafe"), pinIds)
  }

  @Test
  fun offlineWithoutACacheShowsOfflineAndNotTheError() = test {
    goOnline(false)
    venueRepository.failing = true
    showing()

    assertTrue(state.offline)
    assertFalse(state.loadFailed)
    assertEquals(emptyList<VenuePin>(), state.pins)
  }

  @Test
  fun backOnlineLoadsTheFailedVenuesWithoutTryingAgain() = test {
    goOnline(false)
    venueRepository.failing = true
    showing()

    venueRepository.failing = false
    goOnline(true)

    assertFalse(state.offline)
    assertFalse(state.loadFailed)
    assertEquals(setOf("cafe"), pinIds)
    assertEquals(2, venueRepository.requests)
  }

  @Test
  fun backOnlineLoadsTheFailedQuestsWithoutTryingAgain() = test {
    questRepository.activeQuests = flow { throw IOException("offline") }
    goOnline(false)
    showing()

    questRepository.activeQuests = quests
    goOnline(true)

    assertFalse(state.loadFailed)
    assertEquals(setOf("cafe"), pinIds)
  }

  @Test
  fun backOnlineWhileStillFailingShowsTheError() = test {
    goOnline(false)
    venueRepository.failing = true
    showing()

    goOnline(true)

    assertTrue(state.loadFailed)
    assertEquals(emptyList<VenuePin>(), state.pins)
  }

  @Test
  fun backOnlineAfterBothFailedLoadsTheQuestsAndTheVenues() = test {
    // The venues fail, then the quests: those kept offline keep the same venues, so reloading the
    // quests alone wouldn't reload the venues
    val answers = Channel<List<Quest>>(Channel.UNLIMITED)
    questRepository.activeQuests = answers.receiveAsFlow()
    answers.trySend(listOf(testQuest(id = "cafe-1", venueId = "cafe")))
    goOnline(false)
    venueRepository.failing = true
    showing()
    answers.close(IOException("dropped"))
    runCurrent()

    questRepository.activeQuests = quests
    venueRepository.failing = false
    goOnline(true)

    assertFalse(state.loadFailed)
    assertEquals(setOf("cafe"), pinIds)
  }

  @Test
  fun backOnlineShowsNoErrorWhileTheReloadWaits() = test {
    goOnline(false)
    venueRepository.failing = true
    showing()

    venueRepository.answering = false
    goOnline(true)

    assertFalse(state.loadFailed)
    assertFalse(state.retrying)
  }

  @Test
  fun backOnlineWithNothingFailedDoesNotListenAgain() = test {
    // Each new listener costs Firestore reads
    goOnline(false)
    showing()

    goOnline(true)

    assertEquals(1, venueRepository.requests)
  }

  @Test
  fun aFailureOnlineStillShowsTheError() = test {
    venueRepository.failing = true
    showing()

    assertFalse(state.offline)
    assertTrue(state.loadFailed)
    // Not listened to again before the explorer taps "Try again"
    assertEquals(1, venueRepository.requests)
  }

  @Test
  fun theLocationStaysOffAlongsideOffline() = test {
    goOnline(false)
    showing()

    viewModel.onLocationPermissionResult(false)
    runCurrent()

    assertTrue(state.offline)
    assertTrue(state.locationRefused)
  }

  private companion object {
    /** The tests' clock, in epoch milliseconds. */
    const val START = 1_800_000_000_000L
  }
}
