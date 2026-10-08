// Co-authored-by: OpenAI Codex
package com.github.aroundteam2026.aroundapp.ui.auth

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthDemoTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun existingSessionShowsConfirmationAndSignOutReturnsToForm() {
    val repository = FakeAuthRepository("demo-user")
    compose.setContent { AroundAppTheme { AuthDemo(repository) } }
    compose.onNodeWithText("You are signed in.").assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
    compose.onNodeWithText("Sign out").performClick()
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    assertNull(repository.currentUserId.value)
  }

  @Test
  fun authenticationChangesShowConfirmation() {
    val repository = FakeAuthRepository()
    compose.setContent { AroundAppTheme { AuthDemo(repository) } }
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    compose.runOnIdle {
      kotlinx.coroutines.runBlocking {
        repository.signUpWithEmail("ada@around.test", "123456").getOrThrow()
      }
    }
    compose.onNodeWithText("You are signed in.").assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
  }
}
