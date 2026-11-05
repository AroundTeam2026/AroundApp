// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.SaveState
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class CompletionScreenTest {
  @get:Rule val compose = createComposeRule()
  private var state by
      mutableStateOf(
          CompletionUiState(
              status = CompletionFormStatus.READY,
              questTitle = "Find the hidden fox",
              proofType = ProofType.TEXT,
              requirements = "Order any drink.",
          )
      )
  private var submits = 0
  private var retries = 0
  private var requests = 0

  private fun capture(name: String) {
    val image = compose.onNodeWithTag("completion_screen").captureToImage()
    val output = File("build/reports/completion-$name.png")
    output.parentFile?.mkdirs()
    output.outputStream().use {
      image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
    }
  }

  private fun show(refused: Boolean = false, dark: Boolean = false) {
    compose.setContent {
      AroundAppTheme(darkTheme = dark) {
        CompletionContent(
            state,
            "Café Lumière",
            { state = state.copy(answer = it) },
            { submits++ },
            {},
            { retries++ },
            { requests++ },
            preciseLocationRefused = refused,
            photoPicker = {
              ProofPhotoBox(
                  "Café Lumière",
                  state.photo != null,
                  null,
                  false,
                  {},
                  {},
                  {},
                  { state = state.copy(photo = null) },
                  enabled = state.isEditable,
              )
            },
        )
      }
    }
  }

  @Test
  fun textProofRequiresNonblankAnswerAndSends() {
    show()
    compose.onNodeWithTag("completion_submit").assertIsNotEnabled()
    compose.onNodeWithTag("completion_answer").performTextInput("   ")
    compose.onNodeWithTag("completion_submit").assertIsNotEnabled()
    compose.onNodeWithTag("completion_answer").performTextInput("Mochi")
    compose.onNodeWithTag("completion_submit").performClick()
    assertEquals(1, submits)
  }

  @Test
  fun photoProofUsesPickerAndRequiresPhoto() {
    state = state.copy(proofType = ProofType.PHOTO)
    show()
    compose.onNodeWithTag("completion_answer").assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.PROOF_PHOTO).assertIsDisplayed()
    capture("photo-light")
    compose.onNodeWithTag("completion_submit").assertIsNotEnabled()
    compose.runOnIdle { state = state.copy(photo = Uri.parse("content://proof/photo")) }
    compose.onNodeWithTag("completion_submit").assertIsEnabled()
    compose.onNodeWithTag(C.Tag.PROOF_PHOTO_RETAKE).performClick()
    compose.onNodeWithTag("completion_submit").assertIsNotEnabled()
  }

  @Test
  fun failedSubmissionKeepsAnswerAndCanRetryInDarkTheme() {
    state = state.copy(answer = "Mochi", saveState = SaveState.Failed)
    show(dark = true)
    capture("text-error-dark")
    compose
        .onNodeWithText(
            "Your proof could not be sent. Your answer or photo is still here. Try sending it again."
        )
        .assertIsDisplayed()
    compose.onNodeWithTag("completion_answer").assertTextContains("Mochi")
    compose.onNodeWithTag("completion_submit").performClick()
    assertEquals(1, submits)
  }

  @Test
  fun savingLocksFormAndShowsProgress() {
    state = state.copy(answer = "Mochi", saveState = SaveState.Saving)
    show()
    compose.onNodeWithText("Sending…").assertIsDisplayed()
    compose.onNodeWithTag("completion_submit").assertIsNotEnabled()
    compose.onNodeWithTag("completion_answer").assertIsNotEnabled()
    compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
  }

  @Test
  fun savingLocksPhotoRetake() {
    state =
        state.copy(
            proofType = ProofType.PHOTO,
            photo = Uri.parse("content://proof/photo"),
            saveState = SaveState.Saving,
        )
    show()
    compose.onNodeWithTag(C.Tag.PROOF_PHOTO_RETAKE).assertIsNotEnabled()
  }

  @Test
  fun locationErrorsHaveDistinctMessagesAndKeepProofForRetry() {
    state = state.copy(answer = "Mochi", locationError = CompletionLocationError.TOO_FAR)
    show()
    compose
        .onNodeWithText(
            "You’re outside this quest’s unlock radius. Move closer to the venue, then try sending your proof again."
        )
        .assertIsDisplayed()
    compose.runOnIdle { state = state.copy(locationError = CompletionLocationError.UNAVAILABLE) }
    compose
        .onNodeWithText(
            "Around cannot get a precise device or venue location, so it cannot check that you’re at the venue. Check location access and try again."
        )
        .assertIsDisplayed()
    compose.onNodeWithText("Enable precise location").performClick()
    assertEquals(1, requests)
    compose.onNodeWithTag("completion_answer").assertTextContains("Mochi")
  }

  @Test
  fun approximateOnlyGrantExplainsPreciseRequirement() {
    show(refused = true)
    compose
        .onNodeWithText(
            "Precise location is needed to complete a quest. Approximate location works for the map, but cannot verify that you’re at the venue."
        )
        .assertIsDisplayed()
    compose.onNodeWithText("Enable precise location").performClick()
    assertEquals(1, requests)
  }

  @Test
  fun failedLoadingCanRetryAndUnavailableCannotSubmit() {
    state = state.copy(status = CompletionFormStatus.FAILED)
    show()
    compose.onNodeWithText("Try again").performClick()
    assertEquals(1, retries)
    compose.runOnIdle { state = state.copy(status = CompletionFormStatus.UNAVAILABLE) }
    compose
        .onNodeWithText("This reservation is no longer available to complete.")
        .assertIsDisplayed()
    compose.onNodeWithTag("completion_submit").assertDoesNotExist()
  }
}
