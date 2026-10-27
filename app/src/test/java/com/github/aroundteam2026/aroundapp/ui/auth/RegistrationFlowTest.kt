// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.auth

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.aroundteam2026.aroundapp.model.auth.AuthError
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.user.FakePendingRoleStore
import com.github.aroundteam2026.aroundapp.model.user.FakeUserRepository
import com.github.aroundteam2026.aroundapp.model.user.PendingRole
import com.github.aroundteam2026.aroundapp.model.user.PendingRoleStore
import com.github.aroundteam2026.aroundapp.model.user.ProfileSetup
import com.github.aroundteam2026.aroundapp.model.user.ProfileStatus
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.User
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.screen.RegistrationScreen
import com.github.aroundteam2026.aroundapp.ui.navigation.AppDestination
import com.github.aroundteam2026.aroundapp.ui.navigation.AppRoot
import com.github.aroundteam2026.aroundapp.ui.navigation.AroundApp
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RegistrationFlowTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val auth = FakeAuthRepository()
  private val users = FakeUserRepository()
  private val pending = FakePendingRoleStore()
  private var completionNotifications = 0
  private var backFallbacks = 0

  // A fixture for G2.3's contract, not a second production router: only it calls finish() and
  // selects slots. Tests drive the real form, ProfileSetup and Explorer tabs through that boundary.
  private fun launch(store: PendingRoleStore = pending) {
    val setup = ProfileSetup(auth, users, store)
    compose.activity.onBackPressedDispatcher.addCallback(
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            backFallbacks++
          }
        }
    )
    compose.setContent {
      val uid by auth.currentUserId.collectAsState()
      var retry by remember { mutableIntStateOf(0) }
      var destination by remember { mutableStateOf(AppDestination.LOADING) }
      LaunchedEffect(uid, retry) {
        destination =
            if (uid == null) AppDestination.AUTH
            else {
              when (val profile = setup.finish().getOrThrow()) {
                ProfileStatus.NeedsAccountSetup -> AppDestination.ROLE_SELECTION
                is ProfileStatus.Ready ->
                    if (profile.role == Role.EXPLORER) AppDestination.EXPLORER_HOME
                    else AppDestination.VENUE_ENTRY
              }
            }
      }
      AroundAppTheme {
        AppRoot(
            destination = destination,
            authFlow = {
              RegistrationFlow(auth, setup, onAccountSetupComplete = { error("Not account setup") })
            },
            roleSelection = {
              RegistrationFlow(
                  auth,
                  setup,
                  completeRegistration = true,
                  onAccountSetupComplete = {
                    completionNotifications++
                    retry++
                  },
              )
            },
            explorerHome = { AroundApp(explorerUid = "explorer", onSignOut = {}) },
            venueEntry = { Text("Venue entry") },
            profileError = { error("Unexpected profile failure") },
        )
      }
    }
  }

  private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()

  private fun back() {
    compose.waitForIdle()
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
  }

  private fun assertFinished(role: Role) {
    if (role == Role.EXPLORER) compose.onNodeWithTag(C.Tag.NAV_BAR).assertIsDisplayed()
    else compose.onNodeWithText("Venue entry").assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
    assertEquals(
        role,
        runBlocking { users.getUser(requireNotNull(auth.currentUserId.value)) }?.role,
    )
    assertNull(pending.pending)
    back()
    assertEquals(1, backFallbacks)
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
  }

  @Test
  fun explorerRegistrationCreatesProfileAndEntersTabsWithoutAuthBackStack() {
    launch()
    RegistrationScreen(compose).registerExplorer("explorer@around.test", "123456")
    assertFinished(Role.EXPLORER)
    // A new auth composition must not reuse the previous account's credentials.
    compose.runOnIdle { auth.signOut() }
    click(C.Tag.AUTH_WELCOME_SIGN_IN)
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).assertTextEquals("")
    compose.onNodeWithTag(C.Tag.AUTH_PASSWORD).assertTextEquals("")
    compose.onNodeWithTag(C.Tag.AUTH_CONFIRMATION).assertDoesNotExist()
  }

  @Test
  fun venueRegistrationCreatesVenueProfileAndHandsOffWithoutAuthBackStack() {
    launch()
    RegistrationScreen(compose).registerVenue("venue@around.test", "123456")
    assertFinished(Role.VENUE)
  }

  @Test
  fun venueLinkNavigatesWithoutValidatingOrCreatingAnAccountAndBackReturnsToExplorer() {
    launch()
    click(C.Tag.AUTH_WELCOME_CREATE_ACCOUNT)
    click(C.Tag.AUTH_EXPLORER_CARD)
    click(C.Tag.AUTH_VENUE_LINK)
    compose.onNodeWithTag(C.Tag.AUTH_TITLE).assertTextEquals("Create venue account")
    compose.onNodeWithTag(C.Tag.AUTH_ERROR).assertDoesNotExist()
    assertNull(auth.currentUserId.value)
    assertNull(pending.pending)
    back()
    compose.onNodeWithTag(C.Tag.AUTH_TITLE).assertTextEquals("Create account")
    click(C.Tag.AUTH_BACK)
    compose.onNodeWithTag(C.Tag.AUTH_ACCOUNT_TYPE_TITLE).assertIsDisplayed()
  }

  @Test
  fun businessCardOpensVenueCredentialsAndBackReturnsToAccountTypeChoice() {
    launch()
    click(C.Tag.AUTH_WELCOME_CREATE_ACCOUNT)
    click(C.Tag.AUTH_BUSINESS_CARD)
    compose.onNodeWithTag(C.Tag.AUTH_TITLE).assertTextEquals("Create venue account")
    click(C.Tag.AUTH_BACK)
    compose.onNodeWithTag(C.Tag.AUTH_ACCOUNT_TYPE_TITLE).assertIsDisplayed()
    assertNull(pending.pending)
  }

  @Test
  fun missingProfileCompletesAsExplorerWithoutAuthenticatingAgain() {
    completeExisting(Role.EXPLORER, existingProfile = false)
  }

  @Test
  fun profileWithoutRoleCompletesAsVenueWithoutAuthenticatingAgain() {
    completeExisting(Role.VENUE, existingProfile = true)
  }

  private fun completeExisting(role: Role, existingProfile: Boolean) {
    val uid = runBlocking { auth.signUpWithEmail("existing@around.test", "123456").getOrThrow() }
    if (existingProfile) runBlocking { users.createUser(User(uid, "Ada", null, 42L)).getOrThrow() }
    auth.nextError = AuthError.Network
    launch()
    compose.onNodeWithTag(C.Tag.AUTH_TITLE).assertTextContains("Complete registration")
    listOf(C.Tag.AUTH_EMAIL, C.Tag.AUTH_PASSWORD, C.Tag.AUTH_CONFIRMATION, C.Tag.AUTH_BACK)
        .forEach { compose.onNodeWithTag(it).assertDoesNotExist() }
    click(if (role == Role.VENUE) C.Tag.AUTH_VENUE_LINK else C.Tag.AUTH_SUBMIT)
    assertFinished(role)
    assertEquals(uid, auth.currentUserId.value)
    assertEquals(AuthError.Network, auth.nextError)
    assertEquals(1, completionNotifications)
    if (existingProfile) assertEquals("Ada", runBlocking { users.getUser(uid) }?.displayName)
  }

  @Test
  fun completeRegistrationWriteFailureStaysOnFormAndCanRetry() {
    runBlocking { auth.signUpWithEmail("existing@around.test", "123456").getOrThrow() }
    pending.failSave = true
    launch()
    click(C.Tag.AUTH_VENUE_LINK)
    compose.onNodeWithTag(C.Tag.AUTH_ERROR).assertIsDisplayed()
    assertEquals(0, completionNotifications)
    assertNull(runBlocking { users.getUser(requireNotNull(auth.currentUserId.value)) })
    pending.failSave = false
    click(C.Tag.AUTH_VENUE_LINK)
    assertFinished(Role.VENUE)
    assertEquals(1, completionNotifications)
  }

  @Test
  fun pendingChoiceDisablesVenueControlsAndBackUntilTheWriteFinishes() {
    pendingWrite(completeExplorer = false)
  }

  @Test
  fun pendingExplorerCompletionDisablesChoiceAndBackUntilTheWriteFinishes() {
    pendingWrite(completeExplorer = true)
  }

  private fun pendingWrite(completeExplorer: Boolean) {
    val gate = CompletableDeferred<Unit>()
    val store =
        object : PendingRoleStore by pending {
          override suspend fun save(pending: PendingRole) {
            gate.await()
            this@RegistrationFlowTest.pending.save(pending)
          }
        }
    if (completeExplorer) runBlocking { auth.signUpWithEmail("existing@around.test", "123456") }
    launch(store)
    if (completeExplorer) click(C.Tag.AUTH_SUBMIT)
    else RegistrationScreen(compose).registerVenue("venue@around.test", "123456")
    compose.onNodeWithTag(C.Tag.AUTH_LOADING).assertIsDisplayed()
    val tags =
        if (completeExplorer) listOf(C.Tag.AUTH_SUBMIT, C.Tag.AUTH_VENUE_LINK)
        else listOf(C.Tag.AUTH_SUBMIT, C.Tag.AUTH_BACK)
    tags.forEach { compose.onNodeWithTag(it).assertIsNotEnabled() }
    back()
    assertEquals(0, backFallbacks)
    if (!completeExplorer) assertNull(auth.currentUserId.value)
    compose
        .onNodeWithTag(C.Tag.AUTH_TITLE)
        .assertTextContains(
            if (completeExplorer) "Complete registration" else "Create venue account"
        )
    compose.runOnIdle { gate.complete(Unit) }
    assertFinished(if (completeExplorer) Role.EXPLORER else Role.VENUE)
  }
}
