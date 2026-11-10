// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.review

import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.reservation.Proof
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tests the Review proof screen: what it shows of a photo or a written answer, its buttons and
 * reject sheet, and, on the real ViewModel, a failed answer and a proof once reviewed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w413dp-h917dp-mdpi")
class ReviewProofScreenTest {
  @get:Rule val compose = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()
  private var approvals = 0
  private val rejections = mutableListOf<String>()
  private var retries = 0
  private var backs = 0
  private var state by mutableStateOf(PHOTO)

  /** Shows [initial]; a test changes [state] afterwards to move the screen on. */
  private fun show(initial: ReviewProofUiState) {
    state = initial
    compose.setContent {
      AroundAppTheme {
        ReviewProofContent(
            state,
            onApprove = { approvals++ },
            onReject = { rejections += it },
            onRetry = { retries++ },
            onBack = { backs++ },
            clock = CLOCK,
        )
      }
    }
  }

  private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

  private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

  private fun SemanticsNodeInteraction.text() =
      fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()

  private fun partyOf(size: Int) =
      context.resources.getQuantityString(R.plurals.dashboard_party_size, size, size)

  private fun openTheSheet() {
    compose.onNodeWithTag(C.Tag.REVIEW_PROOF_REJECT).performClick()
    compose.waitForIdle()
  }

  @Test
  fun showsWhoSentThePhotoForWhichQuestAndWhatApprovingGives() {
    show(PHOTO)

    assertEquals(EXPLORER, node(C.Tag.REVIEW_PROOF_EXPLORER).text())
    assertEquals(
        string(R.string.review_proof_party_and_sent, partyOf(4), TWO_MIN_AGO),
        node(C.Tag.REVIEW_PROOF_SENT).text(),
    )
    compose.onNodeWithText(string(R.string.review_proof_pending)).assertIsDisplayed()
    assertEquals("Find the hidden fox", node(C.Tag.REVIEW_PROOF_QUEST).text())
    assertEquals(
        string(R.string.review_proof_requirements, "Order any drink."),
        node(C.Tag.REVIEW_PROOF_REQUIREMENTS).text(),
    )
    assertEquals(
        string(R.string.review_proof_reward_party, EXPLORER, "Free croissant"),
        node(C.Tag.REVIEW_PROOF_HINT).text(),
    )
    compose.onNodeWithTag(C.Tag.REVIEW_PROOF_APPROVE).assertIsEnabled()
    compose.onNodeWithTag(C.Tag.REVIEW_PROOF_REJECT).assertIsEnabled()
  }

  @Test
  fun aDownloadedPhotoIsShown() {
    show(PHOTO)

    node(C.Tag.REVIEW_PROOF_PHOTO_IMAGE)
        .assertIsDisplayed()
        .assertContentDescriptionEquals(string(R.string.review_proof_photo_description, EXPLORER))
  }

  // The ViewModel turns bytes that aren't an image into ProofPhoto.Failed, with a retry
  @Test
  fun onlyAnImageDecodes() = runBlocking {
    assertNotNull(decodePhoto(pngBytes()))
    assertNull(decodePhoto(ByteArray(0)))
    assertNull(decodePhoto(byteArrayOf(1, 2, 3)))
  }

  @Test
  fun tappingTheDownloadedPhotoOpensItFullScreen() {
    show(PHOTO)
    node(C.Tag.REVIEW_PROOF_PHOTO_ZOOM).assertIsDisplayed()

    node(C.Tag.REVIEW_PROOF_PHOTO_IMAGE).performClick()

    node(C.Tag.PROOF_VIEWER).assertIsDisplayed()
    assertEquals(EXPLORER, node(C.Tag.PROOF_VIEWER_TITLE).text())
    assertEquals(
        string(R.string.proof_viewer_quest_and_sent, "Find the hidden fox", TWO_MIN_AGO),
        node(C.Tag.PROOF_VIEWER_SUBTITLE).text(),
    )
  }

  @Test
  fun theFullScreenPhotoOfAnUnknownExplorerJustSentSaysSo() {
    show(PHOTO.copy(explorerName = null, questTitle = null, sentAt = NOW))

    node(C.Tag.REVIEW_PROOF_PHOTO_IMAGE).performClick()

    assertEquals(string(R.string.dashboard_unknown_explorer), node(C.Tag.PROOF_VIEWER_TITLE).text())
    assertEquals(
        string(
            R.string.proof_viewer_quest_and_sent_just_now,
            string(R.string.dashboard_unknown_quest),
        ),
        node(C.Tag.PROOF_VIEWER_SUBTITLE).text(),
    )
  }

