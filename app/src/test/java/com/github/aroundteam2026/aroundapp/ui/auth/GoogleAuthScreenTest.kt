// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
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
import com.github.aroundteam2026.aroundapp.model.user.PendingRole
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

  /** Shows the screen and opens the form whose Welcome button is tagged [form]. */
  private fun show(
      restoration: StateRestorationTester? = null,
      form: String = C.Tag.AUTH_WELCOME_SIGN_IN,
      accountType: String = C.Tag.AUTH_EXPLORER_CARD,
      result: suspend () -> GoogleCredentialResult = { GoogleCredentialResult.Token("token") },
  ) {
    val content: @androidx.compose.runtime.Composable () -> Unit = {
      AroundAppTheme { AuthScreen(vm, googleSignInClient = GoogleSignInClient { result() }) }
    }
    if (restoration == null) compose.setContent(content) else restoration.setContent(content)
    click(form)
    if (form == C.Tag.AUTH_WELCOME_CREATE_ACCOUNT) click(accountType)
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
    }
  }

  @Test
  fun googleFromExplorerSignupReplacesAnAbandonedVenueChoice() {
    pending.pending = PendingRole(Role.VENUE, null)
    show(form = C.Tag.AUTH_WELCOME_CREATE_ACCOUNT)
    compose.onNodeWithTag(C.Tag.AUTH_VENUE_LINK).assertIsEnabled()
    click(C.Tag.AUTH_GOOGLE)
    compose.runOnIdle {
      assertNotNull(auth.currentUserId.value)
      assertEquals(Role.EXPLORER, pending.pending?.role)
      assertEquals(
          Role.EXPLORER,
          (runBlocking { ProfileSetup(auth, users, pending).finish().getOrThrow() }
                  as com.github.aroundteam2026.aroundapp.model.user.ProfileStatus.Ready)
              .role,
      )
    }
  }

  @Test
  fun businessSignupGoogleRecordsVenueBeforeAuthentication() {
    show(
        form = C.Tag.AUTH_WELCOME_CREATE_ACCOUNT,
        accountType = C.Tag.AUTH_BUSINESS_CARD,
    )
    click(C.Tag.AUTH_GOOGLE)
    compose.runOnIdle {
      assertNotNull(auth.currentUserId.value)
      assertEquals(Role.VENUE, pending.pending?.role)
    }
  }

  @Test
  fun venueLinkSignupGoogleRecordsVenueBeforeAuthentication() {
    show(form = C.Tag.AUTH_WELCOME_CREATE_ACCOUNT)
    click(C.Tag.AUTH_VENUE_LINK)
    click(C.Tag.AUTH_GOOGLE)
    compose.runOnIdle {
      assertNotNull(auth.currentUserId.value)
      assertEquals(Role.VENUE, pending.pending?.role)
    }
  }

  @Test
  fun explorerSignupWithExistingVenueGoogleAccountShowsErrorWithoutChangingRole() {
    val uid = runBlocking { auth.signUpWithGoogle("existing-venue").getOrThrow() }
    runBlocking {
      pending.pending = PendingRole(Role.VENUE, uid)
      ProfileSetup(auth, users, pending).finish().getOrThrow()
    }
    auth.signOut()
    show(form = C.Tag.AUTH_WELCOME_CREATE_ACCOUNT) {
      GoogleCredentialResult.Token("existing-venue")
    }

    click(C.Tag.AUTH_GOOGLE)

    compose.onNodeWithTag(C.Tag.AUTH_ERROR).assertTextContains("Sign in instead", substring = true)
    compose.runOnIdle {
      assertNull(auth.currentUserId.value)
      assertNull(pending.pending)
      assertEquals(Role.VENUE, runBlocking { users.getUser(uid) }?.role)
    }
  }

  @Test
  fun cancellingBusinessGoogleSignupLeavesNoRoleOrSession() {
    show(
        form = C.Tag.AUTH_WELCOME_CREATE_ACCOUNT,
        accountType = C.Tag.AUTH_BUSINESS_CARD,
        result = { GoogleCredentialResult.Cancelled },
    )
    click(C.Tag.AUTH_GOOGLE)
    compose.onNodeWithTag(C.Tag.AUTH_GOOGLE).assertIsEnabled()
    compose.onNodeWithTag(C.Tag.AUTH_SUBMIT).assertIsEnabled()
    compose.runOnIdle {
      assertNull(pending.pending)
      assertNull(auth.currentUserId.value)
    }
  }

  @Test
  fun cancellationPreservesFieldsAndReleasesAllRegistrationActions() {
    val gate = CompletableDeferred<GoogleCredentialResult>()
    show(form = C.Tag.AUTH_WELCOME_CREATE_ACCOUNT) { gate.await() }
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).performTextInput("ada@around.test")
    click(C.Tag.AUTH_GOOGLE)
    listOf(C.Tag.AUTH_GOOGLE, C.Tag.AUTH_SUBMIT, C.Tag.AUTH_BACK, C.Tag.AUTH_VENUE_LINK).forEach {
      compose.onNodeWithTag(it).assertIsNotEnabled()
    }
    compose.runOnIdle { gate.complete(GoogleCredentialResult.Cancelled) }
    compose.onNodeWithTag(C.Tag.AUTH_VENUE_LINK).assertIsEnabled()
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).assertTextEquals("ada@around.test")
    compose.onNodeWithTag(C.Tag.AUTH_ERROR).assertDoesNotExist()
    assertNull(auth.currentUserId.value)
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
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).assertTextEquals("ada@around.test")
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
