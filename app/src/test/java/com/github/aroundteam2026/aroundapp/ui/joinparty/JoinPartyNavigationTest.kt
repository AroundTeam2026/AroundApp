// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.joinparty

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.connectivity.FakeConnectivityRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeCompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.navigation.AroundApp
import com.github.aroundteam2026.aroundapp.ui.navigation.Tab
import com.github.aroundteam2026.aroundapp.ui.navigation.pressBack
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewScreen
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewViewModel
import com.github.aroundteam2026.aroundapp.ui.venuepage.VenuePageOnFakes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the Join a party screen inside the whole app on in-memory repositories: it opens from the
 * Quests tab, closes back to it, and a party joined shows there. A friend has booked the quest
 * `fox` of the venue `venue-1`, and its party code is [CODE].
 */
@RunWith(AndroidJUnit4::class)
class JoinPartyNavigationTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var navController: NavHostController

  /** What closes the Join a party screen, as the app hands it to the screen. */
  private lateinit var closeScreen: () -> Unit

  private val questRepository =
      FakeQuestRepository(initialQuests = listOf(testQuest("fox", "venue-1")))
  private val reservations = FakeReservationRepository(quests = questRepository, newCode = { CODE })
  private val venues = FakeVenueRepository(listOf(testVenue(id = "venue-1", name = "Café Lumen")))

  private fun launch() {
    runBlocking {
      reservations.createReservation(newPendingReservation("fox", "venue-1", "Ada", 0L), "friend")
    }
    val overviewViewModel =
        OverviewViewModel(
            EXPLORER,
            reservations,
            questRepository,
            venues,
            reservations.party,
            FakeCompletionRepository(),
        )
    composeTestRule.setContent {
      navController = rememberNavController()
      AroundApp(
          explorerUid = EXPLORER,
          onSignOut = {},
          navController = navController,
          overview = { onOpenQuest, onJoinParty ->
            OverviewScreen(overviewViewModel, onOpenQuest, onJoinParty)
          },
          joinParty = { close, openQuest ->
            closeScreen = close
            JoinParty(
                close,
                openQuest,
                viewModelFactory {
                  initializer {
                    JoinPartyViewModel(
                        EXPLORER,
                        reservations,
                        questRepository,
                        venues,
                        reservations.party,
                        FakeConnectivityRepository(),
                    )
                  }
                },
            )
          },
          venuePage = { venueId, questId, onBack, onSubmitProof ->
            VenuePageOnFakes(
                venueId,
                onBack,
                onSubmitProof,
                approvedQuestId = "fox",
                arrivalQuestId = questId,
            )
          },
      )
    }
  }

  private fun openJoinParty() {
    composeTestRule.onNodeWithTag(C.Tag.OVERVIEW_JOIN_PARTY).performClick()
    composeTestRule.waitForIdle()
  }

  /** Types [CODE] and waits for the party it leads to to show. */
  private fun typeTheCode() {
    composeTestRule.onNodeWithTag(C.Tag.JOIN_PARTY_CODE).performTextInput("7kq m42")
    composeTestRule.waitUntil(WAIT_MS) { shown(C.Tag.JOIN_PARTY_FOUND) }
  }

  private fun shown(tag: String) =
      composeTestRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

  /** The Quests tab shows its overview again, with no Join a party screen left behind it. */
  private fun assertBackOnTheOverview() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(Tab.QUESTS.tabTag).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.OVERVIEW_TITLE).assertIsDisplayed()
    assertEquals(false, shown(C.Tag.JOIN_PARTY_SCREEN))
  }

  @Test
  fun theJoinAPartyButtonOpensTheScreenWhileTheQuestsTabStaysSelected() {
    launch()

    openJoinParty()

    composeTestRule.onNodeWithTag(C.Tag.JOIN_PARTY_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tab.QUESTS.tabTag).assertIsSelected()
  }

  @Test
  fun theBackArrowReturnsToTheOverview() {
    launch()
    openJoinParty()

    composeTestRule.onNodeWithContentDescription("Back").performClick()

    assertBackOnTheOverview()
  }

  @Test
  fun systemBackReturnsToTheOverview() {
    launch()
    openJoinParty()

    composeTestRule.pressBack()

    assertBackOnTheOverview()
  }

  // A fast double tap must not stack a second screen that back would then show
  @Test
  fun openingTheScreenWhileItIsOpenOpensNothingMore() {
    launch()
    openJoinParty()

    composeTestRule.runOnUiThread { navController.navigateToJoinParty() }
    composeTestRule.waitForIdle()
    composeTestRule.pressBack()

    assertBackOnTheOverview()
  }

  // The joined party shows again as the screen leaves, so closing must not pop the tab below too
  @Test
  fun closingTheScreenTwiceClosesItOnce() {
    launch()
    openJoinParty()

    composeTestRule.runOnUiThread {
      closeScreen()
      closeScreen()
    }

    assertBackOnTheOverview()
  }

  @Test
  fun joiningAPartyClosesTheScreenAndItsQuestShowsOnTheQuestsTab() {
    launch()
    openJoinParty()
    typeTheCode()

    composeTestRule.onNodeWithTag(C.Tag.JOIN_PARTY_SUBMIT).performClick()
    composeTestRule.waitUntil(WAIT_MS) { !shown(C.Tag.JOIN_PARTY_SCREEN) }
    composeTestRule.waitUntil(WAIT_MS) { shown("${C.Tag.OVERVIEW_ROW}_reservation-1") }

    assertBackOnTheOverview()
    composeTestRule.onNodeWithTag("${C.Tag.OVERVIEW_ROW}_reservation-1").assertIsDisplayed()
  }

  // The explorer may want to see what the quest asks for before joining
  @Test
  fun theFoundPartysCardOpensItsQuestsDetailsAndBackReturnsToTheScreen() {
    launch()
    openJoinParty()
    typeTheCode()

    composeTestRule.onNodeWithTag("${C.Tag.OVERVIEW_ROW}_reservation-1").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(C.Tag.QUEST_DETAIL_SCREEN).assertIsDisplayed()
    composeTestRule.pressBack()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(C.Tag.JOIN_PARTY_SCREEN).assertIsDisplayed()
  }

  private companion object {
    const val EXPLORER = "explorer"
    const val CODE = "7KQM42"
    const val WAIT_MS = 5_000L
  }
}