  @Test
  fun closingTheFullScreenPhotoReturnsToTheReview() {
    show(PHOTO)
    node(C.Tag.REVIEW_PROOF_PHOTO_IMAGE).performClick()

    node(C.Tag.PROOF_VIEWER_CLOSE).performClick()

    node(C.Tag.PROOF_VIEWER).assertDoesNotExist()
    node(C.Tag.REVIEW_PROOF_SCREEN).assertIsDisplayed()
    assertEquals(0, backs)
  }

  // The full-screen photo takes Back first: it closes, and the venue stays on the review
  @Test
  fun backClosesTheFullScreenPhotoAndStaysOnTheReview() {
    show(PHOTO)
    node(C.Tag.REVIEW_PROOF_PHOTO_IMAGE).performClick()
    compose.waitForIdle()

    Espresso.pressBack()
    compose.waitForIdle()

    node(C.Tag.PROOF_VIEWER).assertDoesNotExist()
    node(C.Tag.REVIEW_PROOF_SCREEN).assertIsDisplayed()
    assertEquals(0, backs)
  }

  @Test
  fun aPhotoNotDownloadedHasNothingToZoom() {
    show(PHOTO.copy(photo = ProofPhoto.Failed))

    node(C.Tag.REVIEW_PROOF_PHOTO_ZOOM).assertDoesNotExist()
  }

  @Test
  fun aPhotoBeingDownloadedShowsNeitherTheImageNorThePlaceholder() {
    show(PHOTO.copy(photo = ProofPhoto.Loading))

    node(C.Tag.REVIEW_PROOF_PHOTO).assertIsDisplayed()
    node(C.Tag.REVIEW_PROOF_PHOTO_IMAGE).assertDoesNotExist()
    compose.onNodeWithText(string(R.string.review_proof_photo)).assertDoesNotExist()
  }

  @Test
  fun aPhotoThatFailedToDownloadCanBeDownloadedAgain() {
    show(PHOTO.copy(photo = ProofPhoto.Failed))

    compose.onNodeWithText(string(R.string.review_proof_photo)).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.REVIEW_PROOF_PHOTO_RETRY).performClick()

