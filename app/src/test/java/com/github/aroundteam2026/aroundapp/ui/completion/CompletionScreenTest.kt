// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.reservation.MAX_TEXT_ANSWER_LENGTH
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.SaveState
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class CompletionScreenTest {
  @get:Rule val compose = createComposeRule()

  private val tooLongMessage =
      ApplicationProvider.getApplicationContext<Application>()
          .resources
          .getQuantityString(
              R.plurals.completion_answer_too_long,
              MAX_TEXT_ANSWER_LENGTH,
              MAX_TEXT_ANSWER_LENGTH,
          )
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
  private var servicesEnabled by mutableStateOf(true)

  private fun show(
      refused: Boolean = false,
      dark: Boolean = false,
      venueName: String = "Café Lumière",
  ) {
    compose.setContent {
      AroundAppTheme(darkTheme = dark) {
        CompletionContent(
            state,
            venueName,
            { state = state.copy(answer = it) },
            { submits++ },
            {},
            { retries++ },
            { requests++ },
            preciseLocationRefused = refused,
            locationServicesEnabled = servicesEnabled,
            photoPicker = {
              ProofPhotoBox(
                  venueName,
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
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).assertIsNotEnabled()
    compose.onNodeWithTag(C.Tag.COMPLETION_ANSWER).performTextInput("   ")
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).assertIsNotEnabled()
    compose.onNodeWithTag(C.Tag.COMPLETION_ANSWER).performTextInput("Mochi")
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).performClick()
    assertEquals(1, submits)
  }

  @Test
  fun photoProofUsesPickerAndRequiresPhoto() {
    state = state.copy(proofType = ProofType.PHOTO)
    show()
    compose.onNodeWithTag(C.Tag.COMPLETION_ANSWER).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.PROOF_PHOTO).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).assertIsNotEnabled()
    compose.runOnIdle { state = state.copy(photo = Uri.parse("content://proof/photo")) }
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).assertIsEnabled()
    compose.onNodeWithTag(C.Tag.PROOF_PHOTO_RETAKE).performClick()
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).assertIsNotEnabled()
  }

  @Test
  fun answerLimitExplainsWhyOneCharacterOverCannotBeSentAndClearsAfterEditing() {
    show()
    val answer = compose.onNodeWithTag(C.Tag.COMPLETION_ANSWER)
    answer.performTextInput("x".repeat(MAX_TEXT_ANSWER_LENGTH))
    answer.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).assertIsEnabled()

    answer.performTextInput("x")
    answer.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
    compose.onNodeWithText(tooLongMessage).performScrollTo().assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).assertIsNotEnabled().performClick()
    assertEquals(0, submits)

    answer.performTextReplacement("x".repeat(MAX_TEXT_ANSWER_LENGTH))
    answer.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
    compose.onNodeWithText(tooLongMessage).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).assertIsEnabled()
  }

  @Test
  fun answerLimitUsesTheTrimmedValueThatWillBeStored() {
    show()
    compose
        .onNodeWithTag(C.Tag.COMPLETION_ANSWER)
        .performTextInput("  " + "x".repeat(MAX_TEXT_ANSWER_LENGTH) + "  ")
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).assertIsEnabled()
    compose.onNodeWithText(tooLongMessage).assertDoesNotExist()
  }

  @Test
  fun failedSubmissionKeepsAnswerAndCanRetryInDarkTheme() {
    state = state.copy(answer = "Mochi", saveState = SaveState.Failed)
    show(dark = true)
    compose
        .onNodeWithText("Couldn't send your proof. Check your connection and try again.")
        .assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.COMPLETION_SAVE_ERROR).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.COMPLETION_ANSWER).assertTextContains("Mochi")
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).assertTextContains("Try again").performClick()
    assertEquals(1, submits)
  }

  @Test
  fun failedSubmissionShowsRetryInLightTheme() {
    state = state.copy(answer = "Mochi", saveState = SaveState.Failed)
    show()
    compose.onNodeWithTag(C.Tag.COMPLETION_SAVE_ERROR).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).assertTextContains("Try again").performClick()
    assertEquals(1, submits)
    compose.runOnIdle { state = state.copy(saveState = SaveState.Saving) }
    compose.onNodeWithTag(C.Tag.COMPLETION_SAVE_ERROR).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).assertIsNotEnabled()
    compose.onNodeWithText("Sending…").assertIsDisplayed()
  }

  @Test
  fun venueInitialsUseTheSharedFirstAndLastWordRules() {
    show(venueName = "  Café   du   Lac  ")
    compose.onNodeWithText("CL").assertIsDisplayed()
  }

  @Test
  fun savingLocksFormAndShowsProgress() {
    state = state.copy(answer = "Mochi", saveState = SaveState.Saving)
    show()
    compose.onNodeWithText("Sending…").assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).assertIsNotEnabled()
    compose.onNodeWithTag(C.Tag.COMPLETION_ANSWER).assertIsNotEnabled()
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
            "You’re outside this quest’s unlock radius. Move closer to where the quest takes place, then try sending your proof again."
        )
        .assertIsDisplayed()
    compose.runOnIdle { state = state.copy(locationError = CompletionLocationError.UNAVAILABLE) }
    compose
        .onNodeWithText(
            "Around cannot get a precise device or quest location, so it cannot check that you’re where the quest takes place. Check location access and try again."
        )
        .assertIsDisplayed()
    compose.onNodeWithText("Check location access").performClick()
    assertEquals(1, requests)
    compose.onNodeWithTag(C.Tag.COMPLETION_ANSWER).assertTextContains("Mochi")
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
  fun unavailableWithServicesOffOffersLocationSettingsInLight() {
    locationServicesRecovery(dark = false)
  }

  @Test
  fun unavailableWithServicesOffOffersLocationSettingsInDark() {
    locationServicesRecovery(dark = true)
  }

  private fun locationServicesRecovery(dark: Boolean) {
    state = state.copy(answer = "Mochi", locationError = CompletionLocationError.UNAVAILABLE)
    servicesEnabled = false
    show(dark = dark)
    compose.onNodeWithText("Open location settings").assertIsDisplayed().performClick()
    assertEquals(1, requests)
    compose.runOnIdle { servicesEnabled = true }
    compose.onNodeWithText("Open location settings").assertDoesNotExist()
    compose.onNodeWithText("Check location access").assertIsDisplayed()
  }

  @Test
  fun savedFormCannotEditOrSubmitAgain() {
    state = state.copy(answer = "Mochi", saveState = SaveState.Saved("proof"))
    show()
    compose
        .onNodeWithTag(C.Tag.COMPLETION_SUBMIT)
        .assertTextContains("Proof sent")
        .assertIsNotEnabled()
    compose.onNodeWithTag(C.Tag.COMPLETION_ANSWER).assertIsNotEnabled()
    compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
  }

  @Test
  fun loadingDoesNotOfferProofOrSubmissionControls() {
    state = state.copy(status = CompletionFormStatus.LOADING)
    show()
    compose.onNodeWithTag(C.Tag.COMPLETION_ANSWER).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).assertDoesNotExist()
  }

  @Test
  fun emptyRequirementsDoNotShowAnEmptyCard() {
    state = state.copy(requirements = "")
    show()
    compose.onNodeWithText("WHAT TO DO").assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.COMPLETION_ANSWER).assertIsDisplayed()
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
    compose.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).assertDoesNotExist()
  }
}
