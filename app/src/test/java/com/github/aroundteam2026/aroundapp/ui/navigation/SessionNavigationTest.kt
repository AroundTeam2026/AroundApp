// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
            mainScreen = {
              AroundApp(
                  explorerUid = "explorer",
                  onSignOut = {},
                  screen = {
                    var count by rememberSaveable { mutableIntStateOf(0) }
                    Text("$count", Modifier.testTag(it.screenTag).clickable { count++ })
                  },
              )
            },
        )
      }
    }
  }

  private fun assertBackLeavesFlow() {
    compose.waitForIdle()
    // A callback installed before the flow models the activity's fallback. A stale NavHost
    // would consume Back instead; reaching this callback proves the flow cannot pop to auth/tabs.
    val fallbackCalls = compose.runOnIdle { backFallbackCalls }
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.runOnIdle { assertEquals(fallbackCalls + 1, backFallbackCalls) }
  }

  private var backFallbackCalls = 0

  private fun installBackFallback() {
    compose.runOnUiThread {
      compose.activity.onBackPressedDispatcher.addCallback(
          compose.activity,
          object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
              backFallbackCalls++
            }
          },
      )
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
          mainScreen = {
            AroundApp(
                explorerUid = "explorer",
                onSignOut = {},
                screen = { Text(it.route, Modifier.testTag(it.screenTag)) },
            )
          },
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
  fun profileSignOutReturnsToAuthAndAllowsSigningInAgain() {
    val repo = FakeAuthRepository()
    repo.addAccount("ada@around.test", "123456")
    installBackFallback()
    compose.setContent { AroundAppTheme { SessionNavigation(repo) } }
    submit()
    compose.onNodeWithTag(C.Tag.PROFILE_TAB).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_SIGN_OUT).assertIsDisplayed().performClick()
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.NAV_BAR).assertDoesNotExist()
    compose.runOnIdle { assertEquals(null, repo.currentUserId.value) }
    assertBackLeavesFlow()
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.PROFILE_SCREEN).assertDoesNotExist()
    submit()
    compose.onNodeWithTag(C.Tag.QUESTS_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.PROFILE_TAB).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_SIGN_OUT).assertIsDisplayed()
  }

  @Test
  fun successfulSignInLeavesNoAuthBackStack() {
    val repo = FakeAuthRepository()
    repo.addAccount("ada@around.test", "123456")
    installBackFallback()
    launch(repo)
    submit()
    assertBackLeavesFlow()
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
  }

  @Test
  fun accountCreationLeavesNoAuthBackStack() {
    installBackFallback()
    launch(FakeAuthRepository())
    compose.onNodeWithTag(C.Tag.AUTH_SWITCH_MODE).performScrollTo().performClick()
    compose.onNodeWithTag(C.Tag.AUTH_CONFIRMATION).performTextInput("123456")
    submit()
    assertBackLeavesFlow()
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
  }

  @Test
  fun authFieldsSurviveConfigurationRecreation() {
    val restoration = StateRestorationTester(compose)
    val repo = FakeAuthRepository()
    restoration.setContent { SessionNavigation(repo) }
    compose.onNodeWithTag(C.Tag.AUTH_SWITCH_MODE).performScrollTo().performClick()
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).performTextInput("ada@around.test")
    compose.onNodeWithTag(C.Tag.AUTH_PASSWORD).performTextInput("123456")
    compose.onNodeWithTag(C.Tag.AUTH_CONFIRMATION).performTextInput("123456")
    restoration.emulateSavedInstanceStateRestore()
    compose
        .onNodeWithTag(C.Tag.AUTH_EMAIL)
        .assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.InputText,
                AnnotatedString("ada@around.test"),
            )
        )
    compose
        .onNodeWithTag(C.Tag.AUTH_PASSWORD)
        .assert(
            SemanticsMatcher.expectValue(SemanticsProperties.InputText, AnnotatedString("123456"))
        )
    compose
        .onNodeWithTag(C.Tag.AUTH_CONFIRMATION)
        .assert(
            SemanticsMatcher.expectValue(SemanticsProperties.InputText, AnnotatedString("123456"))
        )
  }

  @Test
  fun pendingAuthRequestSurvivesConfigurationRecreation() {
    val auth = FakeAuthRepository()
    auth.addAccount("ada@around.test", "123456")
    val gate = CompletableDeferred<Unit>()
    var calls = 0
    var cancelled = false
    val delayed =
        object : AuthRepository by auth {
          override suspend fun signInWithEmail(email: String, password: String): Result<String> {
            calls++
            try {
              gate.await()
              return auth.signInWithEmail(email, password)
            } catch (error: kotlinx.coroutines.CancellationException) {
              cancelled = true
              throw error
            }
          }
        }
    val restoration = StateRestorationTester(compose)
    restoration.setContent {
      SessionNavigation(
          delayed,
          mainScreen = { Text("home", Modifier.testTag(C.Tag.QUESTS_SCREEN)) },
      )
    }
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).performTextInput("ada@around.test")
    compose.onNodeWithTag(C.Tag.AUTH_PASSWORD).performTextInput("123456")
    compose.onNodeWithTag(C.Tag.AUTH_SUBMIT).performScrollTo().performClick()
    compose.onNodeWithTag(C.Tag.AUTH_LOADING).assertIsDisplayed()
    restoration.emulateSavedInstanceStateRestore()
    compose.onNodeWithTag(C.Tag.AUTH_LOADING).assertIsDisplayed()
    compose.runOnIdle {
      assertEquals(1, calls)
      assertEquals(false, cancelled)
      gate.complete(Unit)
    }
    compose.onNodeWithTag(C.Tag.QUESTS_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
    compose.runOnIdle { assertEquals(false, cancelled) }
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

  @Test
  fun backAfterSignOutCannotReopenSelectedOrSavedTabs() {
    val repo = FakeAuthRepository("existing")
    installBackFallback()
    launch(repo)
    compose.onNodeWithTag(C.Tag.MAP_TAB).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_TAB).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_SCREEN).assertIsDisplayed()
    compose.runOnIdle { repo.signOut() }
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    assertBackLeavesFlow()
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.NAV_BAR).assertDoesNotExist()
  }

  @Test
  fun signingOutDiscardsSavedTabsEvenWhenTheSameAccountReturns() {
    val repo = FakeAuthRepository()
    repo.addAccount("ada@around.test", "123456")
    runBlocking { repo.signInWithEmail("ada@around.test", "123456") }
    launch(repo)
    compose.onNodeWithTag(C.Tag.MAP_TAB).performClick()
    compose.onNodeWithTag(C.Tag.MAP_SCREEN).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_TAB).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_SCREEN).assertIsDisplayed()
    compose.runOnIdle { repo.signOut() }
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    submit()
    compose.onNodeWithTag(C.Tag.PROFILE_SCREEN).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.MAP_TAB).performClick()
    compose.onNodeWithTag(C.Tag.MAP_SCREEN).assertTextEquals("0")
  }

  @Test
  fun switchingAccountsDiscardsThePreviousAccountsSavedFlow() {
    val repo = FakeAuthRepository()
    runBlocking { repo.signInWithGoogle("first") }
    launch(repo)
    compose.onNodeWithTag(C.Tag.MAP_TAB).performClick()
    compose.onNodeWithTag(C.Tag.MAP_SCREEN).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_TAB).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_SCREEN).assertIsDisplayed()
    compose.runOnIdle { runBlocking { repo.signInWithGoogle("second") } }
    compose.onNodeWithTag(C.Tag.QUESTS_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.PROFILE_TAB).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_SCREEN).assertIsDisplayed()
    compose.runOnIdle { runBlocking { repo.signInWithGoogle("first") } }
    compose.onNodeWithTag(C.Tag.QUESTS_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.PROFILE_SCREEN).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.MAP_TAB).performClick()
    compose.onNodeWithTag(C.Tag.MAP_SCREEN).assertTextEquals("0")
  }

  @Composable
  private fun TabsWithProbe(probes: MutableList<TabViewModel>) {
    AroundApp(
        explorerUid = "explorer",
        onSignOut = {},
        screen = { tab ->
          if (tab == Tab.MAP) {
            val probe: TabViewModel =
                viewModel(
                    factory = viewModelFactory { initializer { TabViewModel().also(probes::add) } }
                )
            Text("Map", Modifier.testTag(tab.screenTag))
          } else Text(tab.route, Modifier.testTag(tab.screenTag))
        },
    )
  }

  @Test
  fun signOutClearsTabViewModelsAndReturnsAnEmptyAuthForm() {
    val repo = FakeAuthRepository()
    repo.addAccount("ada@around.test", "123456")
    val probes = mutableListOf<TabViewModel>()
    compose.setContent { SessionNavigation(repo, mainScreen = { TabsWithProbe(probes) }) }
    submit()
    compose.onNodeWithTag(C.Tag.MAP_TAB).performClick()
    compose.onNodeWithTag(C.Tag.MAP_SCREEN).assertIsDisplayed()
    val original = probes.single()
    assertFreshAuthFormAfterSignOut(repo)
    compose.runOnIdle { assertTrue(original.cleared) }
  }

  @Test
  fun accountChangesClearOldTabViewModels() {
    val repo = FakeAuthRepository()
    runBlocking { repo.signInWithGoogle("first").getOrThrow() }
    val probes = mutableListOf<TabViewModel>()
    compose.setContent { SessionNavigation(repo, mainScreen = { TabsWithProbe(probes) }) }
    compose.onNodeWithTag(C.Tag.MAP_TAB).performClick()
    compose.onNodeWithTag(C.Tag.MAP_SCREEN).assertIsDisplayed()
    val original = probes.single()
    compose.runOnIdle { runBlocking { repo.signInWithGoogle("second").getOrThrow() } }
    compose.onNodeWithTag(C.Tag.QUESTS_SCREEN).assertIsDisplayed()
    compose.runOnIdle { assertTrue(original.cleared) }
    compose.onNodeWithTag(C.Tag.MAP_TAB).performClick()
    compose.onNodeWithTag(C.Tag.MAP_SCREEN).assertIsDisplayed()
    compose.runOnIdle {
      assertEquals(2, probes.size)
      assertNotSame(original, probes.last())
      assertFalse(probes.last().cleared)
    }
  }

  @Test
  fun configurationRecreationRetainsTabViewModels() {
    val repo = FakeAuthRepository("existing")
    val probes = mutableListOf<TabViewModel>()
    val restoration = StateRestorationTester(compose)
    restoration.setContent { SessionNavigation(repo, mainScreen = { TabsWithProbe(probes) }) }
    compose.onNodeWithTag(C.Tag.MAP_TAB).performClick()
    compose.onNodeWithTag(C.Tag.MAP_SCREEN).assertIsDisplayed()
    val original = probes.single()
    restoration.emulateSavedInstanceStateRestore()
    compose.onNodeWithTag(C.Tag.MAP_SCREEN).assertIsDisplayed()
    compose.runOnIdle {
      assertSame(original, probes.single())
      assertFalse(original.cleared)
    }
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
          mainScreen = {
            AroundApp(
                explorerUid = "explorer",
                onSignOut = {},
                screen = { Text(it.route, Modifier.testTag(it.screenTag)) },
            )
          },
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
    compose.runOnIdle { assertEquals("existing", auth.currentUserId.value) }
  }
}

private class TabViewModel : ViewModel() {
  var cleared = false
    private set

  override fun onCleared() {
    cleared = true
  }
}
