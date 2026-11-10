// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.dashboard

import android.content.Context
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.party.FakePartyRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeCompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests approving and declining from the Dashboard tab, through [VenueDashboardScreen] and its
 * [VenueDashboardViewModel] on in-memory repositories: the change of status, a failed answer's
 * snackbar, and Approve disabled for a party below its quest's size.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class VenueDashboardActionsTest {
  @get:Rule val compose = createComposeRule()

  private val party = FakePartyRepository()
  private val quests =
      FakeQuestRepository(
          initialQuests =
              listOf(
                  testQuest(id = QUEST_ID, venueId = VENUE_ID),
                  testQuest(id = GROUP_QUEST_ID, venueId = VENUE_ID, minPartySize = 3),
              )
      )
  // Given the quests, approving checks the party's size, as the rules do
  private val reservations = FakeReservationRepository(party = party, quests = quests)
  private lateinit var viewModel: VenueDashboardViewModel

  /**
   * Stores one pending reservation of the venue's quest [questId] and shows the venue's dashboard.
   */
  private fun showWithOnePending(questId: String = QUEST_ID): String {
    val id = runBlocking {
      reservations
          .createReservation(
              newPendingReservation(questId, VENUE_ID, "Yusuf S.", slotStart = 0L),
              "explorer",
          )
          .getOrThrow()
    }
    viewModel =
        VenueDashboardViewModel(
            FakeAuthRepository(VENUE_ID),
            reservations,
            quests,
            FakeVenueRepository(listOf(testVenue(id = VENUE_ID))),
            party,
            FakeCompletionRepository(),
        )
    compose.setContent { AroundAppTheme { VenueDashboardScreen(viewModel) } }
    compose.waitForIdle()
    return id
  }

  /** The node tagged [tag] inside the row of the reservation [id]. */
  private fun inRow(id: String, tag: String): SemanticsNodeInteraction =
      compose.onNode(
          hasTestTag(tag) and hasAnyAncestor(hasTestTag("${C.Tag.DASHBOARD_ROW}_$id")),
          useUnmergedTree = true,
      )

  private fun status(id: String) = runBlocking {
    reservations.observeForVenue(VENUE_ID).first().single { it.id == id }.status
  }

  @Test
  fun approvingMovesTheReservationToUpcoming() {
    val id = showWithOnePending()

    inRow(id, C.Tag.DASHBOARD_ROW_APPROVE).performClick()
    compose.waitForIdle()

    assertEquals(ReservationStatus.APPROVED, status(id))
    inRow(id, C.Tag.DASHBOARD_ROW_APPROVED).assertIsDisplayed()
    inRow(id, C.Tag.DASHBOARD_ROW_APPROVE).assertDoesNotExist()
    compose
        .onNodeWithTag(C.Tag.DASHBOARD_STAT_PENDING, useUnmergedTree = true)
        .assertTextEquals("0")
  }

  @Test
  fun decliningRemovesTheReservation() {
    val id = showWithOnePending()

    inRow(id, C.Tag.DASHBOARD_ROW_DECLINE).performClick()
    compose.waitForIdle()

    assertEquals(ReservationStatus.REJECTED, status(id))
    compose.onNodeWithTag("${C.Tag.DASHBOARD_ROW}_$id").assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.DASHBOARD_EMPTY).assertIsDisplayed()
  }

  @Test
  fun aFailedAnswerShowsASnackbarOnceAndCanBeRetried() {
    val id = showWithOnePending()
    reservations.forcedFailure = IOException("offline")

    inRow(id, C.Tag.DASHBOARD_ROW_APPROVE).performClick()

    compose.onNodeWithText(string(R.string.dashboard_update_failed)).assertIsDisplayed()
    // Cleared once shown, so a recreated screen doesn't show it again
    assertFalse(viewModel.uiState.value.updateFailed)
    assertEquals(ReservationStatus.PENDING, status(id))
    inRow(id, C.Tag.DASHBOARD_ROW_APPROVE).assertIsEnabled()

    reservations.forcedFailure = null
    inRow(id, C.Tag.DASHBOARD_ROW_APPROVE).performClick()
    compose.waitForIdle()

    assertEquals(ReservationStatus.APPROVED, status(id))
  }

  @Test
  fun aPartyBelowItsQuestsSizeCantBeApprovedButCanBeDeclined() {
    val id = showWithOnePending(GROUP_QUEST_ID)

    inRow(id, C.Tag.DASHBOARD_ROW_APPROVE).assertIsNotEnabled()
    inRow(id, C.Tag.DASHBOARD_ROW_APPROVE_BLOCKED)
        .assertTextEquals(
            ApplicationProvider.getApplicationContext<Context>()
                .resources
                .getQuantityString(R.plurals.dashboard_approve_needs_more, 2, 2)
        )
    inRow(id, C.Tag.DASHBOARD_ROW_APPROVE).performClick()
    compose.waitForIdle()
    assertEquals(ReservationStatus.PENDING, status(id))
    compose.onNodeWithText(string(R.string.dashboard_update_failed)).assertDoesNotExist()

    inRow(id, C.Tag.DASHBOARD_ROW_DECLINE).performClick()
    compose.waitForIdle()

    assertEquals(ReservationStatus.REJECTED, status(id))
  }

  @Test
  fun aPartyThatReachesItsQuestsSizeCanBeApproved() {
    val id = showWithOnePending(GROUP_QUEST_ID)
    runBlocking {
      party.join(id, "friend-1").getOrThrow()
      party.join(id, "friend-2").getOrThrow()
    }
    compose.waitForIdle()

    inRow(id, C.Tag.DASHBOARD_ROW_APPROVE_BLOCKED).assertDoesNotExist()
    inRow(id, C.Tag.DASHBOARD_ROW_APPROVE).assertIsEnabled().performClick()
    compose.waitForIdle()

    assertEquals(ReservationStatus.APPROVED, status(id))
  }

  private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

  private companion object {
    const val VENUE_ID = "cafe"
    const val QUEST_ID = "owl"
    /** A quest for parties of at least 3. */
    const val GROUP_QUEST_ID = "brunch"
  }
}
