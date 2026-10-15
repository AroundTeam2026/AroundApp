// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.auth

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.user.FakePendingRoleStore
import com.github.aroundteam2026.aroundapp.model.user.FakeUserRepository
import com.github.aroundteam2026.aroundapp.model.user.ProfileSetup
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoogleAuthScreenTest {
  @get:Rule val compose = createComposeRule()
  private val auth = FakeAuthRepository()
  private val users = FakeUserRepository()
  private val pending = FakePendingRoleStore()
  private val vm = AuthViewModel(auth, ProfileSetup(auth, users, pending))

  private fun show(
      restoration: StateRestorationTester? = null,
      result: suspend () -> GoogleCredentialResult = { GoogleCredentialResult.Token("token") },
  ) {
    val content: @androidx.compose.runtime.Composable () -> Unit = {
      AroundAppTheme { AuthScreen(vm, googleSignInClient = GoogleSignInClient { result() }) }
    }
    if (restoration == null) compose.setContent(content) else restoration.setContent(content)
  }

  private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()

  @Test
  fun accessibleGoogleSignInDoesNotRequireFieldsOrInferAProfile() {
    show()
    compose
        .onNodeWithTag(C.Tag.AUTH_GOOGLE)
        .assertTextContains("Continue with Google")
        .assertHasClickAction()
    click(C.Tag.AUTH_GOOGLE)
    compose.runOnIdle {
      val uid = requireNotNull(auth.currentUserId.value)
      assertNull(runBlocking { users.getUser(uid) })
      assertNull(pending.pending)
    }
  }

  @Test
  fun googleFromCreateAccountRecordsTheExplicitExplorerChoice() {
    show()
    click(C.Tag.AUTH_SWITCH_MODE)
    compose.onNodeWithTag(C.Tag.AUTH_VENUE_LINK).assertIsEnabled()
    click(C.Tag.AUTH_GOOGLE)
    compose.runOnIdle {
      assertNotNull(auth.currentUserId.value)
      assertEquals(Role.EXPLORER, pending.pending?.role)
    }
  }

  @Test
  fun cancellationPreservesFieldsAndReleasesAllRegistrationActions() {
    val gate = CompletableDeferred<GoogleCredentialResult>()
    show { gate.await() }
    click(C.Tag.AUTH_SWITCH_MODE)
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).performTextInput("ada@around.test")
    click(C.Tag.AUTH_GOOGLE)
    listOf(C.Tag.AUTH_GOOGLE, C.Tag.AUTH_SUBMIT, C.Tag.AUTH_SWITCH_MODE, C.Tag.AUTH_VENUE_LINK)
        .forEach { compose.onNodeWithTag(it).assertIsNotEnabled() }
    compose.runOnIdle { gate.complete(GoogleCredentialResult.Cancelled) }
    compose.onNodeWithTag(C.Tag.AUTH_VENUE_LINK).assertIsEnabled()
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).assertTextEquals("ada@around.test", "Email")
    compose.onNodeWithTag(C.Tag.AUTH_ERROR).assertDoesNotExist()
    assertNull(auth.currentUserId.value)
    assertNull(pending.pending)
  }

  @Test
  fun recreationCancelsTheChooserButKeepsCredentials() {
    val restoration = StateRestorationTester(compose)
    val gate = CompletableDeferred<GoogleCredentialResult>()
    show(restoration) { gate.await() }
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).performTextInput("ada@around.test")
    click(C.Tag.AUTH_GOOGLE)
    restoration.emulateSavedInstanceStateRestore()
    compose.onNodeWithTag(C.Tag.AUTH_GOOGLE).assertIsEnabled()
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).assertTextEquals("ada@around.test", "Email")
    assertNull(auth.currentUserId.value)
  }

  @Test
  fun providerErrorsAreReadableAndAllowAnotherAttempt() {
    var result: GoogleCredentialResult = GoogleCredentialResult.Misconfigured
    show { result }
    click(C.Tag.AUTH_GOOGLE)
    compose
        .onNodeWithTag(C.Tag.AUTH_ERROR)
        .assertTextEquals("Google sign-in is not configured for this build.")
    compose.runOnIdle { result = GoogleCredentialResult.Unavailable }
    click(C.Tag.AUTH_GOOGLE)
    compose
        .onNodeWithTag(C.Tag.AUTH_ERROR)
        .assertTextContains("Add a Google account", substring = true)
    assertNull(auth.currentUserId.value)
  }
}
