// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.dashboard.dashboardDestination
import com.github.aroundteam2026.aroundapp.ui.dashboard.navigateToDashboard
import com.github.aroundteam2026.aroundapp.ui.quest.create.CreateQuestViewModel
import com.github.aroundteam2026.aroundapp.ui.quest.create.createQuestDestination
import com.github.aroundteam2026.aroundapp.ui.quest.create.navigateToCreateQuest
import com.github.aroundteam2026.aroundapp.ui.venuepage.navigateToVenuePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests [Routes]: explorer routes are registered in [AroundApp], venue routes are not, and every
 * route opens its screen.
 */
@RunWith(AndroidJUnit4::class)
class RoutesTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var navController: NavHostController

  /** Shows the explorer's app. */
  private fun launchApp() {
    composeTestRule.setContent {
      navController = rememberNavController()
      AroundApp(onSignOut = {}, navController = navController)
    }
    composeTestRule.waitForIdle()
  }

  /** Shows a stand-in for the venue's NavHost, which registers the venue routes. */
  private fun launchVenueGraph() {
    composeTestRule.setContent {
      navController = rememberNavController()
      NavHost(navController, startDestination = VENUE_START) {
        composable(VENUE_START) { Text("Venue home", Modifier.testTag(VENUE_START)) }
        createQuestDestination(onClose = { navController.popBackStack() }, factory = formFactory)
        dashboardDestination()
      }
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

  private fun registered(routes: List<String>) = composeTestRule.runOnUiThread {
    routes.filter { navController.graph.findNode(it) != null }
  }

  /** Checks that the stub tagged [tag] is shown. */
  private fun assertStub(tag: String) {
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

  // Catches an explorer route added to Routes but never registered, which would crash when opened
  @Test
  fun everyExplorerRouteIsRegisteredInTheApp() {
    launchApp()
    assertEquals(Routes.EXPLORER, registered(Routes.EXPLORER))
  }

  // Venue screens live in the venue's own NavHost, so an explorer can't reach them
  @Test
  fun noVenueRouteIsRegisteredInTheExplorersApp() {
    launchApp()
    assertEquals(emptyList<String>(), registered(Routes.VENUE))
  }

  @Test
  fun venuePageOpensTheVenuesPage() {
    launchApp()
    open { navigateToVenuePage("venue-1") }

    composeTestRule.onNodeWithTag(C.Tag.VENUE_PAGE_SCREEN).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(composeTestRule.activity.getString(R.string.coming_soon))
        .assertDoesNotExist()
  }

  // An id with a slash would otherwise be read as two path segments and match no route
  @Test
  fun venuePageReceivesAnIdWithReservedCharacters() {
    launchApp()
    open { navigateToVenuePage("a b/c?d") }

    composeTestRule.onNodeWithTag(C.Tag.VENUE_PAGE_SCREEN).assertIsDisplayed()
    val venueId = composeTestRule.runOnUiThread {
      navController.currentBackStackEntry?.arguments?.getString(Routes.VENUE_ID)
    }
    assertEquals("a b/c?d", venueId)
  }

  // "venue_page/" matches no route: opening it would crash the app
  @Test
  fun aBlankVenueIdOpensNothingInsteadOfCrashing() {
    launchApp()
    open { navigateToVenuePage(" ") }

    composeTestRule.onNodeWithTag(Tab.QUESTS.screenTag).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.VENUE_PAGE_SCREEN).assertDoesNotExist()
  }

  @Test
  fun theVenuePageRouteRefusesABlankId() {
    assertThrows(IllegalArgumentException::class.java) { Routes.venuePage("") }
  }

  // The venue's page must not be a dead end: Back returns to where it was opened from
  @Test
  fun backFromTheVenuePageReturnsToThePreviousScreen() {
    launchApp()
    open { navigateToVenuePage("venue-1") }
    pressBack()

    composeTestRule.onNodeWithTag(Tab.QUESTS.screenTag).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.VENUE_PAGE_SCREEN).assertDoesNotExist()
  }

  @Test
  fun theVenuePagesBackArrowReturnsToThePreviousScreen() {
    launchApp()
    open { navigateToVenuePage("venue-1") }
    composeTestRule.onNodeWithTag(C.Tag.VENUE_PAGE_BACK).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(Tab.QUESTS.screenTag).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.VENUE_PAGE_SCREEN).assertDoesNotExist()
  }

  @Test
  fun createQuestOpensTheFormInTheVenueGraph() {
    launchVenueGraph()
    open { navigateToCreateQuest() }
    composeTestRule.onNodeWithTag(C.Tag.CREATE_QUEST_SCREEN).assertIsDisplayed()
  }

  @Test
  fun dashboardOpensItsStubInTheVenueGraph() {
    launchVenueGraph()
    open { navigateToDashboard() }
    assertStub(C.Tag.DASHBOARD_STUB)
  }

  // Single-top: opening the same stub twice needs only one Back to leave it
  @Test
  fun openingTheSameStubTwiceDoesNotStackIt() {
    launchVenueGraph()
    open { navigateToCreateQuest() }
    open { navigateToCreateQuest() }
    pressBack()

    composeTestRule.onNodeWithTag(VENUE_START).assertIsDisplayed()
  }

  private companion object {
    const val VENUE_START = "venue_start"

    /** The create-quest form on fakes, so the route needs no Firebase. */
    val formFactory = viewModelFactory {
      initializer {
        CreateQuestViewModel(FakeAuthRepository(), FakeVenueRepository(), FakeQuestRepository())
      }
    }
  }
}
