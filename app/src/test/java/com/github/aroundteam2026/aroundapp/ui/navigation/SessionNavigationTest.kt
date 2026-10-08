// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
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

  @OptIn(InternalCoroutinesApi::class)
  @Test
  fun restoredProfileTabSurvivesANewViewModelCheckingTheSession() {
    val auth = FakeAuthRepository("existing")
    var gate = CompletableDeferred<Unit>().apply { complete(Unit) }
    val delayed =
        object : AuthRepository by auth {
          override val currentUserId =
              object : StateFlow<String?> by auth.currentUserId {
                override suspend fun collect(collector: FlowCollector<String?>): Nothing {
                  gate.await()
                  return auth.currentUserId.collect(collector)
                }
              }
        }
    var currentViewModel: SessionViewModel? = null
    val restoration = StateRestorationTester(compose)
    restoration.setContent {
      controller = rememberNavController()
      val vm = remember { SessionViewModel(delayed) }
      currentViewModel = vm
      DisposableEffect(vm) { onDispose { vm.viewModelScope.cancel() } }
      SessionNavigation(
          delayed,
          controller,
          vm,
          mainScreen = { AroundApp(screen = { Text(it.route, Modifier.testTag(it.screenTag)) }) },
      )
    }
    compose.onNodeWithTag(C.Tag.PROFILE_TAB).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_SCREEN).assertIsDisplayed()
    val original = currentViewModel
    gate = CompletableDeferred()
    restoration.emulateSavedInstanceStateRestore()
    compose.onNodeWithTag(C.Tag.SESSION_LOADING).assertIsDisplayed()
    compose.runOnIdle {
      assertNotSame(original, currentViewModel)
      assertEquals(SessionState.Checking, currentViewModel?.uiState?.value)
      assertEquals("main/tabs", controller.currentDestination?.route)
      gate.complete(Unit)
    }
    compose.onNodeWithTag(C.Tag.PROFILE_SCREEN).assertIsDisplayed()
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

  @Test
  fun profileSignOutButtonReturnsToAuthAndClearsTheSession() {
    val repo = FakeAuthRepository("existing")
    compose.setContent {
      controller = rememberNavController()
      AroundAppTheme { SessionNavigation(repo, controller) }
    }
    compose.onNodeWithTag(C.Tag.PROFILE_TAB).performClick()
    compose.onNodeWithTag(C.Tag.SESSION_SIGN_OUT).performClick()
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.NAV_BAR).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.PROFILE_SCREEN).assertDoesNotExist()
    compose.runOnIdle { assertNull(repo.currentUserId.value) }
    assertNoPreviousFlow()
  }
}
