// Co-authored-by: OpenAI Codex
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.auth

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.auth.AuthError
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthScreenTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val repository = FakeAuthRepository()

  private fun launch(repo: AuthRepository = repository) {
    val viewModel = AuthViewModel(repo)
    compose.setContent { AroundAppTheme { AuthScreen(viewModel) } }
  }

  private fun fill() {
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).performTextInput("ada@around.test")
    compose.onNodeWithTag(C.Tag.AUTH_PASSWORD).performTextInput("123456")
  }

  private fun click(tag: String) {
    compose.onNodeWithTag(tag).performScrollTo().performClick()
  }

  private fun openSignIn() = click(C.Tag.AUTH_WELCOME_SIGN_IN)

  private fun openCreateAccount() {
    click(C.Tag.AUTH_WELCOME_CREATE_ACCOUNT)
    click(C.Tag.AUTH_EXPLORER_CARD)
  }

  @Test
  fun signInUsesEnteredCredentials() {
    val uid = repository.addAccount("ada@around.test", "123456")
    launch()
    openSignIn()
    compose.onNodeWithTag(C.Tag.AUTH_CONFIRMATION).assertDoesNotExist()
    fill()
    click(C.Tag.AUTH_SUBMIT)
    compose.waitForIdle()
    assertEquals(uid, repository.currentUserId.value)
    compose.onNodeWithTag(C.Tag.AUTH_ERROR).assertDoesNotExist()
  }

  @Test
  fun accountCreationRejectsMismatchThenSucceedsAfterCorrection() {
    repository.nextError = AuthError.Network
    launch()
    openCreateAccount()
    // Without ProfileSetup a Venue choice could not be recorded, so the link is not offered.
    compose.onNodeWithTag(C.Tag.AUTH_VENUE_LINK).assertDoesNotExist()
    fill()
    click(C.Tag.AUTH_SUBMIT)
    compose.onNodeWithTag(C.Tag.AUTH_ERROR).assertTextEquals("Passwords do not match.")
    assertEquals(AuthError.Network, repository.nextError)
    assertNull(repository.currentUserId.value)
    compose.onNodeWithTag(C.Tag.AUTH_CONFIRMATION).performTextInput("123456")
    compose.onNodeWithTag(C.Tag.AUTH_ERROR).assertDoesNotExist()
    repository.nextError = null
    click(C.Tag.AUTH_SUBMIT)
    compose.waitForIdle()
    assertNotNull(repository.currentUserId.value)
  }

  @Test
  fun failedSignInShowsReadableErrorAndAllowsRetry() {
    val uid = repository.addAccount("ada@around.test", "123456")
    repository.nextError = AuthError.Network
    launch()
    openSignIn()
    fill()
    click(C.Tag.AUTH_SUBMIT)
    compose
        .onNodeWithTag(C.Tag.AUTH_ERROR)
        .assertTextEquals("Could not connect. Check your connection and try again.")
    compose.onNodeWithTag(C.Tag.AUTH_SUBMIT).assertIsEnabled()
    click(C.Tag.AUTH_SUBMIT)
    compose.waitForIdle()
    assertEquals(uid, repository.currentUserId.value)
    compose.onNodeWithTag(C.Tag.AUTH_ERROR).assertDoesNotExist()
  }

  @Test
  fun pendingRequestDisablesTheFormAndBack() {
    val gate = CompletableDeferred<Unit>()
    val gatedRepository =
        object : AuthRepository by repository {
          override suspend fun signInWithEmail(email: String, password: String): Result<String> {
            gate.await()
            return repository.signInWithEmail(email, password)
          }
        }
    repository.addAccount("ada@around.test", "123456")
    launch(gatedRepository)
    openSignIn()
    fill()
    click(C.Tag.AUTH_SUBMIT)
    compose.onNodeWithTag(C.Tag.AUTH_LOADING).assertIsDisplayed()
    for (tag in
        listOf(
            C.Tag.AUTH_EMAIL,
            C.Tag.AUTH_PASSWORD,
            C.Tag.AUTH_PASSWORD_VISIBILITY,
            C.Tag.AUTH_SUBMIT,
            C.Tag.AUTH_GOOGLE,
            C.Tag.AUTH_BACK,
        )) {
      compose.onNodeWithTag(tag).assertIsNotEnabled()
    }
    compose.runOnIdle { gate.complete(Unit) }
    compose.waitForIdle()
    compose.onNodeWithTag(C.Tag.AUTH_LOADING).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.AUTH_SUBMIT).assertIsEnabled()
  }

  @Test
  fun backToWelcomeClearsMismatchAndSignInHidesConfirmation() {
    launch()
    openCreateAccount()
    fill()
    compose.onNodeWithTag(C.Tag.AUTH_CONFIRMATION).performTextInput("different")
    click(C.Tag.AUTH_SUBMIT)
    compose.onNodeWithTag(C.Tag.AUTH_ERROR).assertIsDisplayed()

    click(C.Tag.AUTH_BACK)
    click(C.Tag.AUTH_BACK)
    openSignIn()

    compose.onNodeWithTag(C.Tag.AUTH_ERROR).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.AUTH_CONFIRMATION).assertDoesNotExist()
    click(C.Tag.AUTH_BACK)
    openCreateAccount()
    compose.onNodeWithTag(C.Tag.AUTH_CONFIRMATION).assertTextEquals("")
  }

  @Test
  fun shortPasswordShowsFormattedPluralMessage() {
    launch()
    openCreateAccount()
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).performTextInput("ada@around.test")
    compose.onNodeWithTag(C.Tag.AUTH_PASSWORD).performTextInput("12345")
    click(C.Tag.AUTH_SUBMIT)
    compose
        .onNodeWithTag(C.Tag.AUTH_ERROR)
        .assertTextEquals("Use a password with at least 6 characters.")
  }

  private fun assertRenderedPassword(tag: String, expected: String) {
    val layouts = mutableListOf<TextLayoutResult>()
    compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
      assertEquals(true, it(layouts))
    }
    assertEquals(expected, layouts.single().layoutInput.text.text)
  }

  @Test
  fun passwordCanBeShownAndHiddenWithoutChangingSignInCredentials() {
    val uid = repository.addAccount("ada@around.test", "123456")
    launch()
    openSignIn()
    fill()
    assertRenderedPassword(C.Tag.AUTH_PASSWORD, "••••••")
    compose
        .onNodeWithTag(C.Tag.AUTH_PASSWORD_VISIBILITY)
        .assertContentDescriptionEquals("Show password")
        .performClick()
    assertRenderedPassword(C.Tag.AUTH_PASSWORD, "123456")
    compose
        .onNodeWithTag(C.Tag.AUTH_PASSWORD_VISIBILITY)
        .assertContentDescriptionEquals("Hide password")
        .performClick()
    assertRenderedPassword(C.Tag.AUTH_PASSWORD, "••••••")
    click(C.Tag.AUTH_SUBMIT)
    compose.runOnIdle { assertEquals(uid, repository.currentUserId.value) }
  }

  @Test
  fun signUpPasswordAndConfirmationVisibilityAreIndependent() {
    launch()
    openCreateAccount()
    fill()
    compose.onNodeWithTag(C.Tag.AUTH_CONFIRMATION).performTextInput("123456")
    assertRenderedPassword(C.Tag.AUTH_CONFIRMATION, "••••••")
    compose.onNodeWithTag(C.Tag.AUTH_PASSWORD_VISIBILITY).performClick()
    assertRenderedPassword(C.Tag.AUTH_CONFIRMATION, "••••••")
    compose
        .onNodeWithTag(C.Tag.AUTH_CONFIRMATION_VISIBILITY)
        .assertContentDescriptionEquals("Show confirmation password")
        .performClick()
    assertRenderedPassword(C.Tag.AUTH_CONFIRMATION, "123456")
    compose
        .onNodeWithTag(C.Tag.AUTH_CONFIRMATION_VISIBILITY)
        .assertContentDescriptionEquals("Hide confirmation password")
        .performClick()
    assertRenderedPassword(C.Tag.AUTH_CONFIRMATION, "••••••")
    assertRenderedPassword(C.Tag.AUTH_PASSWORD, "123456")
    click(C.Tag.AUTH_SUBMIT)
    compose.runOnIdle { assertNotNull(repository.currentUserId.value) }
  }

  @Test
  fun recreationRetainsTheFormAndPasswordVisibility() {
    val restoration = StateRestorationTester(compose)
    val viewModel = AuthViewModel(repository)
    restoration.setContent { AroundAppTheme { AuthScreen(viewModel) } }
    openSignIn()
    fill()
    compose.onNodeWithTag(C.Tag.AUTH_PASSWORD_VISIBILITY).performClick()
    restoration.emulateSavedInstanceStateRestore()
    compose.onNodeWithTag(C.Tag.AUTH_TITLE).assertTextEquals("Sign in")
    assertRenderedPassword(C.Tag.AUTH_PASSWORD, "123456")
    compose
        .onNodeWithTag(C.Tag.AUTH_PASSWORD_VISIBILITY)
        .assertContentDescriptionEquals("Hide password")
  }

  @Test
  fun everyErrorMapsToADifferentMessageResource() {
    val resources = AuthFormError.entries.map { it.messageResource() }
    assertEquals(resources.size, resources.toSet().size)
  }
}
