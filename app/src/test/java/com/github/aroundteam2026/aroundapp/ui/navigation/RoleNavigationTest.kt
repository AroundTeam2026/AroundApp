// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.user.FakePendingRoleStore
import com.github.aroundteam2026.aroundapp.model.user.FakeUserRepository
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.User
import com.github.aroundteam2026.aroundapp.model.user.UserRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.screen.RegistrationScreen
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoleNavigationTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val users = FakeUserRepository()
  private val pending = FakePendingRoleStore()
  private var fallbackCalls = 0

  private fun launch(
      auth: FakeAuthRepository,
      repository: UserRepository = users,
      restoration: StateRestorationTester? = null,
  ) {
    compose.activity.onBackPressedDispatcher.addCallback(
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            fallbackCalls++
          }
        }
    )
    val content: @androidx.compose.runtime.Composable () -> Unit = {
      AroundAppTheme {
        RoleNavigation(
            auth,
            repository,
            pending,
            explorerScreen = {
              var count by rememberSaveable { mutableIntStateOf(0) }
              Text("Explorer $count", Modifier.testTag("explorer").clickable { count++ })
            },
            venueScreen = { uid -> Text(uid, Modifier.testTag("venue")) },
        )
      }
    }
    if (restoration == null) compose.setContent(content) else restoration.setContent(content)
  }

  private fun profile(uid: String, role: Role?) = runBlocking {
    users.createUser(User(uid, "Ada", role, 1L)).getOrThrow()
  }

  @Test
  fun emptyHomeSlotsStillReturnToAuthOnSessionLoss() {
    profile("restored", Role.EXPLORER)
    val auth = FakeAuthRepository("restored")
    compose.setContent { AroundAppTheme { RoleNavigation(auth, users, pending) } }
    compose.onNodeWithTag(C.Tag.APP_LOADING).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.ROLE_PROFILE_ERROR).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.NAV_BAR).assertDoesNotExist()
    compose.runOnIdle { auth.signOut() }
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
  }

  @Test
  fun registrationCreatesExplorerAndBackCannotReturnToAuth() {
    val auth = FakeAuthRepository()
    launch(auth)
    RegistrationScreen(compose).registerExplorer("ada@around.test", "123456")
    compose.onNodeWithTag("explorer").assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
    compose.runOnIdle {
      assertEquals(Role.EXPLORER, runBlocking { users.getUser(auth.currentUserId.value!!) }?.role)
      assertEquals(null, pending.pending)
    }
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.runOnIdle { assertEquals(1, fallbackCalls) }
    compose.onNodeWithTag("explorer").assertIsDisplayed()
  }

  @Test
  fun venueRegistrationRoutesToVenueEntry() {
    val auth = FakeAuthRepository()
    launch(auth)
    RegistrationScreen(compose).registerVenue("venue@around.test", "123456")
    compose.onNodeWithTag("venue").assertIsDisplayed()
    compose.onNodeWithTag("explorer").assertDoesNotExist()
    compose.runOnIdle {
      assertEquals(Role.VENUE, runBlocking { users.getUser(auth.currentUserId.value!!) }?.role)
    }
  }

  @Test
  fun missingProfileCompletesRegistrationWithoutCredentials() {
    val auth = FakeAuthRepository("restored")
    launch(auth)
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.AUTH_PASSWORD).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.AUTH_SUBMIT).performScrollTo().performClick()
    compose.onNodeWithTag("explorer").assertIsDisplayed()
    compose.runOnIdle {
      assertEquals(Role.EXPLORER, runBlocking { users.getUser("restored") }?.role)
    }
  }

  @Test
  fun unsetRoleCanBeCompletedAsVenue() {
    profile("restored", null)
    launch(FakeAuthRepository("restored"))
    compose.onNodeWithTag(C.Tag.AUTH_VENUE_LINK).performScrollTo().performClick()
    compose.onNodeWithTag("venue").assertTextEquals("restored")
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
  }

  @Test
  fun profileLoadingBlocksHomeUntilResolved() {
    profile("restored", Role.EXPLORER)
    val gate = CompletableDeferred<Unit>()
    val delayed =
        object : UserRepository by users {
          override suspend fun getUser(uid: String): User? {
            gate.await()
            return users.getUser(uid)
          }
        }
    launch(FakeAuthRepository("restored"), delayed)
    compose.onNodeWithTag(C.Tag.APP_LOADING).assertIsDisplayed()
    compose.onNodeWithTag("explorer").assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
    compose.runOnIdle { gate.complete(Unit) }
    compose.onNodeWithTag("explorer").assertIsDisplayed()
  }

  @Test
  fun errorRetryKeepsSessionAndRoutesStoredRole() {
    profile("restored", Role.VENUE)
    var fails = true
    val failing =
        object : UserRepository by users {
          override fun observeUser(uid: String) = flow {
            if (fails) error("offline")
            emit(users.getUser(uid))
          }
        }
    val auth = FakeAuthRepository("restored")
    launch(auth, failing)
    compose.onNodeWithTag(C.Tag.ROLE_PROFILE_ERROR).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
    compose.onNodeWithTag("venue").assertDoesNotExist()
    compose.runOnIdle { fails = false }
    compose.onNodeWithTag(C.Tag.ROLE_PROFILE_RETRY).performClick()
    compose.onNodeWithTag("venue").assertTextEquals("restored")
    compose.runOnIdle { assertEquals("restored", auth.currentUserId.value) }
  }

  @Test
  fun signOutDisposesProtectedFlowAndBackCannotReopenIt() {
    profile("restored", Role.EXPLORER)
    val auth = FakeAuthRepository("restored")
    launch(auth)
    compose.onNodeWithTag("explorer").assertIsDisplayed()
    compose.runOnIdle { auth.signOut() }
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag("explorer").assertDoesNotExist()
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.runOnIdle { assertEquals(1, fallbackCalls) }
    compose.onNodeWithTag("explorer").assertDoesNotExist()
  }

  @Test
  fun recreationKeepsExplorerStateButNewSessionStartsFresh() {
    val auth = FakeAuthRepository()
    val uid = runBlocking { auth.signInWithGoogle("first").getOrThrow() }
    profile(uid, Role.EXPLORER)
    val restoration = StateRestorationTester(compose)
    launch(auth, restoration = restoration)
    compose.onNodeWithTag("explorer").performClick()
    restoration.emulateSavedInstanceStateRestore()
    compose.onNodeWithTag("explorer").assertTextEquals("Explorer 1")
    compose.runOnIdle { auth.signOut() }
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    compose.runOnIdle { runBlocking { auth.signInWithGoogle("first").getOrThrow() } }
    compose.onNodeWithTag("explorer").assertTextEquals("Explorer 0")
  }

  @Test
  fun accountChangeRoutesTheNewUsersRole() {
    val auth = FakeAuthRepository()
    val first = runBlocking { auth.signInWithGoogle("first").getOrThrow() }
    profile(first, Role.EXPLORER)
    val second = runBlocking { auth.signInWithGoogle("second").getOrThrow() }
    profile(second, Role.VENUE)
    runBlocking { auth.signInWithGoogle("first").getOrThrow() }
    launch(auth)
    compose.onNodeWithTag("explorer").assertIsDisplayed()
    compose.runOnIdle { runBlocking { auth.signInWithGoogle("second").getOrThrow() } }
    compose.onNodeWithTag("venue").assertTextEquals(second)
    compose.onNodeWithTag("explorer").assertDoesNotExist()
  }

  @Test
  fun profileErrorCanSignOut() {
    val failing =
        object : UserRepository by users {
          override suspend fun getUser(uid: String): User? = error("offline")
        }
    val auth = FakeAuthRepository("restored")
    launch(auth, failing)
    compose.onNodeWithTag(C.Tag.ROLE_PROFILE_SIGN_OUT).performClick()
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.ROLE_PROFILE_ERROR).assertDoesNotExist()
    compose.runOnIdle { assertEquals(null, auth.currentUserId.value) }
  }
}
