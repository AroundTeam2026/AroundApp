// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.quest.ScriptedQuestRepository
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
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
import org.junit.Before
import org.junit.Test

/** Tests the nearby quest filters of [MapViewModel]: they narrow the list, not the map. */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelFiltersTest {

  private val dispatcher = StandardTestDispatcher()

  /** Where the explorer is: central Lausanne. */
  private val here = Location(46.5197, 6.6323)
  private val discount = Reward.Discount(15.0, DiscountUnit.PERCENT, specifics = null)

  // About 260 m, 500 m and 1.3 km from here
  private val cafe =
      testQuest(
          id = "cafe-1",
          venueId = "cafe",
          reward = discount,
      )
  private val bar =
      testQuest(
          id = "bar-1",
          venueId = "bar",
          reward = Reward.Other("Free entry"),
      )
  private val museum =
      testQuest(
          id = "museum-1",
          venueId = "museum",
          reward = null,
      )
  private val quests = MutableStateFlow(listOf(cafe, bar, museum))
  private val lausanne = GeoBounds(south = 46.0, west = 6.0, north = 47.0, east = 7.0)

  private val viewModel by lazy {
    MapViewModel(
        FakeLocationRepository(here),
        ScriptedQuestRepository.of(quests),
        FakeVenueRepository(
            listOf(
                testVenue(id = "cafe", location = Location(46.5220, 6.6330)),
                testVenue(id = "bar", location = Location(46.5235, 6.6365)),
                testVenue(id = "museum", location = Location(46.5150, 6.6200)),
                testVenue(id = "bakery", location = Location(46.5205, 6.6325)),
                testVenue(id = "pub", location = Location(46.5210, 6.6340)),
            )
        ),
        clock = { 1_000L },
    )
  }

  private val state
    get() = viewModel.uiState.value

  private val listed
    get() = state.nearby.map { it.questId }

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) =
      runTest(dispatcher) {
        viewModel.onLocationPermissionResult(granted = true)
        viewModel.onVisibleAreaChanged(lausanne)
        runCurrent()
        body()
      }

  @Test
  fun theListStartsUnfiltered() = test {
    assertEquals(NearbyFilters(), state.filters)
    assertEquals(listOf("cafe-1", "bar-1", "museum-1"), listed)
  }

  @Test
  fun filtersNarrowTheListButNotThePins() = test {
    val filters = NearbyFilters(distance = DistanceFilter.WITHIN_1_KM)

    viewModel.onFiltersChange(filters)
    runCurrent()

    assertEquals(filters, state.filters)
    assertEquals(listOf("cafe-1", "bar-1"), listed)
    assertEquals(setOf("cafe", "bar", "museum"), state.pins.map { it.venueId }.toSet())
  }

  @Test
  fun questsThatArriveLaterAreFilteredToo() = test {
    viewModel.onFiltersChange(NearbyFilters(reward = RewardFilter.DISCOUNT))
    runCurrent()

    quests.value =
        quests.value +
            testQuest(
                id = "bakery-1",
                venueId = "bakery",
                reward = discount,
            ) +
            testQuest(id = "pub-1", venueId = "pub")
    runCurrent()

    assertEquals(listOf("bakery-1", "cafe-1"), listed)
  }

  @Test
  fun theFiltersOutliveClosingTheOverlay() = test {
    val filters = NearbyFilters(reward = RewardFilter.OTHER)
    viewModel.onNearbyOpen()
    viewModel.onFiltersChange(filters)
    viewModel.onNearbyClose()
    runCurrent()

    viewModel.onNearbyOpen()
    runCurrent()

    assertEquals(filters, state.filters)
    assertEquals(listOf("bar-1"), listed)
  }
}