    assertEquals(1, retries)
  }

  // An approval is final, so the venue must have seen the photo it approves
  @Test
  fun aPhotoNotShownYetCanOnlyBeRejected() {
    show(PHOTO.copy(photo = ProofPhoto.Failed))

    compose.onNodeWithTag(C.Tag.REVIEW_PROOF_APPROVE).assertIsNotEnabled()
    compose.onNodeWithTag(C.Tag.REVIEW_PROOF_REJECT).assertIsEnabled()
  }

  @Test
  fun aWrittenAnswerIsShownForASoloVisit() {
    show(ANSWER)

    assertEquals("Mochi, the orange one.", node(C.Tag.REVIEW_PROOF_ANSWER).text())
    compose
        .onNodeWithText(string(R.string.review_proof_answer_label).uppercase())
        .assertIsDisplayed()
    node(C.Tag.REVIEW_PROOF_PHOTO).assertDoesNotExist()
    assertEquals(
        string(
            R.string.review_proof_party_and_sent,
            string(R.string.dashboard_proof_solo),
            TWO_MIN_AGO,
        ),
        node(C.Tag.REVIEW_PROOF_SENT).text(),
    )
    assertEquals(
        string(R.string.review_proof_reward_solo, "Nora B.", "Free croissant"),
        node(C.Tag.REVIEW_PROOF_HINT).text(),
    )
  }

  @Test
  fun whatIsNotKnownFallsBackOrIsLeftOut() {
    show(
        ANSWER.copy(
            explorerName = null,
            partySize = 0,
            questTitle = null,
            requirements = null,
            reward = null,
        )
    )

    assertEquals(
        string(R.string.dashboard_unknown_explorer),
        node(C.Tag.REVIEW_PROOF_EXPLORER).text(),
    )
    assertEquals(
        string(R.string.review_proof_sent, TWO_MIN_AGO),
        node(C.Tag.REVIEW_PROOF_SENT).text(),
    )
    assertEquals(string(R.string.dashboard_unknown_quest), node(C.Tag.REVIEW_PROOF_QUEST).text())
    node(C.Tag.REVIEW_PROOF_REQUIREMENTS).assertDoesNotExist()
    node(C.Tag.REVIEW_PROOF_HINT).assertDoesNotExist()
  }

  @Test
  fun aProofSentUnderAMinuteAgoWasSentJustNow() {
    show(PHOTO.copy(sentAt = NOW - 10_000L))

    assertEquals(
        string(R.string.review_proof_party_and_sent_just_now, partyOf(4)),
        node(C.Tag.REVIEW_PROOF_SENT).text(),
    )
  }

  @Test
  fun aProofOfAnUnknownPartySentJustNowSaysOnlyThat() {
    show(PHOTO.copy(partySize = 0, sentAt = NOW))

    assertEquals(string(R.string.review_proof_sent_just_now), node(C.Tag.REVIEW_PROOF_SENT).text())
  }

  @Test
  fun approveAndBackCallTheirActions() {
    show(PHOTO)

    compose.onNodeWithTag(C.Tag.REVIEW_PROOF_APPROVE).performClick()
    compose.onNodeWithTag(C.Tag.REVIEW_PROOF_BACK).performClick()

    assertEquals(1, approvals)
    assertEquals(1, backs)
  }

  @Test
  fun whileAnApprovalIsSavedNothingCanBeTapped() {
    show(PHOTO)
    compose.onNodeWithTag(C.Tag.REVIEW_PROOF_APPROVE).performClick()

    state = PHOTO.copy(saving = true)
    compose.waitForIdle()

    compose.onNodeWithTag(C.Tag.REVIEW_PROOF_APPROVE).assertIsNotEnabled()
    compose.onNodeWithText(string(R.string.review_proof_approving)).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.REVIEW_PROOF_REJECT).assertIsNotEnabled()
    compose.onNodeWithTag(C.Tag.REVIEW_PROOF_BACK).assertIsNotEnabled()
    assertEquals(
        string(R.string.review_proof_sending, EXPLORER),
        node(C.Tag.REVIEW_PROOF_HINT).text(),
    )
  }

  @Test
  fun rejectOpensTheSheetWhichSendsItsReason() {
    show(ANSWER)
    openTheSheet()

    compose.onNodeWithTag(C.Tag.REJECT_PROOF_SHEET).assertIsDisplayed()
    // A written answer: the sheet leaves out the reasons about photos
    compose
        .onNodeWithTag("${C.Tag.REJECT_PROOF_REASON}_${RejectReason.BLURRY.name}")
        .assertDoesNotExist()
    compose
        .onNodeWithTag("${C.Tag.REJECT_PROOF_REASON}_${RejectReason.NO_MATCH.name}")
        .performClick()
    compose.onNodeWithTag(C.Tag.REJECT_PROOF_CONFIRM).performClick()

    assertEquals(listOf(string(R.string.reject_proof_reason_no_match)), rejections)
    compose.onNodeWithTag(C.Tag.REJECT_PROOF_SHEET).assertIsDisplayed()
  }

  @Test
  fun aRejectionBeingSavedShowsOnTheSheetNotOnApprove() {
    show(PHOTO)
    openTheSheet()

    state = PHOTO.copy(saving = true)
    compose.waitForIdle()

    compose.onNodeWithText(string(R.string.reject_proof_rejecting)).assertIsDisplayed()
    compose.onNodeWithText(string(R.string.review_proof_approving)).assertDoesNotExist()
  }

  // Otherwise the sheet would reopen empty once the proof loads again
  @Test
  fun aProofThatFailsToLoadClosesTheSheetForGood() {
    show(PHOTO)
    openTheSheet()

    state = ReviewProofUiState(status = ReviewProofStatus.FAILED)
    compose.waitForIdle()
    state = PHOTO
    compose.waitForIdle()

    compose.onNodeWithTag(C.Tag.REJECT_PROOF_SHEET).assertDoesNotExist()
  }

  // Leaving would cancel the answer being saved, even while the proof can't be shown
  @Test
  fun backWaitsForAnAnswerAlsoWhileTheProofFailsToLoad() {
    show(ReviewProofUiState(status = ReviewProofStatus.FAILED, saving = true))

    compose.onNodeWithTag(C.Tag.REVIEW_PROOF_BACK).assertIsNotEnabled()
  }

  // A party of 0 couldn't be read: it may be solo or a whole party
  @Test
  fun anUnknownPartyGetsTheRewardHintWithoutWho() {
    show(PHOTO.copy(partySize = 0))

    assertEquals(
        string(R.string.review_proof_reward_unknown_party, "Free croissant"),
        node(C.Tag.REVIEW_PROOF_HINT).text(),
    )
  }

  @Test
  fun closingTheSheetRejectsNothing() {
    show(PHOTO)
    openTheSheet()

    compose.onNodeWithTag(C.Tag.REJECT_PROOF_BACK).performClick()
    compose.waitForIdle()

    compose.onNodeWithTag(C.Tag.REJECT_PROOF_SHEET).assertDoesNotExist()
    assertEquals(emptyList<String>(), rejections)
    compose.onNodeWithTag(C.Tag.REVIEW_PROOF_APPROVE).assertIsEnabled()
  }

  @Test
  fun aReviewedProofClosesTheSheet() {
    show(PHOTO)
    openTheSheet()

    state = PHOTO.copy(reviewed = true)
    compose.waitForIdle()

    compose.onNodeWithTag(C.Tag.REJECT_PROOF_SHEET).assertDoesNotExist()
  }

  @Test
  fun aProofBeingReadShowsASpinnerAndNoButtons() {
    show(ReviewProofUiState())

    node(C.Tag.REVIEW_PROOF_LOADING).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.REVIEW_PROOF_APPROVE).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.REVIEW_PROOF_REJECT).assertDoesNotExist()
  }

  @Test
  fun aProofThatCannotBeReadCanBeReadAgain() {
    show(ReviewProofUiState(status = ReviewProofStatus.FAILED))

    node(C.Tag.REVIEW_PROOF_LOAD_FAILED).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.REVIEW_PROOF_APPROVE).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.REVIEW_PROOF_RETRY).performClick()

    assertEquals(1, retries)
  }

  @Test
  fun thePreviewsShowAPhotoAndAnAnswer() {
    var answer by mutableStateOf(false)
    compose.setContent { if (answer) ReviewProofAnswerPreview() else ReviewProofPhotoPreview() }

    compose.onNodeWithTag(C.Tag.REVIEW_PROOF_PHOTO, useUnmergedTree = true).assertIsDisplayed()
    answer = true
    compose.waitForIdle()
    compose.onNodeWithTag(C.Tag.REVIEW_PROOF_ANSWER, useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun aFailedAnswerShowsTheSnackbarOnce() {
    val fakes = ReviewProofOnFakes()
    val viewModel = fakes.reviewProof(runBlocking { fakes.sendProof() })
    compose.setContent { AroundAppTheme { ReviewProofScreen(viewModel, onBack = { backs++ }) } }
    fakes.completions.forcedFailure = IllegalStateException("offline")

    compose.onNodeWithTag(C.Tag.REVIEW_PROOF_APPROVE).performClick()
    compose.waitForIdle()

    compose.onNodeWithText(string(R.string.review_proof_answer_failed)).assertIsDisplayed()
    assertFalse(viewModel.uiState.value.updateFailed)
    assertEquals(0, backs)
  }

  @Test
  fun aReviewedProofReturnsToTheDashboardOnce() {
    val fakes = ReviewProofOnFakes()
    val viewModel = fakes.reviewProof(runBlocking { fakes.sendProof() })
    compose.setContent { AroundAppTheme { ReviewProofScreen(viewModel, onBack = { backs++ }) } }

    compose.onNodeWithTag(C.Tag.REVIEW_PROOF_APPROVE).performClick()
    compose.waitForIdle()

    assertEquals(1, backs)
  }

  /** A real image's bytes, as a downloaded photo would be. */
  private fun pngBytes(): ByteArray {
    val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
    return ByteArrayOutputStream()
        .also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        .toByteArray()
  }

  private companion object {
    const val EXPLORER = "yusuf.s"
    const val NOW = 1_800_000_000_000L
    val CLOCK: Clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneId.of("UTC"))
    val TWO_MIN_AGO =
        DateUtils.getRelativeTimeSpanString(
                NOW - 2 * DateUtils.MINUTE_IN_MILLIS,
                NOW,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            )
            .toString()

    val PHOTO =
        ReviewProofUiState(
            status = ReviewProofStatus.LOADED,
            explorerName = EXPLORER,
            partySize = 4,
            sentAt = NOW - 2 * DateUtils.MINUTE_IN_MILLIS,
            questTitle = "Find the hidden fox",
            requirements = "Order any drink.",
            reward = Reward.FreeItem("croissant", specifics = null),
            proof = Proof.Photo("gs://around/proofs/p.jpg"),
            photo = ProofPhoto.Loaded(ImageBitmap(4, 4)),
        )

    val ANSWER =
        PHOTO.copy(
            explorerName = "Nora B.",
            partySize = 1,
            proof = Proof.Text("Mochi, the orange one."),
            photo = null,
        )
  }
}
