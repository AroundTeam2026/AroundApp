// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepositoryProvider
import com.github.aroundteam2026.aroundapp.resources.C
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.e2e.E2eRule
import com.github.aroundteam2026.aroundapp.screen.MainScreen
import com.github.aroundteam2026.aroundapp.ui.map.awaitGoogleMap
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import io.github.kakaocup.compose.node.element.ComposeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/** Launches the app on a device and walks through its tabs. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest : TestCase() {

  @get:Rule val e2e = E2eRule()
  private val composeTestRule = e2e.composeTestRule

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

  @Test
  fun activityRecreationKeepsTheSession() {
    ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) {
      profileTab { performClick() }
      profileScreen { assertIsDisplayed() }
    }
    composeTestRule.activityRule.scenario.recreate()
    ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) {
      navBar { assertIsDisplayed() }
      profileTab { assertIsSelected() }
      profileScreen { assertIsDisplayed() }
    }
  }

  @Test
  fun missingSessionShowsAuthAfterRecreation() {
    composeTestRule.runOnIdle { AuthRepositoryProvider.repository.signOut() }
    composeTestRule.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.NAV_BAR).assertDoesNotExist()
    composeTestRule.activityRule.scenario.recreate()
    composeTestRule.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.NAV_BAR).assertDoesNotExist()
  }

  @Test
  fun theMapTabRunsAGoogleMap() = run {
    step("Open the Map tab") {
      ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) {
        mapTab { performClick() }
        map { assertIsDisplayed() }
      }
    }
    step("The Maps SDK hands the map over, so it is set up in this build") {
      // Fails if the Maps SDK never provides the map
      composeTestRule.awaitGoogleMap()
    }
  }
}
