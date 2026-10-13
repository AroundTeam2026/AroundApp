// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.venuepage.navigateToVenuePage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AroundAppTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var navController: NavHostController

  private fun launch(app: @Composable () -> Unit = { AroundApp() }) =
      composeTestRule.setContent(app)

  /** Shows the app with a controller the test can navigate with. */
  private fun launchWithNavController() = launch {
    navController = rememberNavController()
    AroundApp(navController = navController)
  }

  /** Opens a venue's page from the tab in use, as a tap on its card will. */
  private fun openVenuePage() {
    composeTestRule.waitForIdle()
    composeTestRule.runOnUiThread { navController.navigateToVenuePage("venue-1") }
    composeTestRule.waitForIdle()
  }

  private fun select(tab: Tab) {
    composeTestRule.onNodeWithTag(tab.tabTag).performClick()
  }

  private fun pressBack() {
    // Otherwise Back can arrive before the last tab switch finished, and leaves the app instead
    composeTestRule.waitForIdle()
    composeTestRule.runOnUiThread {
      composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    }
  }

  /** Only [tab] is selected, and only its screen is shown. */
  private fun assertShowing(tab: Tab) {
    composeTestRule.waitForIdle()
    Tab.entries.forEach {
      val tabNode = composeTestRule.onNodeWithTag(it.tabTag)
      val screenNode = composeTestRule.onNodeWithTag(it.screenTag)
      if (it == tab) {
        tabNode.assertIsSelected()
        screenNode.assertIsDisplayed()
      } else {
        tabNode.assertIsNotSelected()
        screenNode.assertDoesNotExist()
      }
    }
  }

  @Test
  fun startsOnQuests() {
    launch()
    assertShowing(Tab.QUESTS)
  }

  @Test
  fun eachTabShowsItsScreen() {
    launch()
    Tab.entries.reversed().forEach {
      select(it)
      assertShowing(it)
    }
  }

  @Test
  fun theMapTabShowsTheMap() {
    launch()
    select(Tab.MAP)

    composeTestRule.onNodeWithTag(C.Tag.MAP).assertIsDisplayed()
  }

  @Test
  fun theQuestsTabShowsTheOverview() {
    launch()

    composeTestRule.onNodeWithTag(Tab.QUESTS.screenTag).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.OVERVIEW_STUB).assertIsDisplayed()
  }

  // Otherwise no tab is selected while a screen opened from a tab is shown
  @Test
  fun aScreenOpenedFromATabKeepsThatTabSelected() {
    launchWithNavController()
    openVenuePage()

    composeTestRule.onNodeWithTag(C.Tag.VENUE_PAGE_STUB).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tab.QUESTS.tabTag).assertIsSelected()
  }

  // The tab would otherwise be stuck on the opened screen, with no way back through the bar
  @Test
  fun reselectingTheTabInUseReturnsToItsOwnScreen() {
    launchWithNavController()
    openVenuePage()
    select(Tab.QUESTS)

    assertShowing(Tab.QUESTS)
    composeTestRule.onNodeWithTag(C.Tag.VENUE_PAGE_STUB).assertDoesNotExist()
  }

  // Leaving a tab keeps what was opened from it; coming back shows it, and the tab, again
  @Test
  fun switchingTabsKeepsTheScreenOpenedFromATab() {
    launchWithNavController()
    openVenuePage()
    select(Tab.MAP)
    assertShowing(Tab.MAP)

    select(Tab.QUESTS)
    composeTestRule.onNodeWithTag(C.Tag.VENUE_PAGE_STUB).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tab.QUESTS.tabTag).assertIsSelected()
  }

  // A venue's card on the map opens its page: the Map tab stays selected, and Back returns to the
  // map
  @Test
  fun aVenuePageOpenedFromTheMapBelongsToTheMapTab() {
    launchWithNavController()
    select(Tab.MAP)
    openVenuePage()

    composeTestRule.onNodeWithTag(C.Tag.VENUE_PAGE_STUB).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tab.MAP.tabTag).assertIsSelected()

    pressBack()
    assertShowing(Tab.MAP)
  }

  @Test
  fun backFromAnyTabReturnsToQuests() {
    launch()
    select(Tab.MAP)
    select(Tab.PROFILE)
    pressBack()
    assertShowing(Tab.QUESTS)
  }

  @Test
  fun reselectingATabDoesNotStackIt() {
    launch()
    select(Tab.MAP)
    select(Tab.MAP)
    pressBack()
    assertShowing(Tab.QUESTS)
  }

  // Map, not Quests: Quests stays at the bottom of the back stack, so its state would survive even
  // if switching tabs did not save it.
  @Test
  fun eachTabKeepsItsOwnState() {
    launch { AroundApp(screen = { CounterScreen(it) }) }
    select(Tab.MAP)
    repeat(2) { composeTestRule.onNodeWithTag(Tab.MAP.screenTag).performClick() }

    select(Tab.PROFILE)
    composeTestRule.onNodeWithTag(Tab.PROFILE.screenTag).assertTextEquals("0")

    select(Tab.MAP)
    composeTestRule.onNodeWithTag(Tab.MAP.screenTag).assertTextEquals("2")
  }
}

/** Counts its taps; the count outlives leaving the tab only if navigation saved its state. */
@Composable
private fun CounterScreen(tab: Tab) {
  var count by rememberSaveable { mutableIntStateOf(0) }
  Text("$count", Modifier.testTag(tab.screenTag).clickable { count++ })
}
