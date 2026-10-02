// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AroundAppTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before fun setUp() = composeTestRule.setContent { AroundApp() }

  private fun select(tab: Tab) {
    composeTestRule.onNodeWithTag(tab.tabTag).performClick()
  }

  private fun pressBack() {
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

  @Test fun startsOnQuests() = assertShowing(Tab.QUESTS)

  @Test
  fun eachTabShowsItsScreen() {
    Tab.entries.reversed().forEach {
      select(it)
      assertShowing(it)
    }
  }

  @Test
  fun backFromAnyTabReturnsToQuests() {
    select(Tab.MAP)
    select(Tab.PROFILE)
    pressBack()
    assertShowing(Tab.QUESTS)
  }

  @Test
  fun reselectingATabDoesNotStackIt() {
    select(Tab.MAP)
    select(Tab.MAP)
    pressBack()
    assertShowing(Tab.QUESTS)
  }
}
