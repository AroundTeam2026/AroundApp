// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.FlakyVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class VenueEntryViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  private fun viewModel(repository: VenueRepository) = VenueEntryViewModel(USER, repository)

  @Test
  fun noVenueStartsAtInitialization() {
    assertEquals(VenueEntryStart.Initialization, startFor(null))
  }

  @Test
  fun aVenueWithoutALocationStartsAtTheArea() {
    assertEquals(VenueEntryStart.Area("venue"), startFor(testVenue(id = "venue", location = null)))
  }

  @Test
  fun aVenueWithALocationStartsAtHome() {
    assertEquals(VenueEntryStart.Home, startFor(testVenue(location = Location(46.5, 6.6))))
  }

  @Test
  fun startsLoadingThenChoosesFromItsUsersVenue() = test {
    val viewModel = viewModel(FakeVenueRepository(listOf(testVenue(id = USER, location = null))))
    assertEquals(VenueEntryUiState.Loading, viewModel.uiState.value)

    runCurrent()

    assertEquals(VenueEntryUiState.Ready(VenueEntryStart.Area(USER)), viewModel.uiState.value)
  }

  @Test
  fun anotherUsersVenueDoesNotCount() = test {
    val viewModel = viewModel(FakeVenueRepository(listOf(testVenue(id = "someone-else"))))
    runCurrent()

    assertEquals(VenueEntryUiState.Ready(VenueEntryStart.Initialization), viewModel.uiState.value)
  }

  @Test
  fun aFailedReadFailsAndRetryingReadsAgain() = test {
    val repository = FlakyVenueRepository(failures = 1, venue = testVenue(id = USER))
    val viewModel = viewModel(repository)
    runCurrent()
    assertEquals(VenueEntryUiState.Failed, viewModel.uiState.value)

    viewModel.onRetry()
    assertEquals(VenueEntryUiState.Loading, viewModel.uiState.value)
    runCurrent()

    assertEquals(VenueEntryUiState.Ready(VenueEntryStart.Home), viewModel.uiState.value)
    assertEquals(2, repository.reads)
  }

  @Test
  fun retryingWithoutAFailureDoesNothing() = test {
    val repository = FlakyVenueRepository(failures = 0, venue = null)
    val viewModel = viewModel(repository)
    runCurrent()

    viewModel.onRetry()
    runCurrent()

    assertEquals(VenueEntryUiState.Ready(VenueEntryStart.Initialization), viewModel.uiState.value)
    assertEquals(1, repository.reads)
  }

  @Test
  fun retryingWhileLoadingDoesNothing() = test {
    val gate = CompletableDeferred<Venue?>()
    val repository = FlakyVenueRepository(failures = 0, venue = null, gate = gate)
    val viewModel = viewModel(repository)
    runCurrent()

    viewModel.onRetry()
    gate.complete(null)
    runCurrent()

    assertEquals(1, repository.reads)
  }

  private companion object {
    const val USER = "owner-1"
  }
}
