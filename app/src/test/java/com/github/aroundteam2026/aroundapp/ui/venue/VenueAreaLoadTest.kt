// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import com.github.aroundteam2026.aroundapp.model.address.FakeAddressSearchRepository
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.GatedVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits.DEFAULT_RADIUS_METERS
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits.MAX_RADIUS_METERS
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits.MIN_RADIUS_METERS
import com.github.aroundteam2026.aroundapp.ui.map.DEFAULT_MAP_CENTER
import com.github.aroundteam2026.aroundapp.ui.venue.VenueAreaViewModel.Companion.FRAMED_RADIUS_METERS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Tests how [VenueAreaViewModel] opens on the venue's saved marker and radius. */
@OptIn(ExperimentalCoroutinesApi::class)
class VenueAreaLoadTest {

  private val dispatcher = StandardTestDispatcher()
  private val entrance = Location(46.5191, 6.6335)
  private val placed = Location(46.5210, 6.6300)
  private val zurich = Location(47.3769, 8.5417)
  private val lausanne = DEFAULT_MAP_CENTER.boundsWithin(FRAMED_RADIUS_METERS)

  private val saved = testVenue(id = VENUE_ID, location = entrance).copy(radiusMeters = 120)
  // Another venue, so the screen must read the one it was given
  private val other = testVenue(id = "other", location = zurich).copy(radiusMeters = 30)

  private val locations = FakeLocationRepository(zurich)
  private var venues = GatedVenueRepository(listOf(saved, other))
  private val viewModel by lazy {
    VenueAreaViewModel(VENUE_ID, venues, locations, FakeAddressSearchRepository())
  }

  private val state
    get() = viewModel.uiState.value

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  @Test
  fun itOpensOnTheSavedMarkerAndRadiusAndFramesThem() = test {
    viewModel
    advanceUntilIdle()

    assertEquals(entrance, state.marker)
    assertEquals(120, state.radiusMeters)
    assertEquals(entrance.boundsWithin(FRAMED_RADIUS_METERS), state.areaToFrame)
    assertEquals(1, venues.reads)
  }

  @Test
  fun aVenueWithoutASavedLocationKeepsTheDefaults() = test {
    venues = GatedVenueRepository(listOf(saved.copy(location = null, radiusMeters = 120)))
    viewModel
    advanceUntilIdle()

    assertNull(state.marker)
    assertEquals(DEFAULT_RADIUS_METERS, state.radiusMeters)
    assertEquals(lausanne, state.areaToFrame)
  }

  @Test
  fun anUnknownVenueKeepsTheDefaults() = test {
    venues = GatedVenueRepository(listOf(other))
    viewModel
    advanceUntilIdle()

    assertNull(state.marker)
    assertEquals(DEFAULT_RADIUS_METERS, state.radiusMeters)
    assertEquals(lausanne, state.areaToFrame)
  }

  @Test
  fun aFailedLoadKeepsTheDefaults() = test {
    venues.readError = IllegalStateException("offline")
    viewModel
    advanceUntilIdle()

    assertNull(state.marker)
    assertEquals(DEFAULT_RADIUS_METERS, state.radiusMeters)
    assertEquals(lausanne, state.areaToFrame)
  }

  @Test
  fun aCancelledLoadChangesNothing() = test {
    venues.readError = CancellationException("closed")
    viewModel
    advanceUntilIdle()

    assertNull(state.marker)
    assertEquals(DEFAULT_RADIUS_METERS, state.radiusMeters)
  }

  @Test
  fun aStoredRadiusBelowTheMinimumIsClampedToIt() = test {
    venues = GatedVenueRepository(listOf(saved.copy(radiusMeters = MIN_RADIUS_METERS - 5)))
    viewModel
    advanceUntilIdle()

    assertEquals(MIN_RADIUS_METERS, state.radiusMeters)
  }

  @Test
  fun aStoredRadiusAboveTheMaximumIsClampedToIt() = test {
    venues = GatedVenueRepository(listOf(saved.copy(radiusMeters = MAX_RADIUS_METERS + 5)))
    viewModel
    advanceUntilIdle()

    assertEquals(MAX_RADIUS_METERS, state.radiusMeters)
  }

  @Test
  fun aMarkerPlacedBeforeLoadingFinishesIsKept() = test {
    venues.readGate = CompletableDeferred()
    viewModel.onMarkerPlaced(placed)

    venues.readGate!!.complete(Unit)
    advanceUntilIdle()

    assertEquals(placed, state.marker)
    // The camera stays where the venue is working
    assertEquals(lausanne, state.areaToFrame)
  }

  @Test
  fun aRadiusChosenBeforeLoadingFinishesIsKept() = test {
    venues.readGate = CompletableDeferred()
    viewModel.onRadiusChanged(80)

    venues.readGate!!.complete(Unit)
    advanceUntilIdle()

    assertEquals(80, state.radiusMeters)
    // The saved marker still fills in, as the venue hasn't placed one
    assertEquals(entrance, state.marker)
  }

  @Test
  fun theDevicesPositionDoesNotMoveTheCameraOffTheSavedMarker() = test {
    viewModel
    advanceUntilIdle()
    viewModel.onAreaFramed(entrance.boundsWithin(FRAMED_RADIUS_METERS))

    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()

    assertNull(state.areaToFrame)
  }

  @Test
  fun theSavedMarkerIsFramedEvenWhenTheDeviceWasLocatedFirst() = test {
    venues.readGate = CompletableDeferred()
    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()
    assertEquals(zurich.boundsWithin(FRAMED_RADIUS_METERS), state.areaToFrame)

    venues.readGate!!.complete(Unit)
    advanceUntilIdle()

    assertEquals(entrance.boundsWithin(FRAMED_RADIUS_METERS), state.areaToFrame)
  }

  private companion object {
    const val VENUE_ID = "venue"
  }
}
