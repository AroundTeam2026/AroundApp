// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.e2e

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.reservation.Completion
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionRepositoryFirestore
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionStatus
import com.github.aroundteam2026.aroundapp.model.reservation.Proof
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepositoryFirestore
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.completionId
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.User
import com.github.aroundteam2026.aroundapp.model.user.UserRepositoryFirestore
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.testing.SETUP_TIMEOUT_MS
import com.github.aroundteam2026.aroundapp.ui.review.RejectReason
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Where the device is during the journey, and where the venue and its quest are: EPFL. */
private val HERE = Location(46.5191, 6.5668)

/** The explorer's display name, which the rules want their reservation to carry. */
private const val EXPLORER_NAME = "Proof explorer"

/** What the explorer writes as proof. */
private const val ANSWER = "The fox hides behind the third bookshelf"

/**
 * A quest's completion, through the app as it ships on the Auth and Firestore emulators. The venue
 * has approved the explorer's reservation, as [ExplorerJourneyTest] leads up to: the explorer opens
 * the quest's details at the venue and sends a written answer as proof, the venue answers it from
 * its dashboard, and the explorer then sees that answer.
 */
@RunWith(AndroidJUnit4::class)
class ProofJourneyTest : TestCase() {
  @get:Rule val e2e = E2eRule()
  private val composeTestRule = e2e.composeTestRule
  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val id = UUID.randomUUID()
  private val venueEmail = "venue-$id@around.test"
  private val explorerEmail = "explorer-$id@around.test"
  private val venueName = "Proof venue $id"
  private val questTitle = "Proof quest $id"

  /** The seeded venue's id, its owner's uid. */
  private lateinit var venueId: String
  /** The seeded explorer's uid. */
  private lateinit var explorerUid: String
  /** The seeded reservation's id, which Firestore assigns. */
  private lateinit var reservationId: String

  /** The id of the explorer's proof, which the dashboard tags its row with. */
  private val proofId
    get() = completionId(reservationId)

  /**
   * Puts the device at [HERE], inside the quest's radius, and seeds the venue, its quest asking for
   * a written answer, and the explorer's reservation of it, which the venue approved.
   */
  @Before
  fun seedApprovedReservation(): Unit = runBlocking {
    // Two accounts and what they write: about twice what a single account needs
    withTimeout(2 * SETUP_TIMEOUT_MS) {
      mockDeviceLocation(HERE)
      val app = seedApp()
      val quest = seedVenueAndQuest(app, venueEmail, venueName, questTitle, HERE, ProofType.TEXT)
      venueId = quest.venueId
      val auth = Firebase.auth(app)
      val db = Firebase.firestore(app)
      explorerUid =
          auth.createUserWithEmailAndPassword(explorerEmail, E2E_PASSWORD).await().user!!.uid
      UserRepositoryFirestore(db)
          .createUser(User(explorerUid, EXPLORER_NAME, Role.EXPLORER, 0L))
          .getOrThrow()
      val reservations = ReservationRepositoryFirestore(db)
      val pending =
          newPendingReservation(quest.questId, venueId, EXPLORER_NAME, System.currentTimeMillis())
      reservationId = reservations.createReservation(pending, explorerUid).getOrThrow()
      db.waitForPendingWrites().await()
      auth.signOut()
      // Only the venue may approve it
      auth.signInWithEmailAndPassword(venueEmail, E2E_PASSWORD).await()
      reservations.updateStatus(reservationId, ReservationStatus.APPROVED).getOrThrow()
      auth.signOut()
    }
  }

  @After fun stopMockingLocation() = runBlocking { stopMockingDeviceLocation() }

  @Test
  fun theVenueApprovesAWrittenProofAndTheExplorerSeesTheQuestCompleted() = run {
    step("The explorer sends a written answer from the quest's details") { sendTheProof() }
    step("The venue approves it from its dashboard") {
      openTheProofFromTheDashboard()
      composeTestRule.onNodeWithTag(C.Tag.REVIEW_PROOF_APPROVE).performClick()
      // Answering the proof closes the screen, back to the dashboard
      composeTestRule.waitUntilGone(C.Tag.REVIEW_PROOF_SCREEN)
      assertEquals(CompletionStatus.APPROVED, answeredProof().status)
      composeTestRule.signOut(C.Tag.VENUE_PROFILE_TAB)
    }
    step("The explorer's Quests tab shows the quest completed") {
      composeTestRule.signIn(explorerEmail, landsOn = C.Tag.NAV_BAR)
      composeTestRule.seeTheQuestsCard(
          reservationId,
          questTitle,
          R.string.overview_status_completed,
      )
    }
  }

