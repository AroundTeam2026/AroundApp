// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.dashboard

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class VenueDashboardScreenTest {
  @get:Rule val compose = createComposeRule()

  private val approved = mutableListOf<String>()
  private val declined = mutableListOf<String>()
  private var retries = 0
  private val opened = mutableListOf<String>()

  private fun show(state: VenueDashboardUiState) {
    compose.setContent {
      AroundAppTheme {
        VenueDashboardContent(
            state,
            onApprove = { approved += it },
            onDecline = { declined += it },
            onRetry = { retries++ },
            onOpenProof = { opened += it },
            clock = CLOCK,
        )
      }
    }
  }

  private fun loaded(
      needsAnswer: List<DashboardReservation> = emptyList(),
      upcoming: List<DashboardReservation> = emptyList(),
      proofs: List<DashboardProof> = emptyList(),
  ) =
      VenueDashboardUiState(
          status = VenueDashboardStatus.LOADED,
          venueName = "Café Lumière",
          avatar = VenueAvatar.Initials("CL"),
          needsAnswer = needsAnswer,
          upcoming = upcoming,
          activeQuestsCount = 4,
          proofsToReview = proofs,
      )

  private fun string(id: Int, vararg args: Any) =
      ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

  private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

  /** The row of the reservation [id]. */
  private fun row(id: String) = compose.onNodeWithTag("${C.Tag.DASHBOARD_ROW}_$id")

  /** The node tagged [tag] inside the row of [id]. */
  private fun inRow(id: String, tag: String): SemanticsNodeInteraction =
      compose.onNode(
          hasTestTag(tag) and hasAnyAncestor(hasTestTag("${C.Tag.DASHBOARD_ROW}_$id")),
          useUnmergedTree = true,
      )

  private fun SemanticsNodeInteraction.text() =
      fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()

  private fun scrollTo(tag: String) {
    compose.onNodeWithTag(C.Tag.DASHBOARD_LIST).performScrollToNode(hasTestTag(tag))
  }

  @Test
  fun showsASpinnerWhileLoading() {
    show(VenueDashboardUiState())

    compose.onNodeWithTag(C.Tag.DASHBOARD_LOADING).assertIsDisplayed()
  }

  @Test
  fun aFailureOffersToTryAgain() {
    show(VenueDashboardUiState(status = VenueDashboardStatus.FAILED))

    compose.onNodeWithTag(C.Tag.DASHBOARD_FAILED).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.DASHBOARD_RETRY).performClick()

    assertEquals(1, retries)
  }

  @Test
  fun withoutReservationsShowsTheEmptyStateAndNoSections() {
    show(loaded())

    compose.onNodeWithTag(C.Tag.DASHBOARD_EMPTY).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.DASHBOARD_NEEDS_ANSWER).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.DASHBOARD_UPCOMING).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.DASHBOARD_PROOFS).assertDoesNotExist()
  }

  @Test
  fun theHeaderShowsTheVenueAndToday() {
    show(loaded())

    assertEquals(
        string(R.string.dashboard_venue_and_date, "Café Lumière", TODAY),
        node(C.Tag.DASHBOARD_VENUE).text(),
    )
  }

  @Test
  fun aVenueNotReadYetShowsOnlyToday() {
    show(loaded().copy(venueName = "", avatar = null))

    assertEquals(TODAY, node(C.Tag.DASHBOARD_VENUE).text())
  }

  @Test
  fun theTilesShowTheCounts() {
    show(loaded(needsAnswer = listOf(pending("a"), pending("b"))))

    node(C.Tag.DASHBOARD_STAT_PENDING).assertTextEquals("2")
    node(C.Tag.DASHBOARD_STAT_ACTIVE_QUESTS).assertTextEquals("4")
  }

  @Test
  fun approveAndDeclineTellWhichReservation() {
    show(loaded(needsAnswer = listOf(pending("a"), pending("b"))))

    inRow("b", C.Tag.DASHBOARD_ROW_APPROVE).performClick()
    inRow("a", C.Tag.DASHBOARD_ROW_DECLINE).performClick()

    assertEquals(listOf("b"), approved)
    assertEquals(listOf("a"), declined)
  }

  @Test
  fun anAnswerBeingSavedDisablesOnlyItsRowsButtons() {
    show(loaded(needsAnswer = listOf(pending("a"), pending("b"))).copy(savingIds = setOf("a")))

    inRow("a", C.Tag.DASHBOARD_ROW_APPROVE).assertIsNotEnabled()
    inRow("a", C.Tag.DASHBOARD_ROW_DECLINE).assertIsNotEnabled()
    inRow("b", C.Tag.DASHBOARD_ROW_APPROVE).assertIsEnabled()
    inRow("b", C.Tag.DASHBOARD_ROW_DECLINE).assertIsEnabled()
  }

  @Test
  fun bothSectionsShowTheirCounts() {
    show(
        loaded(
            needsAnswer = listOf(pending("a"), pending("b")),
            upcoming = listOf(pending("c").copy(approved = true)),
        )
    )

    compose
        .onNode(hasText("2") and hasAnyAncestor(hasTestTag(C.Tag.DASHBOARD_NEEDS_ANSWER)), true)
        .assertExists()
    scrollTo(C.Tag.DASHBOARD_UPCOMING)
    compose
        .onNode(hasText("1") and hasAnyAncestor(hasTestTag(C.Tag.DASHBOARD_UPCOMING)), true)
        .assertExists()
  }

  @Test
  fun manyPendingReservationsShowTheFirstFewUntilShowAll() {
    val pending = (1..5).map { pending("p$it") }
    show(loaded(needsAnswer = pending, upcoming = listOf(pending("c").copy(approved = true))))

    scrollTo(C.Tag.DASHBOARD_SHOW_ALL)
    compose
        .onNodeWithTag(C.Tag.DASHBOARD_SHOW_ALL)
        .assertTextEquals(string(R.string.dashboard_show_all, 5))
    assertThrows(AssertionError::class.java) { scrollTo("${C.Tag.DASHBOARD_ROW}_p4") }

    compose.onNodeWithTag(C.Tag.DASHBOARD_SHOW_ALL).performClick()

    scrollTo("${C.Tag.DASHBOARD_ROW}_p5")
    row("p5").assertExists()
    compose.onNodeWithTag(C.Tag.DASHBOARD_SHOW_ALL).assertDoesNotExist()
    scrollTo(C.Tag.DASHBOARD_UPCOMING)
  }

  @Test
  fun aFewPendingReservationsNeedNoShowAll() {
    show(loaded(needsAnswer = (1..PENDING_SHOWN_FIRST).map { pending("p$it") }))

    compose.onNodeWithTag(C.Tag.DASHBOARD_SHOW_ALL).assertDoesNotExist()
  }

  @Test
  fun proofsToReviewComeFirstWithTheirCount() {
    show(loaded(needsAnswer = listOf(pending("a")), proofs = listOf(proof("p1"), proof("p2"))))

    compose
        .onNode(hasText("2") and hasAnyAncestor(hasTestTag(C.Tag.DASHBOARD_PROOFS)), true)
        .assertExists()
    assertTrue(top(C.Tag.DASHBOARD_PROOFS) < top(C.Tag.DASHBOARD_NEEDS_ANSWER))
    proofRow("p1").assertIsDisplayed()
    proofRow("p2").assertIsDisplayed()
  }

  @Test
  fun tappingAProofOpensIt() {
    show(loaded(proofs = listOf(proof("p1"), proof("p2"))))

    proofRow("p2").performClick()

    assertEquals(listOf("p2"), opened)
  }

  // A party's proof has its reservation's id, so the two rows must not share a list key
  @Test
  fun aProofAndItsReservationShowSideBySide() {
    show(loaded(upcoming = listOf(pending("a").copy(approved = true)), proofs = listOf(proof("a"))))

    proofRow("a").assertIsDisplayed()
    scrollTo("${C.Tag.DASHBOARD_ROW}_a")
    row("a").assertIsDisplayed()
  }

  @Test
  fun onlyProofsShowNoEmptyState() {
    show(loaded(proofs = listOf(proof("p1"))))

    compose.onNodeWithTag(C.Tag.DASHBOARD_EMPTY).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.DASHBOARD_PROOFS).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.DASHBOARD_NEEDS_ANSWER).assertDoesNotExist()
  }

  @Test
  fun thePreviewShowsItsProofAboveTheReservations() {
    compose.setContent { VenueDashboardPreview() }

    compose.onNodeWithTag(C.Tag.DASHBOARD_PROOFS).assertIsDisplayed()
    proofRow("p").assertIsDisplayed()
    assertTrue(top(C.Tag.DASHBOARD_PROOFS) < top(C.Tag.DASHBOARD_NEEDS_ANSWER))
  }

  /** Where the node tagged [tag] starts, from the top of the screen. */
  private fun top(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

  /** The row of the proof [id]. */
  private fun proofRow(id: String) = compose.onNodeWithTag("${C.Tag.DASHBOARD_PROOF_ROW}_$id")

  private companion object {
    val ZONE: ZoneId = ZoneId.of("Europe/Zurich")
    val NOW: Long = LocalDateTime.of(2026, 10, 28, 14, 0).atZone(ZONE).toInstant().toEpochMilli()
    val CLOCK: Clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZONE)
    const val REQUEST_AGE_MS = 12 * DateUtils.MINUTE_IN_MILLIS

    // NOW's day as the header writes it in English
    const val TODAY = "Wed, Oct 28"

    fun proof(id: String) =
        DashboardProof(
            completionId = id,
            explorerName = "Nora B.",
            questTitle = "Name the café cat",
            partySize = 1,
            proofType = ProofType.TEXT,
            sentAt = NOW - REQUEST_AGE_MS,
        )

    fun pending(id: String, partySize: Int = 2) =
        DashboardReservation(
            reservationId = id,
            questTitle = "Find the hidden fox",
            explorerName = "Yusuf S.",
            partySize = partySize,
            requestedAt = NOW - REQUEST_AGE_MS,
            approved = false,
        )
  }
}
