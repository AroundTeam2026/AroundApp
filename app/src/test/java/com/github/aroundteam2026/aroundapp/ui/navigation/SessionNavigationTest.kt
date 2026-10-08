// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionNavigationTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private lateinit var controller: NavHostController

  private fun launch(repo: FakeAuthRepository) {
    compose.setContent {
      controller = rememberNavController()
      AroundAppTheme {
        SessionNavigation(
            repo,
            controller,
            mainScreen = { AroundApp(screen = { Text(it.route, Modifier.testTag(it.screenTag)) }) },
        )
      }
    }
  }

  private fun assertNoPreviousFlow() {
    compose.runOnIdle { assertNull(controller.previousBackStackEntry) }
  }

  private fun submit() {
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).performTextInput("ada@around.test")
    compose.onNodeWithTag(C.Tag.AUTH_PASSWORD).performTextInput("123456")
    compose.onNodeWithTag(C.Tag.AUTH_SUBMIT).performScrollTo().performClick()
    compose.onNodeWithTag(C.Tag.QUESTS_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
    assertNoPreviousFlow()
  }

  @Test
  fun signedOutUsersSeeOnlyAuth() {
    launch(FakeAuthRepository())
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.NAV_BAR).assertDoesNotExist()
    assertNoPreviousFlow()
  }

  @Test
  fun existingSessionOpensTabsAndKeepsTabNavigation() {
    launch(FakeAuthRepository("existing"))
    compose.onNodeWithTag(C.Tag.QUESTS_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.PROFILE_TAB).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_SCREEN).assertIsDisplayed()
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.onNodeWithTag(C.Tag.QUESTS_SCREEN).assertIsDisplayed()
    assertNoPreviousFlow()
  }

  @Test
  fun successfulSignInClearsAuthBackStack() {
    val repo = FakeAuthRepository()
    repo.addAccount("ada@around.test", "123456")
    launch(repo)
    submit()
  }

  @Test
  fun accountCreationClearsAuthBackStack() {
    launch(FakeAuthRepository())
    compose.onNodeWithTag(C.Tag.AUTH_SWITCH_MODE).performScrollTo().performClick()
    compose.onNodeWithTag(C.Tag.AUTH_CONFIRMATION).performTextInput("123456")
    submit()
  }

  @Test
  fun signOutClearsProtectedTabsAndNextSessionStartsFresh() {
    val repo = FakeAuthRepository("existing")
    repo.addAccount("ada@around.test", "123456")
    launch(repo)
    compose.onNodeWithTag(C.Tag.PROFILE_TAB).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_SCREEN).assertIsDisplayed()
    compose.runOnIdle { repo.signOut() }
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.NAV_BAR).assertDoesNotExist()
    assertNoPreviousFlow()
    submit()
    compose.onNodeWithTag(C.Tag.PROFILE_SCREEN).assertDoesNotExist()
  }
}
