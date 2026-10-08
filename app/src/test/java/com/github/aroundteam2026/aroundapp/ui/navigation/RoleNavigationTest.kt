// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
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
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.user.FakeUserRepository
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.User
import com.github.aroundteam2026.aroundapp.model.user.UserRepository
import com.github.aroundteam2026.aroundapp.resources.C
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoleNavigationTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val users = FakeUserRepository()
  private lateinit var controller: NavHostController

  private fun profile(uid: String, role: Role?) = User(uid, "ada@around.test", "Ada", role, 1L)

  private fun launch(auth: FakeAuthRepository, userRepository: UserRepository = users) {
    compose.setContent {
      controller = rememberNavController()
      RoleNavigation(
          auth,
          userRepository,
          roleSelectionScreen = { Text(it, Modifier.testTag("role-selection")) },
          venueScreen = { Text(it, Modifier.testTag("venue-flow")) },
          explorerScreen = { Text("Explorer", Modifier.testTag("explorer-flow")) },
          navController = controller,
      )
    }
  }

  private fun assertOnly(tag: String) {
    compose.onNodeWithTag(tag).assertIsDisplayed()
    listOf("role-selection", "venue-flow", "explorer-flow", C.Tag.AUTH_SCREEN, C.Tag.SESSION_ERROR)
        .filter { it != tag }
        .forEach { compose.onNodeWithTag(it).assertDoesNotExist() }
    compose.runOnIdle { assertNull(controller.previousBackStackEntry) }
  }

  @Test
  fun restoredExplorerTabSurvivesANewViewModelWaitingForItsProfile() {
    val auth = FakeAuthRepository("explorer")
    var gate = CompletableDeferred<Unit>().apply { complete(Unit) }
    val delayed =
        object : UserRepository by users {
          override fun observeUser(uid: String) = flow {
            gate.await()
            emit(profile(uid, Role.EXPLORER))
          }
        }
    var currentViewModel: RoleRoutingViewModel? = null
    val restoration = StateRestorationTester(compose)
    restoration.setContent {
      controller = rememberNavController()
      val vm = remember { RoleRoutingViewModel(auth, delayed) }
      currentViewModel = vm
      DisposableEffect(vm) { onDispose { vm.viewModelScope.cancel() } }
      RoleNavigation(
          auth,
          delayed,
          roleSelectionScreen = { Text(it, Modifier.testTag("role-selection")) },
          venueScreen = { Text(it, Modifier.testTag("venue-flow")) },
          explorerScreen = {
            AroundApp(screen = { Text(it.route, Modifier.testTag(it.screenTag)) })
          },
          navController = controller,
          routingViewModel = vm,
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
      assertEquals(RoleSessionState.Checking, currentViewModel?.uiState?.value)
      assertEquals("role-session/explorer/home", controller.currentDestination?.route)
      gate.complete(Unit)
    }
    compose.onNodeWithTag(C.Tag.PROFILE_SCREEN).assertIsDisplayed()
    compose.runOnIdle { assertNull(controller.previousBackStackEntry) }
  }

  @Test
  fun returningExplorerOpensExplorerFlow() {
    runBlocking { users.createUser(profile("explorer", Role.EXPLORER)).getOrThrow() }
    launch(FakeAuthRepository("explorer"))
    assertOnly("explorer-flow")
  }

  @Test
  fun returningVenueOpensVenueFlowWithItsUid() {
    runBlocking { users.createUser(profile("venue", Role.VENUE)).getOrThrow() }
    launch(FakeAuthRepository("venue"))
    assertOnly("venue-flow")
    compose.onNodeWithTag("venue-flow").assertTextEquals("venue")
  }

  @Test
  fun missingProfileResumesRoleSelectionAndRoleSaveEntersVenue() {
    launch(FakeAuthRepository("new-user"))
    assertOnly("role-selection")
    compose.onNodeWithTag("role-selection").assertTextEquals("new-user")
    compose.runOnIdle {
      runBlocking {
        users.createUser(profile("new-user", null)).getOrThrow()
        users.setRole("new-user", Role.VENUE).getOrThrow()
      }
    }
    assertOnly("venue-flow")
  }

  @Test
  fun unsetRoleResumesSelectionAndRoleSaveEntersExplorer() {
    runBlocking { users.createUser(profile("user", null)).getOrThrow() }
    launch(FakeAuthRepository("user"))
    assertOnly("role-selection")
    compose.runOnIdle { runBlocking { users.setRole("user", Role.EXPLORER).getOrThrow() } }
    assertOnly("explorer-flow")
  }

  @Test
  fun newAccountEntersRoleSelectionAndSignOutClearsIt() {
    val auth = FakeAuthRepository()
    launch(auth)
    compose.onNodeWithTag(C.Tag.AUTH_SWITCH_MODE).performScrollTo().performClick()
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).performTextInput("ada@around.test")
    compose.onNodeWithTag(C.Tag.AUTH_PASSWORD).performTextInput("123456")
    compose.onNodeWithTag(C.Tag.AUTH_CONFIRMATION).performTextInput("123456")
    compose.onNodeWithTag(C.Tag.AUTH_SUBMIT).performScrollTo().performClick()
    assertOnly("role-selection")
    compose.runOnIdle { auth.signOut() }
    assertOnly(C.Tag.AUTH_SCREEN)
  }

  @Test
  fun signOutClearsVenueFlow() {
    val auth = FakeAuthRepository("venue")
    runBlocking { users.createUser(profile("venue", Role.VENUE)).getOrThrow() }
    launch(auth)
    assertOnly("venue-flow")
    compose.runOnIdle { auth.signOut() }
    assertOnly(C.Tag.AUTH_SCREEN)
  }

  @Test
  fun profileLoadingShowsNeitherProtectedFlowNorRoleSelection() {
    val profiles = MutableSharedFlow<User?>()
    val delayed =
        object : UserRepository by users {
          override fun observeUser(uid: String) = profiles
        }
    val auth = FakeAuthRepository("user")
    launch(auth, delayed)
    compose.onNodeWithTag(C.Tag.SESSION_LOADING).assertIsDisplayed()
    listOf("role-selection", "venue-flow", "explorer-flow", C.Tag.AUTH_SCREEN).forEach {
      compose.onNodeWithTag(it).assertDoesNotExist()
    }
    compose.runOnIdle { auth.signOut() }
    assertOnly(C.Tag.AUTH_SCREEN)
  }

  @Test
  fun profileErrorSignOutClearsTheSessionAndOpensAuth() {
    val auth = FakeAuthRepository("venue")
    val failing =
        object : UserRepository by users {
          override fun observeUser(uid: String) = flow<User?> { error("permission denied") }
        }
    launch(auth, failing)
    assertOnly(C.Tag.SESSION_ERROR)
    compose.onNodeWithTag(C.Tag.SESSION_SIGN_OUT).performClick()
    compose.runOnIdle { assertNull(auth.currentUserId.value) }
    assertOnly(C.Tag.AUTH_SCREEN)
  }

  @Test
  fun failedProfileReadOffersRetryRatherThanRoleSelection() {
    var fails = true
    val failing =
        object : UserRepository by users {
          override fun observeUser(uid: String) = flow {
            if (fails) error("network unavailable")
            emit(profile(uid, Role.VENUE))
          }
        }
    launch(FakeAuthRepository("venue"), failing)
    assertOnly(C.Tag.SESSION_ERROR)
    compose.runOnIdle { fails = false }
    compose.onNodeWithTag(C.Tag.SESSION_RETRY).performClick()
    assertOnly("venue-flow")
  }
}
