// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.dashboard.navigateToDashboard
import com.github.aroundteam2026.aroundapp.ui.overview.navigateToOverview
import com.github.aroundteam2026.aroundapp.ui.quest.create.navigateToCreateQuest
import com.github.aroundteam2026.aroundapp.ui.venuepage.navigateToVenuePage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests that every route in [Routes] is registered in [AroundApp] and opens its stub. */
@RunWith(AndroidJUnit4::class)
class RoutesTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var navController: NavHostController

  private fun launch() {
    composeTestRule.setContent {
      navController = rememberNavController()
      AroundApp(navController = navController)
    }
    composeTestRule.waitForIdle()
  }

  private fun open(action: NavController.() -> Unit) {
    composeTestRule.runOnUiThread { navController.action() }
    composeTestRule.waitForIdle()
  }

  private fun pressBack() {
    composeTestRule.waitForIdle()
    composeTestRule.runOnUiThread {
      composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    }
    composeTestRule.waitForIdle()
  }

  /** Opens a route with [action] and checks that the stub tagged [tag] is shown. */
  private fun assertOpensStub(tag: String, action: NavController.() -> Unit) {
    launch()
    open(action)
    composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.coming_soon))
        .assertIsDisplayed()
  }

  // Two routes with the same name would make one screen unreachable
  @Test
  fun routesAreUnique() {
    assertEquals(Routes.ALL.size, Routes.ALL.toSet().size)
  }

  // Catches a route added to Routes but never registered, which would crash when opened
  @Test
  fun everyRouteIsRegistered() {
    launch()
    val missing = composeTestRule.runOnUiThread {
      Routes.ALL.filter { navController.graph.findNode(it) == null }
    }
    assertEquals(emptyList<String>(), missing)
  }

  @Test
  fun venuePageOpensItsStub() =
      assertOpensStub(C.Tag.VENUE_PAGE_STUB) { navigateToVenuePage("venue-1") }

  @Test
  fun createQuestOpensItsStub() =
      assertOpensStub(C.Tag.CREATE_QUEST_STUB) { navigateToCreateQuest() }

  @Test
  fun dashboardOpensItsStub() = assertOpensStub(C.Tag.DASHBOARD_STUB) { navigateToDashboard() }

  @Test fun overviewOpensItsStub() = assertOpensStub(C.Tag.OVERVIEW_STUB) { navigateToOverview() }

  // An id with a slash would otherwise be read as two path segments and match no route
  @Test
  fun venuePageReceivesAnIdWithReservedCharacters() {
    launch()
    open { navigateToVenuePage("a b/c?d") }

    composeTestRule.onNodeWithTag(C.Tag.VENUE_PAGE_STUB).assertIsDisplayed()
    val venueId = composeTestRule.runOnUiThread {
      navController.currentBackStackEntry?.arguments?.getString(Routes.VENUE_ID)
    }
    assertEquals("a b/c?d", venueId)
  }

  // A stub must not be a dead end: Back returns to where it was opened from
  @Test
  fun backFromAStubReturnsToThePreviousScreen() {
    launch()
    open { navigateToDashboard() }
    pressBack()

    composeTestRule.onNodeWithTag(Tab.QUESTS.screenTag).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.DASHBOARD_STUB).assertDoesNotExist()
  }

  // Single-top: opening the same stub twice needs only one Back to leave it
  @Test
  fun openingTheSameStubTwiceDoesNotStackIt() {
    launch()
    open { navigateToCreateQuest() }
    open { navigateToCreateQuest() }
    pressBack()

    composeTestRule.onNodeWithTag(Tab.QUESTS.screenTag).assertIsDisplayed()
  }
}
