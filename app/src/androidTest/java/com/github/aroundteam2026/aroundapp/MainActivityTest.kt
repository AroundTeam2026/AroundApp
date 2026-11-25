// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp

import android.app.NotificationManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.github.aroundteam2026.aroundapp.e2e.E2E_PASSWORD
import com.github.aroundteam2026.aroundapp.e2e.E2eRule
import com.github.aroundteam2026.aroundapp.e2e.createAccount
import com.github.aroundteam2026.aroundapp.e2e.signOut
import com.github.aroundteam2026.aroundapp.e2e.submitAuthForm
import com.github.aroundteam2026.aroundapp.model.notification.AroundNotification
import com.github.aroundteam2026.aroundapp.model.notification.NotificationChannelKind
import com.github.aroundteam2026.aroundapp.model.notification.NotificationPoster
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.screen.MainScreen
import com.github.aroundteam2026.aroundapp.testing.SETUP_TIMEOUT_MS
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

  @Test
  fun opensOnQuestsAndSwitchesTabs() = run {
    composeTestRule.createAccount()
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
    composeTestRule.createAccount(email)
    // Waits for the auth screen: sign-out first cancels the queued proofs, which Compose can't see
    composeTestRule.signOut()
    composeTestRule.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.NAV_BAR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.AUTH_WELCOME_SIGN_IN).performClick()
    composeTestRule.onNodeWithTag(C.Tag.AUTH_EMAIL).performTextInput(email)
    composeTestRule.onNodeWithTag(C.Tag.AUTH_PASSWORD).performTextInput(E2E_PASSWORD)
    composeTestRule.submitAuthForm()
    composeTestRule.waitUntil(SETUP_TIMEOUT_MS) {
      composeTestRule.onAllNodes(hasTestTag(C.Tag.NAV_BAR)).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag(C.Tag.QUESTS_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
  }

  @Test
  fun tappingANotificationOpensTheQuestsTab() = run {
    composeTestRule.createAccount()
    step("Leave the Quests tab for Profile") {
      ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) {
        profileTab { performClick() }
        profileScreen { assertIsDisplayed() }
      }
    }
    val launched = composeTestRule.activity
    val manager = launched.getSystemService(NotificationManager::class.java)
    try {
      step("Tap a notification about the explorer's reservation") {
        manager.cancelAll()
        NotificationPoster(launched)
            .post(
                AroundNotification(
                    "Café Lumière approved your reservation",
                    "Latte art · Head over and send your proof once you're there.",
                    "r1",
                    NotificationChannelKind.RESERVATIONS,
                )
            )
        // What the shade runs when the notification is tapped
        manager.activeNotifications.single().notification.contentIntent.send()
      }
      step("The app opens again, on the Quests tab") {
        composeTestRule.waitUntil(SETUP_TIMEOUT_MS) {
          composeTestRule
              .onAllNodes(hasTestTag(C.Tag.QUESTS_SCREEN))
              .fetchSemanticsNodes()
              .isNotEmpty()
        }
        ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) {
          questsTab { assertIsSelected() }
          questsScreen { assertIsDisplayed() }
        }
      }
    } finally {
      manager.cancelAll()
      // The tap started a new MainActivity the rule doesn't know about: close it, so the next test
      // doesn't find two apps on screen
      InstrumentationRegistry.getInstrumentation().runOnMainSync {
        ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.RESUMED)
            .filter { it !== launched }
            .forEach { it.finish() }
      }
    }
  }

  @Test
  fun activityRecreationKeepsTheSession() {
    composeTestRule.createAccount()
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
    composeTestRule.onNodeWithTag(C.Tag.AUTH_WELCOME_CREATE_ACCOUNT).performClick()
    composeTestRule.onNodeWithTag(C.Tag.AUTH_EXPLORER_CARD).performClick()
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
    composeTestRule.createAccount()
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
