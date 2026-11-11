// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.reservation.Completion
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionStatus
import com.github.aroundteam2026.aroundapp.model.reservation.Proof
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The venue page's own bar offers proof submission only for the picked approved reservation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class VenuePageSubmitProofTest {
  @get:Rule val compose = createComposeRule()

  private val quest = VenuePageQuest("quest", "Find the fox", "", "", ProofType.PHOTO, 1, null)
  private val pending =
      VenuePageUiState(
          status = VenuePageStatus.LOADED,
          venueName = "Café Lumen",
          quests = listOf(quest, quest.copy(questId = "other")),
          selectedQuestId = quest.questId,
          acceptedParties = mapOf(quest.questId to 1),
          reservationIds = mapOf(quest.questId to "reservation"),
      )
  private val approved = pending.copy(approvedIds = setOf("reservation"))
  private val state = mutableStateOf(pending)
  private val submitted = mutableListOf<String>()
  private var accepts = 0

  private fun show(initial: VenuePageUiState) {
    state.value = initial
    compose.setContent {
      AroundAppTheme {
        VenuePageContent(
            state.value,
            onBack = {},
            onQuestTapped = {},
            onRetry = {},
            onAccept = { accepts++ },
            onSubmitProof = { submitted += it },
        )
      }
    }
  }

  @Test
  fun approvedReservationOffersProofAndPassesItsId() {
    show(approved)

    compose
        .onNodeWithTag(C.Tag.SUBMIT_PROOF_BUTTON)
        .assertIsDisplayed()
        .assertIsEnabled()
        .assertTextEquals("Submit proof")
        .performClick()
    compose.onNodeWithTag(C.Tag.ACCEPT_QUEST_BUTTON).assertDoesNotExist()
    assertEquals(listOf("reservation"), submitted)
    assertEquals(0, accepts)
  }

  @Test
  fun pendingReservationWaitsForApprovalThenOffersProof() {
    show(pending)

    compose.onNodeWithTag(C.Tag.SUBMIT_PROOF_BUTTON).assertDoesNotExist()
    compose
        .onNodeWithTag(C.Tag.ACCEPT_QUEST_BUTTON)
        .assertIsNotEnabled()
        .assertTextEquals("Accepted, waiting for the venue")

    compose.runOnIdle { state.value = approved }

    compose.onNodeWithTag(C.Tag.SUBMIT_PROOF_BUTTON).assertIsDisplayed().assertIsEnabled()
  }

  @Test
  fun removingApprovalRemovesTheProofAction() {
    show(approved)

    compose.runOnIdle { state.value = pending }

    compose.onNodeWithTag(C.Tag.SUBMIT_PROOF_BUTTON).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.ACCEPT_QUEST_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun approvalOfAnotherQuestDoesNotOfferProofForThePickedQuest() {
    show(approved.copy(selectedQuestId = "other"))

    compose.onNodeWithTag(C.Tag.SUBMIT_PROOF_BUTTON).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.ACCEPT_QUEST_BUTTON).assertIsEnabled().performClick()
    assertEquals(1, accepts)
    assertEquals(emptyList<String>(), submitted)
  }

  @Test
  fun unreadableReservationsDoNotOfferProofFromStaleApproval() {
    show(approved.copy(acceptAvailable = false))

    compose.onNodeWithTag(C.Tag.SUBMIT_PROOF_BUTTON).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.ACCEPT_QUEST_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun noPickedQuestShowsNoBarEvenWithAnApprovedReservation() {
    show(approved.copy(selectedQuestId = null))

    compose.onNodeWithTag(C.Tag.ACCEPT_QUEST_BAR).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.SUBMIT_PROOF_BUTTON).assertDoesNotExist()
  }

  @Test
  fun onlyAnApprovedProofIsFinalWhileTheOthersOfferANewOne() {
    show(approved)

    CompletionStatus.entries.forEach { status ->
      val proof =
          Completion(
              id = "completion",
              reservationId = "reservation",
              venueId = "venue",
              explorerUid = "explorer",
              proof = Proof.Photo("gs://proofs/fox.jpg"),
              status = status,
              rejectReason = if (status == CompletionStatus.REJECTED) "Too dark" else null,
              submittedAt = 0L,
          )
      compose.runOnIdle { state.value = approved.copy(completions = mapOf("reservation" to proof)) }

      compose.onNodeWithTag(C.Tag.SUBMIT_PROOF_BUTTON).assertDoesNotExist()
      compose.onNodeWithTag(C.Tag.ACCEPT_QUEST_BUTTON).assertDoesNotExist()
      compose.onNodeWithText("Accepted, waiting for the venue").assertDoesNotExist()
      if (status != CompletionStatus.APPROVED) {
        compose
            .onNodeWithTag(C.Tag.SEND_NEW_PROOF_BUTTON)
            .assertIsDisplayed()
            .assertTextEquals("Send new proof")
            .assertIsEnabled()
            .performClick()
      } else {
        compose.onNodeWithTag(C.Tag.ACCEPT_QUEST_BAR).assertDoesNotExist()
        compose.onNodeWithTag(C.Tag.SEND_NEW_PROOF_BUTTON).assertDoesNotExist()
      }
    }
    // The pending and the rejected proof each offered it, and it opened the party's reservation
    assertEquals(listOf("reservation", "reservation"), submitted)
  }

  @Test
  fun unreadableProofsDoNotOfferAnotherSubmission() {
    show(approved.copy(proofsAvailable = false))

    compose.onNodeWithTag(C.Tag.SUBMIT_PROOF_BUTTON).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.ACCEPT_QUEST_BUTTON).assertIsNotEnabled()
  }
}
