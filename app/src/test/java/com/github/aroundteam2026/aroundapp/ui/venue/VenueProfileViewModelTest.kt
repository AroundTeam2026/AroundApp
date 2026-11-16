// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VenueProfileViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** Collects [model]'s state, as the screen does, so its WhileSubscribed listener runs. */
  private fun TestScope.watch(model: VenueProfileViewModel) {
    backgroundScope.launch(dispatcher) { model.uiState.collect {} }
  }

  @Test
  fun showsTheSignedInVenuesNameAndAddress() =
      runTest(dispatcher) {
        val venues =
            FakeVenueRepository(listOf(testVenue(id = "v", name = "Café", address = "Rue 1")))
        val model = VenueProfileViewModel(FakeAuthRepository("v"), venues)
        watch(model)

        assertEquals(VenueProfileStatus.LOADING, model.uiState.value.status)
        advanceUntilIdle()
        assertEquals(
            VenueProfileUiState(VenueProfileStatus.LOADED, "Café", "Rue 1"),
            model.uiState.value,
        )
      }

  @Test
  fun followsTheVenueWhenItChanges() =
      runTest(dispatcher) {
        val venues = FakeVenueRepository(listOf(testVenue(id = "v", address = null)))
        val model = VenueProfileViewModel(FakeAuthRepository("v"), venues)
        watch(model)
        advanceUntilIdle()
        assertEquals(null, model.uiState.value.address)

        venues.setArea("v", Location(46.5, 6.6), 50, "Rue 2").getOrThrow()
        advanceUntilIdle()
        assertEquals("Rue 2", model.uiState.value.address)
      }

  @Test
  fun aMissingVenueFails() =
      runTest(dispatcher) {
        val model = VenueProfileViewModel(FakeAuthRepository("v"), FakeVenueRepository())
        watch(model)
        advanceUntilIdle()

        assertEquals(VenueProfileStatus.FAILED, model.uiState.value.status)
      }

  @Test
  fun aFailedReadCanBeTriedAgain() =
      runTest(dispatcher) {
        val stored = FakeVenueRepository(listOf(testVenue(id = "v", name = "Café")))
        var reads = 0
        val flaky =
            object : VenueRepository by stored {
              override fun observeVenue(venueId: String): Flow<Venue?> =
                  if (reads++ == 0) flow { throw IOException("offline") }
                  else stored.observeVenue(venueId)
            }
        val model = VenueProfileViewModel(FakeAuthRepository("v"), flaky)
        watch(model)
        advanceUntilIdle()
        assertEquals(VenueProfileStatus.FAILED, model.uiState.value.status)

        model.retry()
        advanceUntilIdle()
        assertEquals(VenueProfileStatus.LOADED, model.uiState.value.status)
        assertEquals("Café", model.uiState.value.name)
        assertEquals(2, reads)
      }

  @Test
  fun signedOutShowsNothingYet() =
      runTest(dispatcher) {
        val model = VenueProfileViewModel(FakeAuthRepository(null), FakeVenueRepository())
        watch(model)
        advanceUntilIdle()

        assertEquals(VenueProfileUiState(), model.uiState.value)
      }
}
