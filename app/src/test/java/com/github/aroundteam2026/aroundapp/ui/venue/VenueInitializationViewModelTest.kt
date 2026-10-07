// Co-authored-by: OpenAI Codex
package com.github.aroundteam2026.aroundapp.ui.venue

import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VenueInitializationViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val signedInUid = MutableStateFlow<String?>("owner-1")
  private val authRepository = mockk<AuthRepository>()
  private val repository = RecordingVenueRepository()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    every { authRepository.currentUserId } returns signedInUid
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel() =
      VenueInitializationViewModel(authRepository, repository, currentTimeMillis = { 123_456L })

  @Test
  fun emptyAndWhitespaceNamesAreRejectedWithoutRepositoryCalls() =
      runTest(dispatcher) {
        val viewModel = viewModel()
        listOf("", " \t\n ").forEach { name ->
          viewModel.updateBusinessName(name)
          viewModel.submit()
          runCurrent()
          assertEquals(VenueInitializationError.EMPTY_NAME, viewModel.uiState.value.error)
          assertFalse(viewModel.uiState.value.isSaving)
        }
        assertEquals(0, repository.reads)
        assertTrue(repository.created.isEmpty())
      }

  @Test
  fun validNameCreatesUnplacedVenueForSignedInUser() =
      runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.updateBusinessName("  Café Lumière  ")
        viewModel.submit()
        runCurrent()

        val expected =
            Venue(
                "owner-1",
                "Café Lumière",
                null,
                VenueLimits.DEFAULT_RADIUS_METERS,
                null,
                123_456L,
            )
        assertEquals(listOf(expected), repository.created)
        assertEquals(expected, repository.getVenue("owner-1"))
        assertEquals("owner-1", viewModel.uiState.value.venueId)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.canContinue)
        viewModel.submit()
        runCurrent()
        assertEquals(1, repository.created.size)
      }

  @Test
  fun savingDisablesContinueAndIgnoresRepeatedSubmissions() =
      runTest(dispatcher) {
        repository.saveGate = CompletableDeferred()
        val viewModel = viewModel()
        viewModel.updateBusinessName("Cafe")
        viewModel.submit()
        assertTrue(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.canContinue)
        viewModel.submit()
        viewModel.updateBusinessName("Replacement")
        runCurrent()
        assertEquals(1, repository.created.size)
        assertEquals("Cafe", viewModel.uiState.value.businessName)
        assertNull(viewModel.uiState.value.venueId)
        repository.saveGate!!.complete(Unit)
        runCurrent()
        assertEquals("owner-1", viewModel.uiState.value.venueId)
      }

  @Test
  fun failedSavePreservesNameAndAllowsRetry() =
      runTest(dispatcher) {
        repository.saveFailure = IllegalStateException("Offline")
        val viewModel = viewModel()
        viewModel.updateBusinessName(" Café ")
        viewModel.submit()
        runCurrent()
        assertEquals(" Café ", viewModel.uiState.value.businessName)
        assertEquals(VenueInitializationError.SAVE_FAILED, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.canContinue)
        assertNull(viewModel.uiState.value.venueId)

        repository.saveFailure = null
        viewModel.submit()
        runCurrent()
        assertEquals("owner-1", viewModel.uiState.value.venueId)
        assertNull(viewModel.uiState.value.error)
      }

  @Test
  fun thrownReadFailureShowsRecoverableError() =
      runTest(dispatcher) {
        repository.readFailure = IllegalStateException("Offline")
        val viewModel = viewModel()
        viewModel.updateBusinessName("Cafe")
        viewModel.submit()
        runCurrent()
        assertEquals(VenueInitializationError.SAVE_FAILED, viewModel.uiState.value.error)
        assertEquals("Cafe", viewModel.uiState.value.businessName)
        assertTrue(viewModel.uiState.value.canContinue)
        assertTrue(repository.created.isEmpty())
      }

  @Test
  fun existingVenueContinuesWithoutCreatingOrOverwriting() =
      runTest(dispatcher) {
        val original = Venue("owner-1", "Original", null, 75, "Existing address", 1L)
        repository.seed(original)
        val viewModel = viewModel()
        viewModel.updateBusinessName("Replacement")
        viewModel.submit()
        runCurrent()
        assertTrue(repository.created.isEmpty())
        assertEquals(original, repository.getVenue("owner-1"))
        assertEquals("owner-1", viewModel.uiState.value.venueId)
      }

  @Test
  fun concurrentCreationKeepsWinningVenueAndContinues() =
      runTest(dispatcher) {
        repository.saveGate = CompletableDeferred()
        val viewModel = viewModel()
        viewModel.updateBusinessName("Losing name")
        viewModel.submit()
        runCurrent()
        val winner = Venue("owner-1", "Winner", null, 50, null, 2L)
        repository.seed(winner)
        repository.saveGate!!.complete(Unit)
        runCurrent()
        assertEquals(winner, repository.getVenue("owner-1"))
        assertEquals("owner-1", viewModel.uiState.value.venueId)
        assertNull(viewModel.uiState.value.error)
      }

  @Test
  fun missingSessionIsRejectedWithoutSaving() =
      runTest(dispatcher) {
        signedInUid.value = null
        val viewModel = viewModel()
        viewModel.updateBusinessName("Cafe")
        viewModel.submit()
        runCurrent()
        assertEquals(VenueInitializationError.SIGN_IN_REQUIRED, viewModel.uiState.value.error)
        assertEquals(0, repository.reads)
        assertTrue(repository.created.isEmpty())
      }

  @Test
  fun editingNameClearsValidationError() {
    val viewModel = viewModel()
    viewModel.submit()
    viewModel.updateBusinessName("Cafe")
    assertEquals("Cafe", viewModel.uiState.value.businessName)
    assertNull(viewModel.uiState.value.error)
  }

  private class RecordingVenueRepository : VenueRepository {
    private val delegate = FakeVenueRepository()
    val created = mutableListOf<Venue>()
    var reads = 0
    var saveFailure: Exception? = null
    var readFailure: Exception? = null
    var saveGate: CompletableDeferred<Unit>? = null

    override fun observeVenue(venueId: String) = delegate.observeVenue(venueId)

    override suspend fun getVenue(venueId: String): Venue? {
      reads++
      readFailure?.let { throw it }
      return delegate.getVenue(venueId)
    }

    override suspend fun createVenue(venue: Venue): Result<Unit> {
      created.add(venue)
      saveGate?.await()
      saveFailure?.let {
        return Result.failure(it)
      }
      return delegate.createVenue(venue)
    }

    suspend fun seed(venue: Venue) {
      delegate.createVenue(venue).getOrThrow()
    }
  }
}
