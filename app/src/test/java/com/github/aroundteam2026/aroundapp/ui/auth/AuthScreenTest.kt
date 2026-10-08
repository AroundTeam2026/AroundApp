// Co-authored-by: OpenAI Codex
package com.github.aroundteam2026.aroundapp.ui.auth

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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

  @Test
  fun signInUsesEnteredCredentials() {
    val uid = repository.addAccount("ada@around.test", "123456")
    launch()
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
    click(C.Tag.AUTH_SWITCH_MODE)
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
  fun pendingRequestDisablesTheFormAndModeSwitch() {
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
    fill()
    click(C.Tag.AUTH_SUBMIT)
    compose.onNodeWithTag(C.Tag.AUTH_LOADING).assertIsDisplayed()
    for (tag in
        listOf(C.Tag.AUTH_EMAIL, C.Tag.AUTH_PASSWORD, C.Tag.AUTH_SUBMIT, C.Tag.AUTH_SWITCH_MODE)) {
      compose.onNodeWithTag(tag).assertIsNotEnabled()
    }
    compose.runOnIdle { gate.complete(Unit) }
    compose.waitForIdle()
    compose.onNodeWithTag(C.Tag.AUTH_LOADING).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.AUTH_SUBMIT).assertIsEnabled()
  }

  @Test
  fun everyErrorHasAReadableMessage() {
    val expected =
        listOf(
            "Enter a valid email address.",
            "Enter your password.",
            "Use a password with at least 6 characters.",
            "Passwords do not match.",
            "The email or password is incorrect.",
            "An account already exists with this email.",
            "Could not connect. Check your connection and try again.",
            "Something went wrong. Please try again.",
        )
    AuthFormError.entries.zip(expected).forEach { (error, message) ->
      assertEquals(message, compose.activity.getString(error.messageResource(), 6))
    }
  }
}
