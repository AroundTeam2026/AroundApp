// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.overview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.aroundteam2026.aroundapp.model.TEST_EXPLORER_NAME
import com.github.aroundteam2026.aroundapp.model.party.FakePartyRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w413dp-h917dp-mdpi")
class OverviewScreenTest {
  @get:Rule val compose = createComposeRule()

  private val opened = mutableListOf<String>()
  private var retries = 0
  private val cancelled = mutableListOf<String>()

  private fun show(state: OverviewUiState) {
    compose.setContent {
      AroundAppTheme {
        OverviewContent(
            state,
            onOpenVenue = { opened += it },
            onRetry = { retries++ },
            onCancel = { cancelled += it },
            clock = CLOCK,
        )
      }
    }
  }

  private fun loaded(vararg rows: OverviewRow) =
      OverviewUiState(OverviewStatus.LOADED, rows.toList())

  @Test
  fun loadingShowsTheHeaderTitleAndASpinner() {
    show(OverviewUiState())

    compose.onNodeWithTag(C.Tag.AROUND_HEADER).assertIsDisplayed()
    compose.onNodeWithContentDescription("Around").assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.OVERVIEW_TITLE).assertTextEquals("My quests")
    compose.onNodeWithTag(C.Tag.OVERVIEW_COUNT).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.OVERVIEW_LOADING).assertIsDisplayed()
    listOf(C.Tag.OVERVIEW_LIST, C.Tag.OVERVIEW_EMPTY, C.Tag.OVERVIEW_FAILED).forEach {
      compose.onNodeWithTag(it).assertDoesNotExist()
    }
  }

  @Test
  fun noAcceptedQuestShowsTheEmptyState() {
    show(loaded())

    compose.onNodeWithTag(C.Tag.OVERVIEW_EMPTY).assertIsDisplayed()
    compose.onNode(hasText("No quests yet")).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.OVERVIEW_COUNT).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.OVERVIEW_LIST).assertDoesNotExist()
  }

  @Test
  fun aFailureShowsRetry() {
    show(OverviewUiState(OverviewStatus.FAILED))

    compose.onNodeWithTag(C.Tag.OVERVIEW_FAILED).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.OVERVIEW_RETRY).performClick()

    assertEquals(1, retries)
    compose.onNodeWithTag(C.Tag.OVERVIEW_LIST).assertDoesNotExist()
  }

  @Test
  fun theCountSaysHowManyQuestsAreAccepted() {
    show(loaded(row(), row(id = "reservation-2")))

    compose.onNodeWithTag(C.Tag.OVERVIEW_COUNT).assertTextEquals("2 accepted")
  }

  @Test
  fun tappingARowOpensItsVenue() {
    show(loaded(row(), row(id = "reservation-2", venueId = "bakery")))

    compose.onNodeWithTag("${C.Tag.OVERVIEW_ROW}_reservation-2").performClick()

    assertEquals(listOf("bakery"), opened)
  }

  @Test
  fun confirmingACancelCancelsThatCardsReservation() {
    show(loaded(row(), row(id = "reservation-2", venueId = "bakery")))

    cancelButtonOf("reservation-2").performClick()
    compose.onNodeWithTag(C.Tag.OVERVIEW_CANCEL_CONFIRM).performClick()

    assertEquals(listOf("reservation-2"), cancelled)
    assertEquals(emptyList<String>(), opened)
  }

  @Test
  fun aCardBeingCancelledHasItsButtonDisabled() {
    show(loaded(row(), row(id = "reservation-2")).copy(cancellingIds = setOf("reservation-2")))

    cancelButtonOf("reservation-2").assertIsNotEnabled()
    cancelButtonOf(ID).assertIsEnabled()
  }

  @Test
  fun cancellingOnTheScreenCancelsTheReservationAndItsCardLeaves() {
    val party = FakePartyRepository()
    val reservations = FakeReservationRepository(party = party)
    runBlocking {
      reservations.createReservation(
          newPendingReservation("fox", CAFE, "explorer", TEST_EXPLORER_NAME, SLOT),
          "explorer",
      )
    }
    val viewModel =
        OverviewViewModel(
            "explorer",
            reservations,
            FakeQuestRepository(initialQuests = listOf(testQuest(id = "fox", venueId = CAFE))),
            FakeVenueRepository(listOf(testVenue(id = CAFE))),
            party,
        )
    compose.setContent { AroundAppTheme { OverviewScreen(viewModel, onOpenVenue = {}) } }

    compose.onNode(hasTestTag(C.Tag.OVERVIEW_ROW_CANCEL), useUnmergedTree = true).performClick()
    compose.onNodeWithTag(C.Tag.OVERVIEW_CANCEL_CONFIRM).performClick()

    compose.onNodeWithTag(C.Tag.OVERVIEW_EMPTY).assertIsDisplayed()
  }

  @Test
  fun theScreenShowsWhatItsViewModelLoads() {
    val party = FakePartyRepository()
    val reservations = FakeReservationRepository(party = party)
    runBlocking {
      reservations.createReservation(
          newPendingReservation("fox", CAFE, "explorer", TEST_EXPLORER_NAME, SLOT),
          "explorer",
      )
    }
    val viewModel =
        OverviewViewModel(
            "explorer",
            reservations,
            FakeQuestRepository(initialQuests = listOf(testQuest(id = "fox", venueId = CAFE))),
            FakeVenueRepository(listOf(testVenue(id = CAFE))),
            party,
        )
    compose.setContent { AroundAppTheme { OverviewScreen(viewModel, onOpenVenue = {}) } }

    compose.onNodeWithTag(C.Tag.OVERVIEW_LIST).assertIsDisplayed()
    compose
        .onNode(hasTestTag(C.Tag.OVERVIEW_ROW_TITLE), useUnmergedTree = true)
        .assertTextEquals(testQuest(id = "fox").title)
  }

  /** The cancel button is a clickable of its own, so the card's merged node leaves it out. */
  private fun cancelButtonOf(reservationId: String) =
      compose.onNode(
          hasTestTag(C.Tag.OVERVIEW_ROW_CANCEL) and
              hasAnyAncestor(hasTestTag("${C.Tag.OVERVIEW_ROW}_$reservationId")),
          useUnmergedTree = true,
      )

  private fun row(
      id: String = ID,
      venueId: String = CAFE,
      status: OverviewRowStatus = OverviewRowStatus.WAITING,
      partySize: Int = 1,
      address: String? = "Rue du Pont 1",
      reward: Reward? = Reward.FreeItem("Coffee", specifics = null),
      slotStart: Long = SLOT,
  ) =
      OverviewRow(
          reservationId = id,
          questId = "fox",
          venueId = venueId,
          questTitle = "Find the hidden fox",
          reward = reward,
          venueName = "Café Lumen",
          address = address,
          avatar = VenueAvatar.Initials("CL"),
          slotStart = slotStart,
          partySize = partySize,
          status = status,
      )

  private companion object {
    const val ID = "reservation-1"
    const val CAFE = "cafe"
    /** 15 January 2027, 08:00 UTC. */
    const val SLOT = 1_800_000_000_000L
    val UTC: ZoneOffset = ZoneOffset.UTC
    /** Two hours after [SLOT], the same day. */
    val CLOCK: Clock = Clock.fixed(Instant.ofEpochMilli(SLOT + 2 * 3_600_000L), UTC)
  }
}
