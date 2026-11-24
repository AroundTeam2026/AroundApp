// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import android.net.Uri
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.user.FakeUserPictureRepository
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The venue's picture on its setup and details form; Robolectric, for real [Uri]s. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VenueInitializationPictureTest {
  private val dispatcher = StandardTestDispatcher()
  private val venues = FakeVenueRepository()
  private val pictures = FakeUserPictureRepository()
  private var pictureWrites = 0
  private var pictureFailure: Exception? = null
  private var pictureGate: CompletableDeferred<Unit>? = null
  private val repository =
      object : VenueRepository by venues {
        override suspend fun setPicture(venueId: String, pictureUrl: String?): Result<Unit> {
          pictureWrites++
          pictureGate?.await()
          return pictureFailure?.let { Result.failure(it) }
              ?: venues.setPicture(venueId, pictureUrl)
        }
      }

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun TestScope.viewModel(editing: Boolean = false) =
      VenueInitializationViewModel(
              FakeAuthRepository(OWNER),
              repository,
              pictures,
              editing = editing,
          )
          .also { runCurrent() }

  private fun seed(pictureUrl: String?) = runBlocking {
    venues
        .createVenue(Venue(OWNER, "Café", null, 50, null, 1L, pictureUrl = pictureUrl))
        .getOrThrow()
  }

  private fun savedPicture() = runBlocking { venues.getVenue(OWNER)?.pictureUrl }

  @Test
  fun setupSavesThePickedPictureWithTheNewVenue() =
      runTest(dispatcher) {
        val model = viewModel()
        model.updateBusinessName("Café")
        model.setPhoto(PHOTO)
        model.submit()
        runCurrent()

        assertEquals(FIRST_UPLOAD, savedPicture())
        assertEquals(OWNER, model.uiState.value.venueId)
      }

  @Test
  fun editingWithoutTouchingThePictureWritesNone() =
      runTest(dispatcher) {
        seed(SAVED)
        val model = viewModel(editing = true)
        assertEquals(SAVED, model.uiState.value.savedPicture)
        model.updateBusinessName("Café Lumière")
        model.submit()
        runCurrent()

        assertEquals(0, pictureWrites)
        assertEquals(SAVED, savedPicture())
      }

  @Test
  fun removingThePictureClearsIt() =
      runTest(dispatcher) {
        seed(SAVED)
        val model = viewModel(editing = true)
        model.removePhoto()
        model.submit()
        runCurrent()

        assertNull(savedPicture())
      }

  @Test
  fun aFailedUploadKeepsTheFormAndWritesNoPicture() =
      runTest(dispatcher) {
        pictures.failure = IllegalStateException("Offline")
        val model = viewModel()
        model.updateBusinessName("Café")
        model.setPhoto(PHOTO)
        model.submit()
        runCurrent()

        assertEquals(VenueInitializationError.SAVE_FAILED, model.uiState.value.error)
        assertEquals(PHOTO, model.uiState.value.photo)
        assertEquals(0, pictureWrites)
      }

  @Test
  fun aRetryAfterAFailedPictureWriteReusesTheUpload() =
      runTest(dispatcher) {
        pictureFailure = IllegalStateException("Offline")
        val model = viewModel()
        model.updateBusinessName("Café")
        model.setPhoto(PHOTO)
        model.submit()
        runCurrent()
        assertNull(model.uiState.value.venueId)

        pictureFailure = null
        model.submit()
        runCurrent()

        // A second upload would have been stored under the next id
        assertEquals(FIRST_UPLOAD, savedPicture())
        assertEquals(OWNER, model.uiState.value.venueId)
      }

  @Test
  fun thePictureCantChangeWhileSaving() =
      runTest(dispatcher) {
        pictureGate = CompletableDeferred()
        val model = viewModel()
        model.updateBusinessName("Café")
        model.setPhoto(PHOTO)
        model.submit()
        runCurrent()

        model.setPhoto(Uri.parse("content://photos/other"))
        model.removePhoto()
        assertEquals(PHOTO, model.uiState.value.photo)
        assertFalse(model.uiState.value.removePhoto)
        pictureGate!!.complete(Unit)
        runCurrent()
        assertEquals(FIRST_UPLOAD, savedPicture())
      }

  private companion object {
    const val OWNER = "owner-1"
    const val SAVED = "gs://fake-bucket/venue-pictures/owner-1/saved"
    const val FIRST_UPLOAD = "gs://fake-bucket/user-pictures/owner-1/1"
    val PHOTO: Uri = Uri.parse("content://photos/venue")
  }
}
