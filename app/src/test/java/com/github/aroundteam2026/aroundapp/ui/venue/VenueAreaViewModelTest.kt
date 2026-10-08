// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import com.github.aroundteam2026.aroundapp.model.address.FakeAddressSearchRepository
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.location.LocationRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits.MAX_RADIUS_METERS
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits.MIN_RADIUS_METERS
import com.github.aroundteam2026.aroundapp.ui.map.DEFAULT_MAP_CENTER
import com.github.aroundteam2026.aroundapp.ui.venue.VenueAreaViewModel.Companion.FRAMED_RADIUS_METERS
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VenueAreaViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private val entrance = Location(46.5191, 6.6335)
  private val elsewhere = Location(46.5210, 6.6300)
  private val zurich = Location(47.3769, 8.5417)
  private val lausanne = DEFAULT_MAP_CENTER.boundsWithin(FRAMED_RADIUS_METERS)
  private val aroundZurich = zurich.boundsWithin(FRAMED_RADIUS_METERS)

  private val locations = FakeLocationRepository(zurich)
  private val viewModel by lazy { VenueAreaViewModel(locations, FakeAddressSearchRepository()) }

  private val state
    get() = viewModel.uiState.value

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  @Test
  fun placingTheMarkerSetsItThenMovesIt() {
    viewModel.onMarkerPlaced(entrance)
    assertEquals(entrance, state.marker)

    viewModel.onMarkerPlaced(elsewhere)
    assertEquals(elsewhere, state.marker)
  }

  @Test
  fun aRadiusWithinTheLimitsIsKept() {
    viewModel.onRadiusChanged(120)
    assertEquals(120, state.radiusMeters)

    // The limits themselves are allowed
    viewModel.onRadiusChanged(MIN_RADIUS_METERS)
    assertEquals(MIN_RADIUS_METERS, state.radiusMeters)
    viewModel.onRadiusChanged(MAX_RADIUS_METERS)
    assertEquals(MAX_RADIUS_METERS, state.radiusMeters)
  }

  @Test
  fun aRadiusBelowTheMinimumIsClampedToIt() {
    viewModel.onRadiusChanged(MIN_RADIUS_METERS - 1)

    assertEquals(MIN_RADIUS_METERS, state.radiusMeters)
  }

  @Test
  fun aRadiusAboveTheMaximumIsClampedToIt() {
    viewModel.onRadiusChanged(MAX_RADIUS_METERS + 1)

    assertEquals(MAX_RADIUS_METERS, state.radiusMeters)
  }

  @Test
  fun changingTheRadiusKeepsTheMarker() {
    viewModel.onMarkerPlaced(entrance)

    viewModel.onRadiusChanged(120)

    assertEquals(entrance, state.marker)
  }

  @Test
  fun aGrantedPermissionFramesTheDeviceAndShowsIt() = test {
    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()

    assertEquals(aroundZurich, state.areaToFrame)
    assertTrue(state.showsUserLocation)
  }

  @Test
  fun aGrantedPermissionDoesNotMoveTheCameraOnceAMarkerIsPlaced() = test {
    viewModel.onMarkerPlaced(entrance)

    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()

    assertEquals(lausanne, state.areaToFrame)
    assertEquals(entrance, state.marker)
  }

  @Test
  fun aMarkerPlacedWhileLocatingStopsTheCameraFromMoving() = test {
    val gate = CompletableDeferred<Unit>()
    locations.gate = gate
    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()

    viewModel.onMarkerPlaced(entrance)
    gate.complete(Unit)
    advanceUntilIdle()

    assertEquals(lausanne, state.areaToFrame)
  }

  @Test
  fun aDeniedPermissionNeverLocates() = test {
    viewModel.onLocationPermissionResult(granted = false)
    advanceUntilIdle()

    assertEquals(0, locations.calls)
    assertFalse(state.showsUserLocation)
    assertEquals(lausanne, state.areaToFrame)
  }

  @Test
  fun aRevokedPermissionStopsShowingTheDevice() = test {
    // Drawing it without the permission throws a SecurityException
    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()

    viewModel.onLocationPermissionResult(granted = false)

    assertFalse(state.showsUserLocation)
  }

  @Test
  fun theDeviceIsLocatedOncePerVisit() = test {
    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()

    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()

    assertEquals(1, locations.calls)
  }

  @Test
  fun aSecondGrantWhileLocatingDoesNotLocateAgain() = test {
    val gate = CompletableDeferred<Unit>()
    locations.gate = gate

    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()
    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()
    assertEquals(1, locations.calls)

    gate.complete(Unit)
    advanceUntilIdle()
    assertEquals(aroundZurich, state.areaToFrame)
  }

  @Test
  fun anUnknownPositionLeavesTheCameraAndIsLookedUpAgainOnTheNextGrant() = test {
    locations.location = null
    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()
    assertEquals(lausanne, state.areaToFrame)

    locations.location = zurich
    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()

    assertEquals(2, locations.calls)
    assertEquals(aroundZurich, state.areaToFrame)
  }

  @Test
  fun useMyLocationFramesTheDeviceButLeavesTheMarker() = test {
    viewModel.onMarkerPlaced(entrance)

    viewModel.onUseMyLocation()
    advanceUntilIdle()

    assertEquals(aroundZurich, state.areaToFrame)
    assertEquals(entrance, state.marker)
  }

  @Test
  fun useMyLocationWithAnUnknownPositionLeavesTheCameraAndTheMarker() = test {
    locations.location = null
    viewModel.onMarkerPlaced(entrance)
    viewModel.onAreaFramed(lausanne)

    viewModel.onUseMyLocation()
    advanceUntilIdle()

    assertEquals(1, locations.calls)
    assertNull(state.areaToFrame)
    assertEquals(entrance, state.marker)
  }

  @Test
  fun useMyLocationDuringTheOpeningLookupFramesTheDeviceOnlyOnce() = test {
    val lookups = AnsweredInAnyOrder()
    val viewModel = VenueAreaViewModel(lookups, FakeAddressSearchRepository())
    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()
    viewModel.onUseMyLocation()
    advanceUntilIdle()

    // The button's lookup finishes first, the camera frames it and the venue pans away...
    lookups.answer(lookup = 1, with = zurich)
    advanceUntilIdle()
    assertEquals(aroundZurich, viewModel.uiState.value.areaToFrame)
    viewModel.onAreaFramed(aroundZurich)
    // ...then the opening lookup finishes late, and must not move the camera back
    lookups.answer(lookup = 0, with = zurich)
    advanceUntilIdle()

    assertNull(viewModel.uiState.value.areaToFrame)
  }

  @Test
  fun aRefusalStopsTheOpeningLookup() = test {
    val gate = CompletableDeferred<Unit>()
    locations.gate = gate
    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()

    viewModel.onLocationPermissionResult(granted = false)
    gate.complete(Unit)
    advanceUntilIdle()

    assertEquals(lausanne, state.areaToFrame)
  }

  @Test
  fun aRefusalStopsUseMyLocation() = test {
    val gate = CompletableDeferred<Unit>()
    locations.gate = gate
    viewModel.onUseMyLocation()
    advanceUntilIdle()

    viewModel.onLocationPermissionResult(granted = false)
    gate.complete(Unit)
    advanceUntilIdle()

    assertEquals(lausanne, state.areaToFrame)
  }

  @Test
  fun useMyLocationTwiceWhileLocatingLooksUpOnce() = test {
    val gate = CompletableDeferred<Unit>()
    locations.gate = gate

    viewModel.onUseMyLocation()
    advanceUntilIdle()
    viewModel.onUseMyLocation()
    advanceUntilIdle()
    assertEquals(1, locations.calls)

    gate.complete(Unit)
    advanceUntilIdle()
    assertEquals(aroundZurich, state.areaToFrame)
  }

  @Test
  fun framingTheAreaClearsIt() {
    viewModel.onAreaFramed(lausanne)

    assertNull(state.areaToFrame)
  }

  @Test
  fun framingAnOutdatedAreaKeepsTheNewerOne() = test {
    // The position arrives while the map is still moving to Lausanne
    viewModel.onUseMyLocation()
    advanceUntilIdle()

    viewModel.onAreaFramed(lausanne)

    assertEquals(aroundZurich, state.areaToFrame)
  }
}

/** Answers each lookup only when the test says so, so lookups can finish in any order. */
private class AnsweredInAnyOrder : LocationRepository {
  private val answers = mutableListOf<CompletableDeferred<Location?>>()

  override suspend fun currentLocation(): Location? =
      CompletableDeferred<Location?>().also { answers += it }.await()

  /** Ends the [lookup]th lookup, counting from 0, with the position [with]. */
  fun answer(lookup: Int, with: Location?) {
    answers[lookup].complete(with)
  }
}
