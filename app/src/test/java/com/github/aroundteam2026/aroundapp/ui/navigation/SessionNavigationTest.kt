// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionNavigationTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun launch(repo: FakeAuthRepository) {
    compose.setContent {
      AroundAppTheme {
        SessionNavigation(
            repo,
            mainScreen = { AroundApp(screen = { Text(it.route, Modifier.testTag(it.screenTag)) }) },
        )
      }
    }
  }

  private fun submit() {
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).performTextInput("ada@around.test")
    compose.onNodeWithTag(C.Tag.AUTH_PASSWORD).performTextInput("123456")
    compose.onNodeWithTag(C.Tag.AUTH_SUBMIT).performScrollTo().performClick()
    compose.onNodeWithTag(C.Tag.QUESTS_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
  }

  @OptIn(InternalCoroutinesApi::class, ExperimentalForInheritanceCoroutinesApi::class)
  private fun launchCheckingSession(userId: String?): CompletableDeferred<Unit> {
    val auth = FakeAuthRepository(userId)
    val gate = CompletableDeferred<Unit>()
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
    compose.setContent {
      val vm = remember { SessionViewModel(delayed) }
      DisposableEffect(vm) { onDispose { vm.viewModelScope.cancel() } }
      SessionNavigation(
          delayed,
          vm,
          mainScreen = { AroundApp(screen = { Text(it.route, Modifier.testTag(it.screenTag)) }) },
      )
    }
    compose.onNodeWithTag(C.Tag.APP_LOADING).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.NAV_BAR).assertDoesNotExist()
    return gate
  }

  @Test
  fun checkingShowsOnlyLoadingBeforeRestoringSession() {
    val gate = launchCheckingSession("existing")
    compose.runOnIdle { gate.complete(Unit) }
    compose.onNodeWithTag(C.Tag.QUESTS_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.APP_LOADING).assertDoesNotExist()
  }

  @Test
  fun checkingShowsOnlyLoadingBeforeResolvingMissingSession() {
    val gate = launchCheckingSession(null)
    compose.runOnIdle { gate.complete(Unit) }
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.APP_LOADING).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.NAV_BAR).assertDoesNotExist()
  }

  @Test
  fun signedOutUsersSeeOnlyAuth() {
    launch(FakeAuthRepository())
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.NAV_BAR).assertDoesNotExist()
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
  }

  @Test
  fun successfulSignInOpensTabs() {
    val repo = FakeAuthRepository()
    repo.addAccount("ada@around.test", "123456")
    launch(repo)
    submit()
  }

  @Test
  fun accountCreationOpensTabs() {
    launch(FakeAuthRepository())
    compose.onNodeWithTag(C.Tag.AUTH_SWITCH_MODE).performScrollTo().performClick()
    compose.onNodeWithTag(C.Tag.AUTH_CONFIRMATION).performTextInput("123456")
    submit()
  }

  private fun assertFreshAuthFormAfterSignOut(repo: FakeAuthRepository) {
    compose.runOnIdle { repo.signOut() }
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    compose
        .onNodeWithTag(C.Tag.AUTH_EMAIL)
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    compose
        .onNodeWithTag(C.Tag.AUTH_PASSWORD)
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    compose.onNodeWithTag(C.Tag.AUTH_ERROR).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.AUTH_LOADING).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.AUTH_SWITCH_MODE).performScrollTo().performClick()
    compose
        .onNodeWithTag(C.Tag.AUTH_CONFIRMATION)
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
  }

  @Test
  fun signOutAfterSignInReturnsToAnEmptyAuthForm() {
    val repo = FakeAuthRepository()
    repo.addAccount("ada@around.test", "123456")
    launch(repo)
    submit()
    assertFreshAuthFormAfterSignOut(repo)
  }

  @Test
  fun sessionLossAfterSignUpReturnsToAnEmptyAuthForm() {
    val repo = FakeAuthRepository()
    launch(repo)
    compose.onNodeWithTag(C.Tag.AUTH_SWITCH_MODE).performScrollTo().performClick()
    compose.onNodeWithTag(C.Tag.AUTH_CONFIRMATION).performTextInput("123456")
    submit()
    assertFreshAuthFormAfterSignOut(repo)
  }

  @Test
  fun sessionChangesReplaceTheVisibleFlow() {
    val repo = FakeAuthRepository("existing")
    repo.addAccount("ada@around.test", "123456")
    launch(repo)
    compose.onNodeWithTag(C.Tag.PROFILE_TAB).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_SCREEN).assertIsDisplayed()
    compose.runOnIdle { repo.signOut() }
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.NAV_BAR).assertDoesNotExist()
    submit()
    compose.onNodeWithTag(C.Tag.PROFILE_SCREEN).assertDoesNotExist()
  }

  @OptIn(InternalCoroutinesApi::class, ExperimentalForInheritanceCoroutinesApi::class)
  @Test
  fun restoredTabSurvivesANewViewModelCheckingTheSession() {
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
      val vm = remember { SessionViewModel(delayed) }
      currentViewModel = vm
      DisposableEffect(vm) { onDispose { vm.viewModelScope.cancel() } }
      SessionNavigation(
          delayed,
          vm,
          mainScreen = { AroundApp(screen = { Text(it.route, Modifier.testTag(it.screenTag)) }) },
      )
    }
    compose.onNodeWithTag(C.Tag.PROFILE_TAB).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_SCREEN).assertIsDisplayed()
    val original = currentViewModel
    gate = CompletableDeferred()
    restoration.emulateSavedInstanceStateRestore()
    compose.onNodeWithTag(C.Tag.APP_LOADING).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.NAV_BAR).assertDoesNotExist()
    compose.runOnIdle {
      assertNotSame(original, currentViewModel)
      assertEquals(SessionState.Checking, currentViewModel?.uiState?.value)
      gate.complete(Unit)
    }
    compose.onNodeWithTag(C.Tag.PROFILE_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.APP_LOADING).assertDoesNotExist()
  }
}
