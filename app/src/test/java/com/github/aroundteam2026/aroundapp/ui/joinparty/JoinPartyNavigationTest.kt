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
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.reservation.FakeCompletionRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.navigation.AroundApp
import com.github.aroundteam2026.aroundapp.ui.navigation.Tab
import com.github.aroundteam2026.aroundapp.ui.navigation.pressBack
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewScreen
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewViewModel
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the Join a party screen inside the whole app on in-memory repositories: it opens from the
 * Quests tab, closes back to it, and a party joined shows there. A friend has booked the quest
 * `fox` of the venue `venue-1`, and its party code is `7KQM42`.
 */
@RunWith(AndroidJUnit4::class)
class JoinPartyNavigationTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var navController: NavHostController

  /** What closes the Join a party screen, as the app hands it to the screen. */
  private lateinit var closeScreen: () -> Unit

  private val fakes = JoinPartyOnFakes()

  private fun launch() {
    val overviewViewModel =
        OverviewViewModel(
            EXPLORER,
            fakes.reservations,
            fakes.questRepository,
            fakes.venues,
            fakes.reservations.party,
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
          joinParty = { close ->
            closeScreen = close
            JoinParty(
                close,
                viewModelFactory { initializer { fakes.viewModel() } },
            )
          },
      )
    }
  }

  private fun openJoinParty() {
    composeTestRule.onNodeWithTag(C.Tag.OVERVIEW_JOIN_PARTY).performClick()
    composeTestRule.waitForIdle()
  }

  private fun shown(tag: String) =
      composeTestRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

  /** The Quests tab shows its overview again, with no Join a party screen left behind it. */
  private fun assertBackOnTheOverview() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(Tab.QUESTS.tabTag).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.OVERVIEW_TITLE).assertIsDisplayed()
    assertFalse(shown(C.Tag.JOIN_PARTY_SCREEN))
  }

  @Test
  fun theButtonOpensTheScreenWhileTheTabStaysSelectedAndTheBackArrowAndSystemBackReturn() {
    launch()

    openJoinParty()
    composeTestRule.onNodeWithTag(C.Tag.JOIN_PARTY_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tab.QUESTS.tabTag).assertIsSelected()
    composeTestRule
        .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.join_party_back))
        .performClick()
    assertBackOnTheOverview()

    openJoinParty()
    composeTestRule.pressBack()
    assertBackOnTheOverview()
  }

  // A fast double tap must not stack a second screen, and the joined party shows again as the
  // screen leaves, so closing must not pop the tab below too
  @Test
  fun openingAndClosingTheScreenTwiceDoesItOnce() {
    launch()
    openJoinParty()

    composeTestRule.runOnUiThread { navController.navigateToJoinParty() }
    composeTestRule.waitForIdle()
    composeTestRule.pressBack()
    assertBackOnTheOverview()

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
    composeTestRule.onNodeWithTag(C.Tag.JOIN_PARTY_CODE).performTextInput(JoinPartyOnFakes.TYPED)
    composeTestRule.waitUntil(WAIT_MS) { shown(C.Tag.JOIN_PARTY_FOUND) }

    composeTestRule.onNodeWithTag(C.Tag.JOIN_PARTY_SUBMIT).performClick()
    composeTestRule.waitUntil(WAIT_MS) { !shown(C.Tag.JOIN_PARTY_SCREEN) }
    composeTestRule.waitUntil(WAIT_MS) { shown("${C.Tag.OVERVIEW_ROW}_reservation-1") }

    assertBackOnTheOverview()
  }

  private companion object {
    const val EXPLORER = JoinPartyOnFakes.EXPLORER
    const val WAIT_MS = 5_000L
  }
}
