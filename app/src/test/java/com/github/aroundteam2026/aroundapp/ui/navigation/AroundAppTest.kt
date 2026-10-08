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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.resources.C
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AroundAppTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private fun launch(app: @Composable () -> Unit = { AroundApp() }) =
      composeTestRule.setContent(app)

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
  fun startsOnExplore() {
    launch()
    assertShowing(Tab.EXPLORE)
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
  fun theExploreTabShowsTheMap() {
    launch()

    composeTestRule.onNodeWithTag(C.Tag.MAP).assertIsDisplayed()
  }

  @Test
  fun backFromAnyTabReturnsToExplore() {
    launch()
    select(Tab.QUESTS)
    select(Tab.FRIENDS)
    select(Tab.PROFILE)
    pressBack()
    assertShowing(Tab.EXPLORE)
  }

  @Test
  fun backFromExploreLeavesTheApp() {
    launch { AroundApp(screen = { CounterScreen(it) }) }
    pressBack()
    composeTestRule.waitForIdle()

    assertTrue(composeTestRule.activity.isFinishing)
  }

  @Test
  fun reselectingATabDoesNotStackIt() {
    launch()
    select(Tab.FRIENDS)
    select(Tab.FRIENDS)
    pressBack()
    assertShowing(Tab.EXPLORE)
  }

  @Test
  fun reselectingExploreKeepsShowingIt() {
    launch()
    select(Tab.EXPLORE)
    assertShowing(Tab.EXPLORE)
  }

  // Quests, not Explore: Explore stays at the bottom of the back stack, so its state would survive
  // even if switching tabs did not save it.
  @Test
  fun eachTabKeepsItsOwnState() {
    launch { AroundApp(screen = { CounterScreen(it) }) }
    select(Tab.QUESTS)
    repeat(2) { composeTestRule.onNodeWithTag(Tab.QUESTS.screenTag).performClick() }

    select(Tab.FRIENDS)
    composeTestRule.onNodeWithTag(Tab.FRIENDS.screenTag).assertTextEquals("0")

    select(Tab.QUESTS)
    composeTestRule.onNodeWithTag(Tab.QUESTS.screenTag).assertTextEquals("2")
  }

  @Test
  fun theBarIsTheFigmaBar() {
    launch { AroundApp(screen = { CounterScreen(it) }) }

    // Only the design's bar has the pills; Material's would not
    Tab.entries.forEach {
      composeTestRule
          .onNode(
              hasTestTag(C.Tag.NAV_TAB_PILL) and hasAnyAncestor(hasTestTag(it.tabTag)),
              useUnmergedTree = true,
          )
          .assertIsDisplayed()
    }
  }
}

/** Counts its taps; the count outlives leaving the tab only if navigation saved its state. */
@Composable
private fun CounterScreen(tab: Tab) {
  var count by rememberSaveable { mutableIntStateOf(0) }
  Text("$count", Modifier.testTag(tab.screenTag).clickable { count++ })
}
