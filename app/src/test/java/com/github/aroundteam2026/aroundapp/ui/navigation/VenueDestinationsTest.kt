// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.create.createQuestFactoryOnFakes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests [venueDestinations]: the venue home, and the create-quest form it opens full screen. */
@RunWith(AndroidJUnit4::class)
class VenueDestinationsTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private var signOuts = 0
  private lateinit var navController: NavHostController

  /** A venue NavHost that opens on the home, with the form on fakes. */
  @Before
  fun launch() {
    composeTestRule.setContent {
      navController = rememberNavController()
      NavHost(navController, startDestination = Routes.VENUE_HOME) {
        venueDestinations(navController, { signOuts++ }, createQuestFactoryOnFakes())
      }
    }
  }

  private fun openTheForm() {
    composeTestRule.onNodeWithTag(VenueTab.QUESTS.tabTag).performClick()
    composeTestRule.onNodeWithTag(C.Tag.VENUE_NEW_QUEST).performClick()
    composeTestRule.waitForIdle()
  }

  private fun currentRoute() = composeTestRule.runOnUiThread {
    navController.currentDestination?.route
  }

  /** The form is gone, and the home shows the Quests tab it was opened from. */
  private fun assertBackOnTheQuestsTab() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(C.Tag.CREATE_QUEST_SCREEN).assertDoesNotExist()
    composeTestRule.onNodeWithTag(VenueTab.QUESTS.tabTag).assertIsSelected()
    composeTestRule.onNodeWithTag(VenueTab.QUESTS.screenTag).assertIsDisplayed()
  }

  @Test
  fun theHomeOpensOnTheDashboard() {
    composeTestRule.onNodeWithTag(C.Tag.VENUE_HOME).assertIsDisplayed()
    composeTestRule.onNodeWithTag(VenueTab.DASHBOARD.tabTag).assertIsSelected()
  }

  // Full screen: the home's bottom bar would otherwise sit under the form's "Create quest" bar
  @Test
  fun newQuestOpensTheFormAboveTheHome() {
    openTheForm()

    composeTestRule.onNodeWithTag(C.Tag.CREATE_QUEST_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.VENUE_HOME).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.VENUE_NAV_BAR).assertDoesNotExist()
  }

  // Saving calls the same onClose; CreateQuestDestinationTest checks that it does
  @Test
  fun closingTheFormReturnsToTheQuestsTab() {
    openTheForm()
    composeTestRule.onNodeWithTag(C.Tag.CREATE_QUEST_CLOSE).performClick()

    assertBackOnTheQuestsTab()
  }

  @Test
  fun closingTheFormLeavesOnlyTheHomeOnTheBackStack() {
    openTheForm()
    assertEquals(Routes.CREATE_QUEST, currentRoute())

    composeTestRule.onNodeWithTag(C.Tag.CREATE_QUEST_CLOSE).performClick()
    composeTestRule.waitForIdle()

    assertEquals(Routes.VENUE_HOME, currentRoute())
    assertNull(composeTestRule.runOnUiThread { navController.previousBackStackEntry })
  }

  @Test
  fun backFromTheFormReturnsToTheQuestsTab() {
    openTheForm()
    composeTestRule.pressBack()

    assertBackOnTheQuestsTab()
  }

  // The form stays on screen, with X enabled, while it fades out: the second tap must do nothing
  @Test
  fun aDoubleTapOnCloseStaysOnTheQuestsTab() {
    openTheForm()
    composeTestRule.mainClock.autoAdvance = false
    composeTestRule.onNodeWithTag(C.Tag.CREATE_QUEST_CLOSE).performClick()
    composeTestRule.mainClock.advanceTimeBy(DOUBLE_TAP_GAP_MS)
    composeTestRule.onNodeWithTag(C.Tag.CREATE_QUEST_CLOSE).performClick()
    composeTestRule.mainClock.autoAdvance = true

    assertBackOnTheQuestsTab()
  }

  @Test
  fun theProfileTabSignsTheVenueOut() {
    composeTestRule.onNodeWithTag(VenueTab.PROFILE.tabTag).performClick()
    composeTestRule.onNodeWithTag(C.Tag.PROFILE_SIGN_OUT).performClick()

    assertEquals(1, signOuts)
  }

  private companion object {
    const val DOUBLE_TAP_GAP_MS = 100L
  }
}
