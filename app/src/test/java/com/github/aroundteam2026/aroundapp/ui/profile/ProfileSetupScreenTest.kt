// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.profile

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.github.aroundteam2026.aroundapp.ui.completion.FakeActivityResults
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.DarkColorScheme
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

  private fun showSetup(
      repository: UserRepository = users,
      pictures: FakeUserPictureRepository = FakeUserPictureRepository(),
      results: FakeActivityResults = FakeActivityResults(),
  ) {
    compose.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides results) {
        AroundAppTheme {
          ExplorerProfileSetupGate("explorer", repository, pictures, onSignOut = {}) {
            Text("Explorer home", modifier = Modifier.testTag(C.Tag.APP))
          }
        }
      }
    }
  }

  private fun newExplorer() = runBlocking {
    users.createUser(User("explorer", "", Role.EXPLORER, 1L)).getOrThrow()
  }

  /** A picture file the gallery hands back, filled with [color]. */
  private fun galleryPhoto(color: Int): Uri {
    val file = File.createTempFile("gallery", ".jpg", compose.activity.cacheDir)
    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
    return Uri.fromFile(file)
  }

  /**
   * The shrunk copies ProofPhotos keeps in its cache folder ("proofs") until they are discarded.
   */
  private fun preparedCopies(): List<File> =
      File(compose.activity.cacheDir, "proofs").listFiles()?.toList().orEmpty()

  /** Whether the avatar's centre is mostly red, as the test photo is, rather than the fallback. */
  private fun SemanticsNodeInteraction.showsRed(): Boolean {
    val image = captureToImage()
    val centre = image.toPixelMap()[image.width / 2, image.height / 2].toArgb()
    return android.graphics.Color.red(centre) > 200 && android.graphics.Color.green(centre) < 60
  }

  @Test
  fun spinnerShowsUntilTheProfileArrives() {
    val pending =
        object : UserRepository by users {
          override fun observeUser(uid: String): Flow<User?> = flow { awaitCancellation() }
        }
    showSetup(pending)
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_LOADING).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_SCREEN).assertDoesNotExist()
  }

  @Test
  fun overlongNameShowsTheLimit() {
    newExplorer()
    showSetup()
    compose
        .onNodeWithTag(C.Tag.PROFILE_SETUP_NAME)
        .performTextInput("x".repeat(ProfileSetupViewModel.MAX_NAME_LENGTH + 1))
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_CONTINUE).performClick()
    compose
        .onNodeWithTag(C.Tag.PROFILE_SETUP_ERROR)
        .assertTextEquals("Use 100 characters or fewer.")
    compose.onNodeWithTag(C.Tag.APP).assertDoesNotExist()
  }

  @Test
  fun pickedPhotoIsPreviewedReplacedAndUploadedWithTheName() {
    newExplorer()
    val pictures = FakeUserPictureRepository()
    showSetup(
        pictures = pictures,
        results = FakeActivityResults(gallery = galleryPhoto(android.graphics.Color.RED)),
    )
    val avatar = compose.onNodeWithTag(C.Tag.PROFILE_SETUP_AVATAR)
    // The shrinking runs off the main thread, so wait for its preview
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_ADD_PHOTO).performClick()
    compose.waitUntil(5_000) { avatar.showsRed() && preparedCopies().size == 1 }
    val first = preparedCopies().single()
    // A second pick makes a new copy and discards the first, so only the new one is left
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_ADD_PHOTO).performClick()
    compose.waitUntil(5_000) { preparedCopies().let { it.size == 1 && it.single() != first } }
    compose.waitUntil(5_000) { avatar.showsRed() }

    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_NAME).performTextInput("Ada")
    compose.waitUntil(5_000) {
      runCatching { compose.onNodeWithTag(C.Tag.PROFILE_SETUP_CONTINUE).assertIsEnabled() }
          .isSuccess
    }
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_CONTINUE).performClick()
    compose.waitUntil(5_000) { runBlocking { users.getUser("explorer")?.pictureUrl } != null }
    val saved = runBlocking { users.getUser("explorer") }
    assertEquals("Ada", saved?.displayName)
    assertTrue(saved?.pictureUrl.orEmpty().contains("user-pictures/explorer/"))
    compose.onNodeWithTag(C.Tag.APP).assertIsDisplayed()
  }

  @Test
  fun unreadablePhotoShowsAnErrorAndKeepsTheForm() {
    newExplorer()
    val notAnImage =
        File.createTempFile("gallery", ".jpg", compose.activity.cacheDir).apply {
          writeText("not an image")
        }
    showSetup(results = FakeActivityResults(gallery = Uri.fromFile(notAnImage)))
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_ADD_PHOTO).performClick()
    compose.waitUntil(5_000) {
      compose.onAllNodesWithTag(C.Tag.PROFILE_SETUP_ERROR).fetchSemanticsNodes().isNotEmpty()
    }
    compose
        .onNodeWithTag(C.Tag.PROFILE_SETUP_ERROR)
        .assertTextEquals("Could not prepare that photo. Try another.")
    compose.onNodeWithTag(C.Tag.PROFILE_SETUP_CONTINUE).assertIsEnabled()
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
