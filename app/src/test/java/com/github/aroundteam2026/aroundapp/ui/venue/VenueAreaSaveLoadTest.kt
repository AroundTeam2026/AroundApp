// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import com.github.aroundteam2026.aroundapp.model.address.FakeAddressSearchRepository
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits.DEFAULT_RADIUS_METERS
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits.MAX_RADIUS_METERS
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
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

/** Tests how [VenueAreaViewModel] loads and saves the venue's marker and radius. */
@OptIn(ExperimentalCoroutinesApi::class)
class VenueAreaSaveLoadTest {
  private val dispatcher = StandardTestDispatcher()
  private val entrance = Location(46.5191, 6.6335)
  private val placed = Location(46.5210, 6.6300)
  private val venue =
      testVenue(location = entrance, address = "Rue de Bourg 12")
          .copy(radiusMeters = MAX_RADIUS_METERS + 5)

  // The stored venue, read once its gate opens; writes wait for theirs and give write()
  private var stored: Venue? = venue
  private var readGate = CompletableDeferred(Unit)
  private var readError: Exception? = null
  private var writeGate = CompletableDeferred(Unit)
  private var write: () -> Result<Unit> = { Result.success(Unit) }
  private var writes = 0
  private val venues =
      object : VenueRepository by FakeVenueRepository() {
        override suspend fun getVenue(venueId: String): Venue? {
          readGate.await()
          readError?.let { throw it }
          return stored?.takeIf { it.id == venueId }
        }

        override suspend fun setArea(venueId: String, location: Location, radiusMeters: Int) =
            write().also {
              writes++
              writeGate.await()
              if (it.isSuccess)
                  stored = stored?.copy(location = location, radiusMeters = radiusMeters)
            }
      }
  private val viewModel by lazy {
    VenueAreaViewModel(
        venue.id,
        venues,
        FakeLocationRepository(null),
        FakeAddressSearchRepository(),
    )
  }
  private val state
    get() = viewModel.uiState.value

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) =
      runTest(dispatcher) {
        viewModel
        advanceUntilIdle()
        body()
      }

  @Test
  fun itOpensOnTheSavedAreaWithTheRadiusClampedAndFramesIt() = test {
    assertEquals(entrance, state.marker)
    assertEquals(MAX_RADIUS_METERS, state.radiusMeters)
    assertEquals(entrance.boundsWithin(FRAMED_RADIUS_METERS), state.areaToFrame)
  }

  @Test fun anUnknownVenueOpensOnTheDefaults() = opensOnTheDefaults { stored = null }

  @Test
  fun aVenueThatCantBeReadOpensOnTheDefaults() = opensOnTheDefaults {
    readError = IllegalStateException("offline")
  }

  @Test
  fun withoutASavedAreaItOpensOnTheDefaultsAndCantSave() =
      opensOnTheDefaults(
          andThen = {
            viewModel.onSave()
            advanceUntilIdle()
            assertEquals(0, writes)
          }
      ) {
        stored = venue.copy(location = null)
      }

  /** Opens the venue after [setUp], checks it shows the defaults, then runs [andThen]. */
  private fun opensOnTheDefaults(andThen: suspend TestScope.() -> Unit = {}, setUp: () -> Unit) {
    setUp()
    test {
      assertNull(state.marker)
      assertEquals(DEFAULT_RADIUS_METERS, state.radiusMeters)
      andThen()
    }
  }

  @Test
  fun aMarkerPlacedBeforeLoadingFinishesIsKeptAndTheRadiusStillLoads() {
    readGate = CompletableDeferred()
    test {
      viewModel.onMarkerPlaced(placed)
      readGate.complete(Unit)
      advanceUntilIdle()
      assertEquals(placed, state.marker)
      assertEquals(MAX_RADIUS_METERS, state.radiusMeters)
    }
  }

  @Test
  fun aRadiusChosenBeforeLoadingFinishesIsKeptAndTheMarkerStillLoads() {
    readGate = CompletableDeferred()
    test {
      viewModel.onRadiusChanged(80)
      readGate.complete(Unit)
      advanceUntilIdle()
      assertEquals(80, state.radiusMeters)
      assertEquals(entrance, state.marker)
    }
  }

  @Test
  fun savingRunsOnceAndWritesOnlyTheMarkerAndRadiusThenIsReportedUntilHandled() = test {
    writeGate = CompletableDeferred()
    viewModel.onMarkerPlaced(placed)
    viewModel.onRadiusChanged(80)
    viewModel.onSave()
    viewModel.onSave()
    advanceUntilIdle()
    assertTrue(state.isSaving)
    assertEquals(1, writes)

    writeGate.complete(Unit)
    advanceUntilIdle()
    assertFalse(state.isSaving)
    assertEquals(venue.copy(location = placed, radiusMeters = 80), stored)
    assertTrue(state.isSaved)
    viewModel.onSaveHandled()
    assertFalse(state.isSaved)
  }

  @Test
  fun aFailedSaveShowsAnErrorAndKeepsTheEditsToRetry() = test {
    write = { Result.failure(IllegalStateException("offline")) }
    viewModel.onMarkerPlaced(placed)
    viewModel.onRadiusChanged(80)
    viewModel.onSave()
    advanceUntilIdle()
    assertTrue(state.saveFailed)
    assertFalse(state.isSaved)
    assertEquals(placed, state.marker)
    assertEquals(80, state.radiusMeters)

    write = { Result.success(Unit) }
    writeGate = CompletableDeferred()
    viewModel.onSave()
    advanceUntilIdle()
    assertFalse(state.saveFailed)
    writeGate.complete(Unit)
    advanceUntilIdle()
    assertTrue(state.isSaved)
  }

  @Test
  fun aSaveThatThrowsIsAFailedSave() = test {
    write = { throw IllegalStateException("offline") }
    viewModel.onSave()
    advanceUntilIdle()

    assertTrue(state.saveFailed)
    assertFalse(state.isSaving)
  }
}
