// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.screen.MainScreen
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import io.github.kakaocup.compose.node.element.ComposeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launches the app on a device and walks through its tabs. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest : TestCase() {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun opensOnQuestsAndSwitchesTabs() = run {
    step("Start on the Quests tab") {
      ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) {
        navBar { assertIsDisplayed() }
        questsTab { assertIsSelected() }
        questsScreen { assertIsDisplayed() }
      }
    }
    step("Open the Map tab") {
      ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) {
        mapTab { performClick() }
        mapTab { assertIsSelected() }
        mapScreen { assertIsDisplayed() }
      }
    }
    step("Open the Profile tab") {
      ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) {
        profileTab { performClick() }
        profileTab { assertIsSelected() }
        profileScreen { assertIsDisplayed() }
      }
    }
  }
}
