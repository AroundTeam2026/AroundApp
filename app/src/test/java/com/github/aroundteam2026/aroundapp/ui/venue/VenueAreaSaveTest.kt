// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import com.github.aroundteam2026.aroundapp.model.address.FakeAddressSearchRepository
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.GatedVenueRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests how [VenueAreaViewModel] saves the venue's marker and radius. */
@OptIn(ExperimentalCoroutinesApi::class)
class VenueAreaSaveTest {

  private val dispatcher = StandardTestDispatcher()
  private val entrance = Location(46.5191, 6.6335)
  private val placed = Location(46.5210, 6.6300)

  // A venue that hasn't placed its marker yet, with fields setArea must leave alone
  private val venue =
      testVenue(id = VENUE_ID, location = null, address = "Rue de Bourg 12", featuredQuestId = "q")

  private var venues = GatedVenueRepository(listOf(venue))
  private val viewModel by lazy {
    VenueAreaViewModel(
        VENUE_ID,
        venues,
        FakeLocationRepository(null),
        FakeAddressSearchRepository(),
    )
  }

  private val state
    get() = viewModel.uiState.value

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  /** Places the marker and sets the radius, once the venue loaded. */
  private fun TestScope.editArea() {
    viewModel
    advanceUntilIdle()
    viewModel.onMarkerPlaced(placed)
    viewModel.onRadiusChanged(120)
  }

  @Test
  fun savingIsUnavailableUntilAMarkerExists() = test {
    viewModel
    advanceUntilIdle()
    assertFalse(state.canSave)

    viewModel.onSave()
    advanceUntilIdle()

    assertEquals(0, venues.writes)
    assertFalse(state.isSaving)
  }

  @Test
  fun placingAMarkerMakesSavingAvailable() = test {
    editArea()

    assertTrue(state.canSave)
  }

  @Test
  fun savingWritesOnlyTheMarkerAndRadius() = test {
    editArea()

    viewModel.onSave()
    advanceUntilIdle()

    assertEquals(1, venues.writes)
    assertEquals(
        venue.copy(location = placed, radiusMeters = 120),
        venues.getVenue(VENUE_ID),
    )
  }

  @Test
  fun aSavedAreaIsSavedAgainAsLoaded() = test {
    venues = GatedVenueRepository(listOf(venue.copy(location = entrance, radiusMeters = 80)))
    viewModel
    advanceUntilIdle()
    assertTrue(state.canSave)

    viewModel.onSave()
    advanceUntilIdle()

    assertTrue(state.isSaved)
    assertEquals(entrance, venues.getVenue(VENUE_ID)?.location)
    assertEquals(80, venues.getVenue(VENUE_ID)?.radiusMeters)
  }

  @Test
  fun aSuccessfulSaveIsReportedUntilHandled() = test {
    editArea()

    viewModel.onSave()
    advanceUntilIdle()
    assertTrue(state.isSaved)
    assertFalse(state.isSaving)
    assertFalse(state.saveFailed)

    viewModel.onSaveHandled()

    assertFalse(state.isSaved)
  }

  @Test
  fun whileSavingItShowsProgressAndCantSaveTwice() = test {
    editArea()
    venues.writeGate = CompletableDeferred()

    viewModel.onSave()
    advanceUntilIdle()
    assertTrue(state.isSaving)
    assertFalse(state.canSave)
    viewModel.onSave()
    advanceUntilIdle()
    assertEquals(1, venues.writes)

    venues.writeGate!!.complete(Unit)
    advanceUntilIdle()

    assertFalse(state.isSaving)
    assertTrue(state.isSaved)
    assertEquals(1, venues.writes)
  }

  @Test
  fun aFailedSaveShowsAnErrorAndKeepsTheEdits() = test {
    editArea()
    venues.writeFailure = IllegalStateException("offline")

    viewModel.onSave()
    advanceUntilIdle()

    assertTrue(state.saveFailed)
    assertFalse(state.isSaving)
    assertFalse(state.isSaved)
    assertEquals(placed, state.marker)
    assertEquals(120, state.radiusMeters)
    // It can be tried again
    assertTrue(state.canSave)
  }

  @Test
  fun aSaveThatThrowsShowsAnError() = test {
    editArea()
    venues.writeError = IllegalStateException("offline")

    viewModel.onSave()
    advanceUntilIdle()

    assertTrue(state.saveFailed)
    assertFalse(state.isSaving)
    assertEquals(placed, state.marker)
  }

  @Test
  fun savingAnUnknownVenueFails() = test {
    venues = GatedVenueRepository()
    editArea()

    viewModel.onSave()
    advanceUntilIdle()

    assertTrue(state.saveFailed)
    assertFalse(state.isSaved)
  }

  @Test
  fun savingAgainClearsTheError() = test {
    editArea()
    venues.writeFailure = IllegalStateException("offline")
    viewModel.onSave()
    advanceUntilIdle()
    venues.writeFailure = null
    venues.writeGate = CompletableDeferred()

    viewModel.onSave()
    advanceUntilIdle()
    assertFalse(state.saveFailed)

    venues.writeGate!!.complete(Unit)
    advanceUntilIdle()
    assertTrue(state.isSaved)
  }

  @Test
  fun aCancelledSaveReportsNeitherSuccessNorFailure() = test {
    editArea()
    venues.writeError = CancellationException("closed")

    viewModel.onSave()
    advanceUntilIdle()

    assertFalse(state.isSaved)
    assertFalse(state.saveFailed)
  }

  private companion object {
    const val VENUE_ID = "venue"
  }
}
