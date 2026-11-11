// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.profile

import android.graphics.Bitmap
import android.graphics.Color.RED
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
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
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileEditScreenTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val users = FakeUserRepository()
  // Every uploaded picture is a red square, so the avatar's colour tells it from the placeholder
  private val pictures = FakeUserPictureRepository { redPhoto() }

  private fun redPhoto(): ByteArray {
    val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(RED) }
    return ByteArrayOutputStream().use {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
      it.toByteArray()
    }
  }

  /** A saved picture, as a profile holds it. */
  private fun savedPicture(): String = runBlocking {
    pictures.upload("explorer", Uri.parse("content://photos/saved")).getOrThrow()
  }

  /** Whether the edit form's avatar shows the red picture rather than the placeholder. */
  private fun avatarShowsThePicture(): Boolean {
    val image = compose.onNodeWithTag(C.Tag.PROFILE_SETUP_AVATAR).captureToImage()
    val centre = image.toPixelMap()[image.width / 2, image.height / 2].toArgb()
    return android.graphics.Color.red(centre) > 200 && android.graphics.Color.green(centre) < 60
  }

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

  @Test
  fun removePhotoShowsThePlaceholderAtOnce() {
    launch(pictureUrl = savedPicture())
    compose.onNodeWithTag(C.Tag.PROFILE_EDIT_ACTION).performClick()
    compose.waitUntil(5_000) { avatarShowsThePicture() }

    compose.onNodeWithTag(C.Tag.PROFILE_EDIT_REMOVE_PHOTO).performClick()
    compose.waitUntil(5_000) { !avatarShowsThePicture() }
    compose.onNodeWithTag(C.Tag.PROFILE_EDIT_REMOVE_PHOTO).assertDoesNotExist()
  }

  @Test
  fun removePhotoIsOnlyOfferedWithAPicture() {
    launch()
    compose.onNodeWithTag(C.Tag.PROFILE_EDIT_ACTION).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_NAME).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.PROFILE_EDIT_REMOVE_PHOTO).assertDoesNotExist()
  }

  @Test
  fun theProfileCardsAreAnnouncedAsButtons() {
    launch()
    for (tag in listOf(C.Tag.PROFILE_EDIT_ACTION, C.Tag.PROFILE_SIGN_OUT)) {
      compose
          .onNodeWithTag(tag)
          .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, SemanticsRole.Button))
    }
  }
}