  @Test
  fun theVenueRejectsAWrittenProofAndTheExplorerSeesWhy() = run {
    val reason = context.getString(R.string.reject_proof_reason_no_match)
    step("The explorer sends a written answer from the quest's details") { sendTheProof() }
    step("The venue rejects it with a reason") {
      openTheProofFromTheDashboard()
      composeTestRule.onNodeWithTag(C.Tag.REVIEW_PROOF_REJECT).performClick()
      composeTestRule.waitFor(hasTestTag(C.Tag.REJECT_PROOF_SHEET))
      composeTestRule
          .onNodeWithTag("${C.Tag.REJECT_PROOF_REASON}_${RejectReason.NO_MATCH.name}")
          .performClick()
      composeTestRule.onNodeWithTag(C.Tag.REJECT_PROOF_CONFIRM).assertIsEnabled().performClick()
      // Answering the proof closes the sheet and the screen, back to the dashboard
      composeTestRule.waitUntilGone(C.Tag.REVIEW_PROOF_SCREEN)
      val proof = answeredProof()
      assertEquals(CompletionStatus.REJECTED, proof.status)
      assertEquals(reason, proof.rejectReason)
      composeTestRule.signOut(C.Tag.VENUE_PROFILE_TAB)
    }
    step("The explorer's Quests tab shows the proof rejected") {
      composeTestRule.signIn(explorerEmail, landsOn = C.Tag.NAV_BAR)
      composeTestRule.seeTheQuestsCard(
          reservationId,
          questTitle,
          R.string.overview_status_proof_rejected,
      )
    }
    step("The quest's details give the venue's reason") {
      openTheQuestsDetails()
      composeTestRule.waitFor(
          hasTestTag(C.Tag.QUEST_DETAIL_PROOF_STATUS) and hasAnyDescendant(hasText(reason))
      )
    }
  }

  /**
   * Signs the explorer in, sends [ANSWER] as proof from the quest's details and checks that the
   * server has it, waiting for the venue. Leaves the explorer signed out.
   */
  private fun sendTheProof() {
    composeTestRule.signIn(explorerEmail, landsOn = C.Tag.NAV_BAR)
    openTheQuestsDetails()
    // Offered once the details know the reservation is approved and has no proof yet
    composeTestRule.waitFor(hasTestTag(C.Tag.SUBMIT_PROOF_BUTTON))
    composeTestRule.onNodeWithTag(C.Tag.SUBMIT_PROOF_BUTTON).performClick()
    // The answer's field shows once the form has loaded the reservation and its quest
    composeTestRule.waitFor(hasTestTag(C.Tag.COMPLETION_ANSWER))
    composeTestRule.onNodeWithTag(C.Tag.COMPLETION_ANSWER).performTextInput(ANSWER)
    composeTestRule.closeKeyboard()
    // Sending checks that a fresh fix of the device is inside the quest's radius, then closes the
    // form
    whileReportingLocation(HERE) {
      composeTestRule.onNodeWithTag(C.Tag.COMPLETION_SUBMIT).assertIsEnabled().performClick()
      composeTestRule.waitUntilGone(C.Tag.COMPLETION_SCREEN)
    }
    val sent = context.getString(R.string.quest_detail_proof_pending_title)
    composeTestRule.waitFor(
        hasTestTag(C.Tag.QUEST_DETAIL_PROOF_STATUS) and hasAnyDescendant(hasText(sent))
    )
    val proof = runBlocking {
      withTimeout(SETUP_TIMEOUT_MS) {
        // The app writes without waiting for the server; a write the rules refuse is rolled back,
        // so it never shows up below and the test times out
        Firebase.firestore.waitForPendingWrites().await()
        CompletionRepositoryFirestore(Firebase.firestore)
            .observeForExplorer(explorerUid)
            .first { it.isNotEmpty() }
            .single()
      }
    }
    assertEquals(proofId, proof.id)
    assertEquals(Proof.Text(ANSWER), proof.proof)
    assertEquals(CompletionStatus.PENDING, proof.status)
    composeTestRule.signOut()
  }

  /** Opens the quest's details from its venue's page, which the map's nearby quests lead to. */
  private fun openTheQuestsDetails() {
    composeTestRule.findTheQuestNearby(questTitle)
    composeTestRule.viewTheVenuesPage(venueName, questTitle)
    // The venue has this one quest, so one row offers details
    composeTestRule.onNodeWithTag(C.Tag.VENUE_QUEST_ROW_DETAILS).performClick()
    composeTestRule.waitFor(hasTestTag(C.Tag.QUEST_DETAIL_SCREEN))
  }

  /**
   * Signs the venue in and opens the explorer's proof from the dashboard's proofs to review, then
   * waits until the screen shows the answer and lets the venue answer it.
   */
  private fun openTheProofFromTheDashboard() {
    composeTestRule.signIn(venueEmail, landsOn = C.Tag.VENUE_DASHBOARD_SCREEN)
    val row = hasTestTag("${C.Tag.DASHBOARD_PROOF_ROW}_$proofId")
    composeTestRule.waitFor(row)
    composeTestRule.onNode(row).performScrollTo().performClick()
    composeTestRule.waitFor(hasTestTag(C.Tag.REVIEW_PROOF_ANSWER) and hasText(ANSWER))
    composeTestRule.waitFor(hasTestTag(C.Tag.REVIEW_PROOF_APPROVE) and isEnabled())
  }

  /** The explorer's proof as the server has it, once the venue has answered it. */
  private fun answeredProof(): Completion = runBlocking {
    withTimeout(SETUP_TIMEOUT_MS) {
      Firebase.firestore.waitForPendingWrites().await()
      CompletionRepositoryFirestore(Firebase.firestore)
          .observeForVenue(venueId)
          .first { proofs -> proofs.any { it.status != CompletionStatus.PENDING } }
          .single()
    }
  }
}
