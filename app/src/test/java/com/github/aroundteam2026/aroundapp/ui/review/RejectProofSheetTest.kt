// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.review

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tests the sheet a venue rejects a proof from: the reason it sends, and when it sends none. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class RejectProofSheetTest {
  @get:Rule val compose = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val rejected = mutableListOf<String>()
  private var dismissals = 0
  private var saving by mutableStateOf(false)
  private val snackbar = SnackbarHostState()

  /** Shows the sheet until it is dismissed, as the Review proof screen does. */
  private fun show(proofType: ProofType = ProofType.PHOTO, explorerName: String? = "yusuf.s") {
    var open by mutableStateOf(true)
    compose.setContent {
      AroundAppTheme {
        if (open) {
          RejectProofSheet(
              explorerName,
              proofType,
              saving,
              onReject = { rejected += it },
              onDismiss = {
                dismissals++
                open = false
              },
              snackbarHostState = snackbar,
          )
        }
      }
    }
    compose.waitForIdle()
  }

  private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

  private fun reason(reason: RejectReason) =
      compose.onNodeWithTag("${C.Tag.REJECT_PROOF_REASON}_${reason.name}")

  private fun confirm() = compose.onNodeWithTag(C.Tag.REJECT_PROOF_CONFIRM)

  private fun writeNote(note: String) {
    compose.onNodeWithTag(C.Tag.REJECT_PROOF_NOTE).performTextInput(note)
  }

  @Test
  fun rejectingStaysOffWithoutAReason() {
    show()

    compose.onNodeWithText(string(R.string.reject_proof_title)).assertIsDisplayed()
    compose.onNodeWithText(string(R.string.reject_proof_body, "yusuf.s")).assertIsDisplayed()
    confirm().assertIsNotEnabled()
    compose.onNodeWithTag(C.Tag.REJECT_PROOF_NEEDS_REASON).assertIsDisplayed()
    RejectReason.entries.forEach { reason(it).assertIsDisplayed().assertIsNotSelected() }
  }

  @Test
  fun aPickedReasonIsSentAsIs() {
    show()

    reason(RejectReason.NO_MATCH).performClick().assertIsSelected()
    compose.onNodeWithTag(C.Tag.REJECT_PROOF_NEEDS_REASON).assertDoesNotExist()
    confirm().assertIsEnabled().performClick()

    assertEquals(listOf(string(R.string.reject_proof_reason_no_match)), rejected)
  }

  @Test
  fun aNoteAloneIsSentTrimmed() {
    show()

    writeNote("  Please show the drink  ")
    confirm().performClick()

    assertEquals(listOf("Please show the drink"), rejected)
  }

  @Test
  fun aBlankNoteIsNoReason() {
    show()

    writeNote("   ")

    confirm().assertIsNotEnabled()
  }

  @Test
  fun aPickedReasonAndANoteAreSentTogether() {
    show()

    reason(RejectReason.BLURRY).performClick()
    writeNote("The cup is out of focus")
    confirm().performClick()

    val blurry = string(R.string.reject_proof_reason_blurry)
    assertEquals(
        listOf(string(R.string.reject_proof_reason_with_note, blurry, "The cup is out of focus")),
        rejected,
    )
  }

  @Test
  fun aNoteReplacesOther() {
    show()

    reason(RejectReason.OTHER).performClick()
    confirm().assertIsEnabled()
    writeNote("Wrong café")
    confirm().performClick()

    assertEquals(listOf("Wrong café"), rejected)
  }

  @Test
  fun otherAloneIsSentAsIs() {
    show()

    reason(RejectReason.OTHER).performClick()
    confirm().performClick()

    assertEquals(listOf(string(R.string.reject_proof_reason_other)), rejected)
  }

  @Test
  fun onlyOneReasonIsPickedAndTappingItAgainUnpicksIt() {
    show()

    reason(RejectReason.BLURRY).performClick()
    reason(RejectReason.NOT_AT_VENUE).performClick()
    reason(RejectReason.BLURRY).assertIsNotSelected()
    reason(RejectReason.NOT_AT_VENUE).assertIsSelected().performClick().assertIsNotSelected()

    confirm().assertIsNotEnabled()
  }

  @Test
  fun aWrittenAnswerLeavesOutTheReasonsAboutPhotos() {
    show(ProofType.TEXT, explorerName = null)

    reason(RejectReason.BLURRY).assertDoesNotExist()
    reason(RejectReason.NOT_AT_VENUE).assertDoesNotExist()
    reason(RejectReason.NO_MATCH).assertIsDisplayed()
    reason(RejectReason.OTHER).assertIsDisplayed()
    compose.onNodeWithText(string(R.string.reject_proof_body_unknown)).assertIsDisplayed()
  }

  @Test
  fun theNoteIsCappedSoTheReasonFitsTheRules() {
    show()

    writeNote("a".repeat(NOTE_MAX_LENGTH + 100))
    reason(RejectReason.entries.maxBy { string(it.label).length }).performClick()
    confirm().performClick()

    // firestore.rules refuses a reject reason over 300 characters
    assertTrue(rejected.single().length <= 300)
    assertTrue(rejected.single().endsWith("a".repeat(NOTE_MAX_LENGTH)))
  }

  @Test
  fun backClosesTheSheetWithoutRejecting() {
    show()
    reason(RejectReason.NO_MATCH).performClick()

    compose.onNodeWithTag(C.Tag.REJECT_PROOF_BACK).performClick()
    compose.waitForIdle()

    assertEquals(1, dismissals)
    assertEquals(emptyList<String>(), rejected)
    compose.onNodeWithTag(C.Tag.REJECT_PROOF_SHEET).assertDoesNotExist()
  }

  @Test
  fun systemBackClosesTheSheetWithoutRejecting() {
    show()
    writeNote("Wrong café")

    Espresso.pressBack()
    compose.waitForIdle()

    assertEquals(1, dismissals)
    assertEquals(emptyList<String>(), rejected)
  }

  @Test
  fun draggingTheSheetDownClosesItWithoutRejecting() {
    show()
    reason(RejectReason.NO_MATCH).performClick()

    compose.onNodeWithTag(C.Tag.REJECT_PROOF_SHEET).performTouchInput { swipeDown() }
    compose.waitForIdle()

    assertEquals(1, dismissals)
    assertEquals(emptyList<String>(), rejected)
  }

  @Test
  fun whileSavingNothingCanBeChangedOrDismissed() {
    show()
    reason(RejectReason.NO_MATCH).performClick()
    confirm().performClick()

    saving = true
    compose.waitForIdle()

    confirm().assertIsNotEnabled()
    compose.onNodeWithText(string(R.string.reject_proof_rejecting)).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.REJECT_PROOF_BACK).assertIsNotEnabled()
    compose.onNodeWithTag(C.Tag.REJECT_PROOF_NOTE).assertIsNotEnabled()
    reason(RejectReason.OTHER).assertIsNotEnabled()
    compose.onNodeWithTag(C.Tag.REJECT_PROOF_SHEET).performTouchInput { swipeDown() }
    compose.waitForIdle()
    assertEquals(0, dismissals)
    Espresso.pressBack()
    compose.waitForIdle()
    compose.onNodeWithTag(C.Tag.REJECT_PROOF_SHEET).assertIsDisplayed()
    assertEquals(0, dismissals)
  }

  @Test
  fun aFailureKeepsTheReasonAndShowsTheSnackbarOnTheSheet() {
    show()
    reason(RejectReason.NO_MATCH).performClick()
    confirm().performClick()
    saving = true
    compose.waitForIdle()

    // What the screen does when saving fails
    saving = false
    compose.runOnUiThread { MainScope().launch { snackbar.showSnackbar("Couldn't send") } }
    compose.waitForIdle()

    compose
        .onNode(hasText("Couldn't send") and hasAnyAncestor(hasTestTag(C.Tag.REJECT_PROOF_SHEET)))
        .assertIsDisplayed()
    reason(RejectReason.NO_MATCH).assertIsSelected()
    confirm().assertIsEnabled().performClick()
    assertEquals(2, rejected.size)
  }

  @Test
  fun thePickedPreviewShowsTheFigmaState() {
    compose.setContent { RejectProofPickedPreview() }

    reason(RejectReason.NO_MATCH).assertIsSelected()
    confirm().assertIsEnabled()
  }

  @Test
  fun theEmptyPreviewAsksForAReason() {
    compose.setContent { RejectProofEmptyPreview() }

    compose.onNodeWithTag(C.Tag.REJECT_PROOF_NEEDS_REASON).assertIsDisplayed()
    confirm().assertIsNotEnabled()
  }
}
