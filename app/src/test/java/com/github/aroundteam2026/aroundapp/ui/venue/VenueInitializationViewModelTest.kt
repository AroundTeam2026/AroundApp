// Co-authored-by: OpenAI Codex
package com.github.aroundteam2026.aroundapp.ui.venue

import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
  private val authRepository = FakeAuthRepository("owner-1")
  private val repository = RecordingVenueRepository()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel() =
      VenueInitializationViewModel(authRepository, repository, currentTimeMillis = { 123_456L })
          .also { dispatcher.scheduler.runCurrent() }

  @Test
  fun emptyAndWhitespaceNamesAreRejectedWithoutAdditionalRepositoryCalls() =
      runTest(dispatcher) {
        val viewModel = viewModel()
        val reads = repository.reads
        listOf("", " \t\n ").forEach { name ->
          viewModel.updateBusinessName(name)
          viewModel.submit()
          runCurrent()
          assertEquals(VenueInitializationError.EMPTY_NAME, viewModel.uiState.value.error)
          assertFalse(viewModel.uiState.value.isSaving)
        }
        assertEquals(reads, repository.reads)
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
  fun existingVenuePrefillsNameAndPreventsEditing() =
      runTest(dispatcher) {
        val original = Venue("owner-1", "Original", null, 75, "Existing address", 1L)
        repository.seed(original)
        val viewModel = viewModel()
        assertEquals("Original", viewModel.uiState.value.businessName)
        assertFalse(viewModel.uiState.value.canEditName)
        viewModel.updateBusinessName("Replacement")
        assertEquals("Original", viewModel.uiState.value.businessName)
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
        assertEquals("Winner", viewModel.uiState.value.businessName)
        assertFalse(viewModel.uiState.value.canEditName)
        assertEquals("owner-1", viewModel.uiState.value.venueId)
        assertNull(viewModel.uiState.value.error)
      }

  @Test
  fun missingSessionIsRejectedWithoutSaving() =
      runTest(dispatcher) {
        authRepository.signOut()
        val viewModel = viewModel()
        viewModel.updateBusinessName("Cafe")
        viewModel.submit()
        runCurrent()
        assertEquals(VenueInitializationError.SIGN_IN_REQUIRED, viewModel.uiState.value.error)
        assertEquals(0, repository.reads)
        assertTrue(repository.created.isEmpty())
      }

  @Test
  fun handledNavigationKeepsNameAndAllowsContinueWithoutRecreatingVenue() =
      runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.updateBusinessName("Cafe")
        viewModel.submit()
        runCurrent()
        viewModel.onLocationNavigationHandled()
        assertFalse(viewModel.uiState.value.canEditName)
        viewModel.updateBusinessName("Discarded edit")
        assertNull(viewModel.uiState.value.venueId)
        assertEquals("Cafe", viewModel.uiState.value.businessName)
        assertTrue(viewModel.uiState.value.canContinue)

        viewModel.submit()
        runCurrent()
        assertEquals("owner-1", viewModel.uiState.value.venueId)
        assertEquals(1, repository.created.size)
      }

  @Test
  fun editingNameClearsValidationError() {
    val viewModel = viewModel()
    viewModel.submit()
    viewModel.updateBusinessName("Cafe")
    assertEquals("Cafe", viewModel.uiState.value.businessName)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun overlongNameIsRejectedAndEditingClearsError() =
      runTest(dispatcher) {
        val viewModel = viewModel()
        val reads = repository.reads
        viewModel.updateBusinessName("a".repeat(VenueLimits.MAX_NAME_LENGTH + 1))
        viewModel.submit()
        runCurrent()
        assertEquals(VenueInitializationError.NAME_TOO_LONG, viewModel.uiState.value.error)
        assertEquals(reads, repository.reads)
        assertTrue(repository.created.isEmpty())
        viewModel.updateBusinessName("Cafe")
        assertNull(viewModel.uiState.value.error)
      }

  @Test
  fun maximumLengthTrimmedNameIsAccepted() =
      runTest(dispatcher) {
        val viewModel = viewModel()
        val name = "a".repeat(VenueLimits.MAX_NAME_LENGTH)
        viewModel.updateBusinessName("  $name  ")
        viewModel.submit()
        runCurrent()
        assertEquals(name, repository.created.single().name)
        assertNull(viewModel.uiState.value.error)
      }

  @Test
  fun loadingDisablesFormUntilExistingVenueIsChecked() =
      runTest(dispatcher) {
        val viewModel = VenueInitializationViewModel(authRepository, repository)
        assertTrue(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.canContinue)
        assertFalse(viewModel.uiState.value.canEditName)
        viewModel.updateBusinessName("Ignored")
        viewModel.submit()
        runCurrent()
        assertEquals("", viewModel.uiState.value.businessName)
        assertTrue(viewModel.uiState.value.canEditName)
        assertTrue(repository.created.isEmpty())
      }

  @Test
  fun failedInitialReadAllowsRetryWithoutOverwritingExistingVenue() =
      runTest(dispatcher) {
        repository.seed(Venue("owner-1", "Original", null, 50, null, 1L))
        repository.readFailure = IllegalStateException("Offline")
        val viewModel = viewModel()
        assertEquals(VenueInitializationError.SAVE_FAILED, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.canContinue)
        repository.readFailure = null
        viewModel.updateBusinessName("Replacement")
        viewModel.submit()
        runCurrent()
        assertEquals("Original", viewModel.uiState.value.businessName)
        assertFalse(viewModel.uiState.value.canEditName)
        assertTrue(repository.created.isEmpty())
      }

  private class RecordingVenueRepository : VenueRepository {
    private val delegate = FakeVenueRepository()
    val created = mutableListOf<Venue>()
    var reads = 0
    var saveFailure: Exception? = null
    var readFailure: Exception? = null
    var saveGate: CompletableDeferred<Unit>? = null

    override fun observeVenue(venueId: String) = delegate.observeVenue(venueId)

    override suspend fun setArea(venueId: String, location: Location, radiusMeters: Int) =
        delegate.setArea(venueId, location, radiusMeters)

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
