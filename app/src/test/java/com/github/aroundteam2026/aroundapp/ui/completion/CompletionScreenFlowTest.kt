// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Application
import android.location.LocationManager
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.reservation.FakeCompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeProofPhotoRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Proof
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The real screen and ViewModel, with repository fakes: UI actions must reach stored results. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class CompletionScreenFlowTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val app = ApplicationProvider.getApplicationContext<Application>()
  private val reservations = FakeReservationRepository()
  private val completions = FakeCompletionRepository()
  private val photos = FakeProofPhotoRepository()
  private val venue = testVenue()
  private val locations = FakeLocationRepository(venue.location)
  private val store = ViewModelStore()
  private lateinit var viewModel: CompletionViewModel
  private val submitted = mutableListOf<String>()
  private var loadFails = false
  private var backCalls = 0
  private var venueName by mutableStateOf(venue.name)

  @After
  fun clear() {
    compose.runOnIdle { store.clear() }
  }

  /** Shows the form of a new approved reservation; returns that reservation's id. */
  private fun show(
      proofType: ProofType = ProofType.TEXT,
      expectedStatus: CompletionFormStatus = CompletionFormStatus.READY,
  ): String {
    shadowOf(app).grantPermissions(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
    shadowOf(app.getSystemService(LocationManager::class.java)).setLocationEnabled(true)
    val reservationId = runBlocking {
      reservations
          .createReservation(newPendingReservation("quest", venue.id, "Ada", 1_000), "explorer")
          .getOrThrow()
          .also { reservations.updateStatus(it, ReservationStatus.APPROVED).getOrThrow() }
    }
    compose.runOnIdle {
      viewModel =
          CompletionViewModel(
              reservationId,
              "explorer",
              object : ReservationRepository by reservations {
                override fun observeForExplorer(uid: String) = flow {
                  if (loadFails) throw IOException("offline")
                  emitAll(reservations.observeForExplorer(uid))
                }
              },
              FakeQuestRepository(initialQuests = listOf(testQuest(proofType = proofType))),
              FakeVenueRepository(listOf(venue)),
              locations,
              completions,
              photos,
          )
      store.put("completion", viewModel)
    }
    compose.setContent {
      AroundAppTheme {
        CompletionScreen(viewModel, venueName, { backCalls++ }, { submitted += it })
      }
    }
    compose.waitUntil { viewModel.uiState.value.status == expectedStatus }
    return reservationId
  }

  // A teammate's proof arrives while the form is open: the screen says so and stays open
  @Test
  fun aTeammatesProofKeepsTheScreenOpenAndSaysSo() {
    val reservationId = show()
    compose.onNodeWithTag(C.Tag.COMPLETION_ANSWER).performTextInput("Mochi")

    runBlocking {
      completions.submit(reservationId, venue.id, "friend", Proof.Text("By the till")).getOrThrow()
    }

    compose
        .onNodeWithTag(C.Tag.COMPLETION_HINT)
        .assertTextContains(app.getString(R.string.completion_sent_by_teammate))
    compose
        .onNodeWithTag(C.Tag.COMPLETION_SUBMIT)
        .assertTextContains(app.getString(R.string.completion_sent))
        .assertIsNotEnabled()
    compose.onNodeWithTag(C.Tag.COMPLETION_SCREEN).assertIsDisplayed()
    assertEquals(emptyList<String>(), submitted)
  }

  @Test
  fun failedTextSubmitRetriesTheSameProofAndReportsTheStoredCompletionOnce() {
    completions.forcedFailure = IOException("offline")
    show()
    compose.onNodeWithTag(C.Tag.COMPLETION_ANSWER).performTextInput("  Mochi  ")
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).performClick()
    compose.onNodeWithTag(C.Tag.COMPLETION_SAVE_ERROR).assertIsDisplayed()
    assertEquals("  Mochi  ", viewModel.uiState.value.answer)
    assertEquals(
        emptyList<Any>(),
        runBlocking { completions.observeForVenue(venue.id).first() },
    )
    completions.forcedFailure = null
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).assertTextContains("Try again").performClick()
    compose.waitUntil { submitted.size == 1 }
    val stored = runBlocking { completions.observeForVenue(venue.id).first().single() }
    assertEquals(Proof.Text("Mochi"), stored.proof)
    assertEquals(listOf(stored.id), submitted)
    compose.runOnIdle { venueName = "Updated venue name" }
    compose
        .onNodeWithTag(C.Tag.COMPLETION_SUBMIT)
        .assertTextContains("Proof sent")
        .assertIsNotEnabled()
    assertEquals(listOf(stored.id), submitted)
  }

  @Test
  fun screenCanReloadAfterARepositoryFailure() {
    loadFails = true
    show(expectedStatus = CompletionFormStatus.FAILED)
    compose.onNodeWithText("Could not load this quest. Please try again.").assertIsDisplayed()
    loadFails = false
    compose.onNodeWithText("Try again").performClick()
    compose.onNodeWithTag(C.Tag.COMPLETION_ANSWER).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).assertIsNotEnabled()
  }

  @Test
  fun textPreviewPresentsAnEmptyTextForm() {
    compose.setContent { CompletionTextPreview() }
    compose.onNodeWithText("Find the hidden fox").assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.COMPLETION_ANSWER).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.PROOF_PHOTO).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).assertIsNotEnabled()
  }

  @Test
  fun photoPreviewPresentsCameraAndGalleryInsteadOfAnAnswer() {
    compose.setContent { CompletionPhotoPreview() }
    compose.onNodeWithText("Find the hidden fox").assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.PROOF_PHOTO_TAKE).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.PROOF_PHOTO_GALLERY).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.COMPLETION_ANSWER).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).assertIsNotEnabled()
  }

  @Test
  fun photoRetakeClearsTheViewModelAndRequiresAnotherPhoto() {
    show(ProofType.PHOTO)
    compose.onNodeWithTag(C.Tag.PROOF_PHOTO_GALLERY).assertIsDisplayed()
    compose.runOnIdle { viewModel.pickPhoto(Uri.parse("content://proof/picked")) }
    compose.onNodeWithTag(C.Tag.PROOF_PHOTO_RETAKE).performClick()
    compose.runOnIdle { assertNull(viewModel.uiState.value.photo) }
    compose.onNodeWithTag(C.Tag.PROOF_PHOTO_GALLERY).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).assertIsNotEnabled()
  }

  @Test
  fun systemBackCannotLeaveWhileTheLocationCheckIsSaving() {
    locations.preciseGate = CompletableDeferred()
    show()
    compose.onNodeWithTag(C.Tag.COMPLETION_ANSWER).performTextInput("Mochi")
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).performClick()
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).assertIsNotEnabled()
    compose.runOnIdle {
      compose.activity.onBackPressedDispatcher.onBackPressed()
      assertFalse(compose.activity.isFinishing)
      assertEquals(0, backCalls)
      locations.preciseLocation = null
      locations.preciseGate!!.complete(Unit)
    }
    compose.onNodeWithContentDescription("Back").performClick()
    assertEquals(1, backCalls)
    assertEquals(0, submitted.size)
  }
}
