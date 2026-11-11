// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.profile

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.aroundteam2026.aroundapp.model.user.FakeUserPictureRepository
import com.github.aroundteam2026.aroundapp.model.user.FakeUserRepository
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.User
import com.github.aroundteam2026.aroundapp.model.user.UserRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.DarkColorScheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileSetupScreenTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val users = FakeUserRepository()

  @Test
  fun firstTimeExplorerSeesSetupThenHomeOnlyAfterSaving() {
    runBlocking { users.createUser(User("explorer", "", Role.EXPLORER, 1L)).getOrThrow() }
    compose.setContent {
      AroundAppTheme {
        ExplorerProfileSetupGate("explorer", users, FakeUserPictureRepository(), onSignOut = {}) {
          Text("Explorer home", modifier = Modifier.testTag(C.Tag.APP))
        }
      }
    }
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_ADD_PHOTO).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_CONTINUE).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_ERROR).assertTextEquals("Enter a display name.")
    compose.onNodeWithTag(C.Tag.APP).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_NAME).performTextInput("Ada")
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_CONTINUE).performClick()
    compose.onNodeWithTag(C.Tag.APP).assertIsDisplayed()
  }

  @Test
  fun failedSaveUnlocksTheFormAndShowsAnError() {
    runBlocking { users.createUser(User("explorer", "", Role.EXPLORER, 1L)).getOrThrow() }
    val result = CompletableDeferred<Result<Unit>>()
    val refusing =
        object : UserRepository by users {
          override suspend fun updateProfile(
              uid: String,
              displayName: String,
              pictureUrl: String?,
          ) = result.await()
        }
    compose.setContent {
      AroundAppTheme {
        ExplorerProfileSetupGate(
            "explorer",
            refusing,
            FakeUserPictureRepository(),
            onSignOut = {},
        ) {
          Text("Explorer home", modifier = Modifier.testTag(C.Tag.APP))
        }
      }
    }
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_NAME).performTextInput("Ada")
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_CONTINUE).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_CONTINUE).assertIsNotEnabled()
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_NAME).assertIsNotEnabled()
    result.complete(Result.failure(IllegalStateException("offline")))
    compose
        .onNodeWithTag(C.Tag.PROFILE_SETUP_ERROR)
        .assertTextEquals("Could not save your profile. Please try again.")
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_CONTINUE).assertIsEnabled()
    compose.onNodeWithTag(C.Tag.APP).assertDoesNotExist()
  }

  @Test
  fun loadErrorExplainsItselfAndOffersRetryAndSignOut() {
    runBlocking { users.createUser(User("explorer", "", Role.EXPLORER, 1L)).getOrThrow() }
    var reads = 0
    val flaky =
        object : UserRepository by users {
          override fun observeUser(uid: String): Flow<User?> =
              if (reads++ == 0) flow { throw IllegalStateException("offline") }
              else users.observeUser(uid)
        }
    var signOuts = 0
    compose.setContent {
      AroundAppTheme {
        ExplorerProfileSetupGate(
            "explorer",
            flaky,
            FakeUserPictureRepository(),
            onSignOut = { signOuts++ },
        ) {}
      }
    }
    compose.onNodeWithTag(C.Tag.ROLE_PROFILE_ERROR).assertIsDisplayed()
    compose.onNodeWithText("Could not load your profile.").assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.ROLE_PROFILE_SIGN_OUT).performClick()
    assertEquals(1, signOuts)

    compose.onNodeWithTag(C.Tag.ROLE_PROFILE_RETRY).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_SCREEN).assertIsDisplayed()
  }

  @Test
  fun firstTimeSetupUsesTheDarkThemeBackground() {
    runBlocking { users.createUser(User("explorer", "", Role.EXPLORER, 1L)).getOrThrow() }
    compose.setContent {
      AroundAppTheme(darkTheme = true) {
        ExplorerProfileSetupGate("explorer", users, FakeUserPictureRepository(), onSignOut = {}) {}
      }
    }
    val screen = compose.onNodeWithTag(C.Tag.PROFILE_SETUP_SCREEN).captureToImage()
    assertEquals(DarkColorScheme.background, screen.toPixelMap()[0, 0])
  }
}
