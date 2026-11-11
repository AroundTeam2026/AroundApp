// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.profile

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.github.aroundteam2026.aroundapp.model.user.FakeUserPictureRepository
import com.github.aroundteam2026.aroundapp.model.user.FakeUserRepository
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.User
import com.github.aroundteam2026.aroundapp.model.user.UserRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileEditScreenTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val users = FakeUserRepository()
  private val pictures = FakeUserPictureRepository()

  private fun launch(
      repository: UserRepository = users,
      pictureUrl: String? = null,
      createdAt: Long = 1L,
  ): ProfileSetupViewModel {
    runBlocking {
      users.createUser(User("explorer", "Ada", Role.EXPLORER, createdAt, pictureUrl)).getOrThrow()
    }
    val model = ProfileSetupViewModel("explorer", repository, pictures)
    compose.setContent { AroundAppTheme { ProfileScreen({}, model, pictures) } }
    compose.waitUntil(5_000) { model.destination.value == ProfileSetupDestination.HOME }
    return model
  }

  @Test
  fun profileShowsTheSavedNameAndWhenTheExplorerJoined() {
    // Mid-October 2025 in every time zone
    launch(createdAt = 1_760_500_000_000L)
    compose.onNodeWithText("Ada").assertIsDisplayed()
    compose.onNodeWithText("Explorer · since October 2025").assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_NAME).assertDoesNotExist()
  }

  @Test
  fun editOpensWithTheSavedNameThenUpdatesTheProfile() {
    launch()
    compose.onNodeWithTag(C.Tag.PROFILE_EDIT_ACTION).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_NAME).assertTextContains("Ada")
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_NAME).performTextClearance()
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_NAME).performTextInput("Bea")
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_CONTINUE).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_EDIT_ACTION).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_NAME).assertDoesNotExist()
    assertEquals("Bea", runBlocking { users.getUser("explorer")?.displayName })
  }

  @Test
  fun failedEditShowsTheFigmaErrorAndKeepsTheFormOpen() {
    val refusing =
        object : UserRepository by users {
          override suspend fun updateProfile(
              uid: String,
              displayName: String,
              pictureUrl: String?,
          ) = Result.failure<Unit>(IllegalStateException("offline"))
        }
    launch(refusing)
    compose.onNodeWithTag(C.Tag.PROFILE_EDIT_ACTION).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_NAME).performTextClearance()
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_NAME).performTextInput("Bea")
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_CONTINUE).performClick()
    compose
        .onNodeWithTag(C.Tag.PROFILE_SETUP_ERROR)
        .assertTextEquals("Couldn't save your changes. Check your connection and try again.")
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_NAME).assertTextContains("Bea")
    compose.onNodeWithTag(C.Tag.PROFILE_EDIT_BACK).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_NAME).assertDoesNotExist()
  }

  @Test
  fun removePhotoActionClearsTheSavedPicture() {
    launch(pictureUrl = "gs://old-picture")
    compose.onNodeWithTag(C.Tag.PROFILE_EDIT_ACTION).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_EDIT_REMOVE_PHOTO).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_CONTINUE).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_EDIT_ACTION).assertIsDisplayed()
    assertEquals(null, runBlocking { users.getUser("explorer")?.pictureUrl })
  }
}
