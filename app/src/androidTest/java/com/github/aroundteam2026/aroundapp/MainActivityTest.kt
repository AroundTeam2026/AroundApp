// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.e2e.E2eRule
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.screen.MainScreen
import com.github.aroundteam2026.aroundapp.ui.map.awaitGoogleMap
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import io.github.kakaocup.compose.node.element.ComposeScreen
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launches the app on a device and walks through its tabs. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest : TestCase() {

  @get:Rule val e2e = E2eRule()
  private val composeTestRule = e2e.composeTestRule

  /** Enters the tabs through the real form, with an account unique to this test. */
  private fun createAccount(email: String = "navigation-${UUID.randomUUID()}@around.test") {
    composeTestRule.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.AUTH_SWITCH_MODE).performScrollTo().performClick()
    composeTestRule.onNodeWithTag(C.Tag.AUTH_EMAIL).performTextInput(email)
    composeTestRule.onNodeWithTag(C.Tag.AUTH_PASSWORD).performTextInput("123456")
    composeTestRule.onNodeWithTag(C.Tag.AUTH_CONFIRMATION).performTextInput("123456")
    composeTestRule.onNodeWithTag(C.Tag.AUTH_SUBMIT).performScrollTo().performClick()
    composeTestRule.waitUntil(10_000) {
      composeTestRule.onAllNodes(hasTestTag(C.Tag.NAV_BAR)).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag(C.Tag.NAV_BAR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
  }

  @Test
  fun opensOnQuestsAndSwitchesTabs() = run {
    createAccount()
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
  fun profileSignOutAllowsSigningInAgain() {
    val email = "navigation-${UUID.randomUUID()}@around.test"
    createAccount(email)
    composeTestRule.onNodeWithTag(C.Tag.PROFILE_TAB).performClick()
    composeTestRule.onNodeWithTag(C.Tag.PROFILE_SIGN_OUT).performClick()
    composeTestRule.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.NAV_BAR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.AUTH_EMAIL).performTextInput(email)
    composeTestRule.onNodeWithTag(C.Tag.AUTH_PASSWORD).performTextInput("123456")
    composeTestRule.onNodeWithTag(C.Tag.AUTH_SUBMIT).performScrollTo().performClick()
    composeTestRule.waitUntil(10_000) {
      composeTestRule.onAllNodes(hasTestTag(C.Tag.NAV_BAR)).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag(C.Tag.QUESTS_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
  }

  @Test
  fun activityRecreationKeepsTheSession() {
    createAccount()
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
  fun activityRecreationKeepsUnsubmittedAuthFields() {
    composeTestRule.onNodeWithTag(C.Tag.AUTH_SWITCH_MODE).performScrollTo().performClick()
    composeTestRule.onNodeWithTag(C.Tag.AUTH_EMAIL).performTextInput("typing@around.test")
    composeTestRule.onNodeWithTag(C.Tag.AUTH_PASSWORD).performTextInput("123456")
    composeTestRule.onNodeWithTag(C.Tag.AUTH_CONFIRMATION).performTextInput("123456")
    composeTestRule.activityRule.scenario.recreate()
    listOf(
            C.Tag.AUTH_EMAIL to "typing@around.test",
            C.Tag.AUTH_PASSWORD to "123456",
            C.Tag.AUTH_CONFIRMATION to "123456",
        )
        .forEach { (tag, value) ->
          composeTestRule
              .onNodeWithTag(tag)
              .assert(
                  SemanticsMatcher.expectValue(
                      SemanticsProperties.InputText,
                      AnnotatedString(value),
                  )
              )
        }
  }

  @Test
  fun missingSessionShowsAuthAfterRecreation() {
    composeTestRule.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.NAV_BAR).assertDoesNotExist()
    composeTestRule.activityRule.scenario.recreate()
    composeTestRule.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.NAV_BAR).assertDoesNotExist()
  }

  @Test
  fun theMapTabRunsAGoogleMap() = run {
    createAccount()
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
