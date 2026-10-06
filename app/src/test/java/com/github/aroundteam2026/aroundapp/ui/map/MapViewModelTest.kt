// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.ui.map.MapViewModel.Companion.DEFAULT_CENTER
import com.github.aroundteam2026.aroundapp.ui.map.MapViewModel.Companion.NEARBY_RADIUS_METERS
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
class MapViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private val lausanne = DEFAULT_CENTER.boundsWithin(NEARBY_RADIUS_METERS)
  private val zurich = Location(47.3769, 8.5417)
  private val geneva = Location(46.2044, 6.1432)

  private val repository = FakeLocationRepository(zurich)
  private val viewModel by lazy { MapViewModel(repository) }

  private val state
    get() = viewModel.uiState.value

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  @Test
  fun startsOnLausanneWithoutTheExplorersPosition() = test {
    assertEquals(lausanne, state.areaToFrame)
    assertFalse(state.showsUserLocation)
  }

  @Test
  fun aGrantedPermissionFramesFiveKilometresAroundTheExplorer() = test {
    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()

    assertEquals(zurich.boundsWithin(5_000.0), state.areaToFrame)
    assertTrue(state.showsUserLocation)
  }

  @Test
  fun aDeniedPermissionKeepsLausanneAndNeverLocates() = test {
    viewModel.onLocationPermissionResult(granted = false)
    advanceUntilIdle()

    assertEquals(lausanne, state.areaToFrame)
    assertFalse(state.showsUserLocation)
    assertEquals(0, repository.calls)
  }

  @Test
  fun anUnknownPositionLeavesTheCameraWhereItIs() = test {
    repository.location = null
    viewModel.onAreaFramed(lausanne)

    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()

    assertNull(state.areaToFrame)
    // The permission is granted, so the position can still be drawn once the device finds it
    assertTrue(state.showsUserLocation)
  }

  @Test
  fun aLaterGrantRetriesWhenNoPositionWasFound() = test {
    repository.location = null
    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()

    repository.location = zurich
    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()

    assertEquals(zurich.boundsWithin(NEARBY_RADIUS_METERS), state.areaToFrame)
    assertEquals(2, repository.calls)
  }

  @Test
  fun onceLocatedReturningToTheMapKeepsTheCameraWhereTheExplorerLeftIt() = test {
    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()
    viewModel.onAreaFramed(zurich.boundsWithin(NEARBY_RADIUS_METERS))

    // Coming back to the Map tab reports the permission again, from somewhere else
    repository.location = geneva
    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()

    assertNull(state.areaToFrame)
    assertEquals(1, repository.calls)
  }

  @Test
  fun aSecondGrantWhileLocatingDoesNotStartAnotherLookup() = test {
    val gate = CompletableDeferred<Unit>()
    repository.gate = gate

    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()
    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()
    assertEquals(1, repository.calls)

    gate.complete(Unit)
    advanceUntilIdle()
    assertEquals(zurich.boundsWithin(NEARBY_RADIUS_METERS), state.areaToFrame)
  }

  @Test
  fun framingTheAreaClearsIt() = test {
    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()
    val area = zurich.boundsWithin(NEARBY_RADIUS_METERS)
    assertEquals(area, state.areaToFrame)

    viewModel.onAreaFramed(area)

    assertNull(state.areaToFrame)
  }

  @Test
  fun framingAnOutdatedAreaKeepsTheNewerOne() = test {
    // The position arrives while the map is still moving to Lausanne
    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()

    viewModel.onAreaFramed(lausanne)

    assertEquals(zurich.boundsWithin(NEARBY_RADIUS_METERS), state.areaToFrame)
  }

  @Test
  fun aRevokedPermissionStopsDrawingTheExplorersPosition() = test {
    // Drawing it without the permission throws a SecurityException
    viewModel.onLocationPermissionResult(granted = true)
    advanceUntilIdle()
    assertTrue(state.showsUserLocation)

    viewModel.onLocationPermissionResult(granted = false)

    assertFalse(state.showsUserLocation)
  }
}
