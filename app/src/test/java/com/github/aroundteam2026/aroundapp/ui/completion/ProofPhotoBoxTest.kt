// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.marker.assertDpEquals
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The three states of the proof photo box, with its errors and while disabled, as drawn, without
 * the camera or the gallery.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w413dp-h917dp-mdpi")
class ProofPhotoBoxTest {
  @get:Rule val compose = createComposeRule()

  private val app = ApplicationProvider.getApplicationContext<Application>()
  private val calls = mutableListOf<String>()

  private fun show(
      picked: Boolean = false,
      preview: ImageBitmap? = null,
      cameraRefused: Boolean = false,
      error: ProofPhotoError? = null,
      enabled: Boolean = true,
      questTitle: String = QUEST,
  ) {
    compose.setContent {
      AroundAppTheme {
        ProofPhotoBox(
            venueName = VENUE,
            picked = picked,
            preview = preview,
            cameraRefused = cameraRefused,
            onTakePhoto = { calls += "take" },
            onChooseFromGallery = { calls += "gallery" },
            onOpenSettings = { calls += "settings" },
            onRetake = { calls += "retake" },
            error = error,
            enabled = enabled,
            questTitle = questTitle,
        )
      }
    }
  }

  private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

  private fun text(id: Int, vararg args: Any) = compose.onNodeWithText(app.getString(id, *args))

  @Test
  fun withoutAPhotoTheBoxSaysWhatToDoAndOffersBothWays() {
    show()

    text(R.string.proof_photo_title).assertIsDisplayed()
    text(R.string.proof_photo_body, VENUE).assertIsDisplayed()
    node(C.Tag.PROOF_PHOTO_TAKE).performClick()
    node(C.Tag.PROOF_PHOTO_GALLERY).performClick()
    node(C.Tag.PROOF_PHOTO_RETAKE).assertDoesNotExist()

    assertEquals(listOf("take", "gallery"), calls)
  }

  private fun height(tag: String) = node(tag).getBoundsInRoot().let { it.bottom - it.top }

  @Test
  fun theBoxAndItsButtonsHaveTheFigmaHeights() {
    show()

    assertDpEquals("box height", FIGMA_BOX_HEIGHT, height(C.Tag.PROOF_PHOTO))
    assertDpEquals("button height", FIGMA_BUTTON_HEIGHT, height(C.Tag.PROOF_PHOTO_TAKE))
  }

  @Test
  fun aRefusedCameraIsExplainedAndOffersTheSettingsAndTheGallery() {
    show(cameraRefused = true)

    text(R.string.proof_photo_camera_refused_title).assertIsDisplayed()
    text(R.string.proof_photo_camera_refused_body).assertIsDisplayed()
    node(C.Tag.PROOF_PHOTO_TAKE).assertDoesNotExist()
    node(C.Tag.PROOF_PHOTO_OPEN_SETTINGS).performClick()
    node(C.Tag.PROOF_PHOTO_GALLERY).performClick()

    assertEquals(listOf("settings", "gallery"), calls)
  }

  @Test
  fun aPickedPhotoFillsTheBoxWithRetake() {
    show(picked = true, preview = PICKED_PHOTO)

    node(C.Tag.PROOF_PHOTO_IMAGE).assertIsDisplayed()
    node(C.Tag.PROOF_PHOTO_TAKE).assertDoesNotExist()
    node(C.Tag.PROOF_PHOTO_GALLERY).assertDoesNotExist()
    node(C.Tag.PROOF_PHOTO_RETAKE).performClick()

    assertEquals(listOf("retake"), calls)
    assertDpEquals("box height", FIGMA_BOX_HEIGHT, height(C.Tag.PROOF_PHOTO))
  }

  @Test
  fun aPhotoStillLoadingShowsTheBoxWithRetake() {
    show(picked = true, preview = null)

    node(C.Tag.PROOF_PHOTO_IMAGE).assertDoesNotExist()
    node(C.Tag.PROOF_PHOTO_RETAKE).assertIsDisplayed()
  }

  @Test
  fun tappingThePhotoOpensItFullScreenUnderTheVenueAndQuest() {
    show(picked = true, preview = PICKED_PHOTO)
    node(C.Tag.PROOF_VIEWER).assertDoesNotExist()

    node(C.Tag.PROOF_PHOTO_IMAGE).performClick()

    node(C.Tag.PROOF_VIEWER).assertIsDisplayed()
    node(C.Tag.PROOF_VIEWER_TITLE).assertTextEquals(VENUE)
    node(C.Tag.PROOF_VIEWER_SUBTITLE).assertTextEquals(QUEST)
  }

  @Test
  fun withoutAQuestTitleTheFullScreenPhotoOnlyNamesTheVenue() {
    show(picked = true, preview = PICKED_PHOTO, questTitle = "")

    node(C.Tag.PROOF_PHOTO_IMAGE).performClick()

    node(C.Tag.PROOF_VIEWER_TITLE).assertTextEquals(VENUE)
    node(C.Tag.PROOF_VIEWER_SUBTITLE).assertDoesNotExist()
  }

  @Test
  fun closingTheFullScreenPhotoReturnsToTheBox() {
    show(picked = true, preview = PICKED_PHOTO)
    node(C.Tag.PROOF_PHOTO_IMAGE).performClick()

    node(C.Tag.PROOF_VIEWER_CLOSE).performClick()

    node(C.Tag.PROOF_VIEWER).assertDoesNotExist()
    node(C.Tag.PROOF_PHOTO_RETAKE).assertIsDisplayed()
    assertEquals(emptyList<String>(), calls)
  }

  @Test
  fun eachErrorIsSaidUnderTheButtons() {
    var shown by mutableStateOf<ProofPhotoError?>(null)
    compose.setContent {
      AroundAppTheme { ProofPhotoBox(VENUE, false, null, false, {}, {}, {}, {}, error = shown) }
    }

    mapOf(
            ProofPhotoError.NO_CAMERA to R.string.proof_photo_no_camera,
            ProofPhotoError.UNREADABLE to R.string.proof_photo_unreadable,
            ProofPhotoError.TOO_BIG to R.string.proof_photo_too_big,
            ProofPhotoError.NO_SETTINGS to R.string.proof_photo_no_settings,
        )
        .forEach { (error, message) ->
          shown = error
          node(C.Tag.PROOF_PHOTO_ERROR).assertTextEquals(app.getString(message))
        }
  }

  @Test
  fun withoutAnErrorNothingIsSaid() {
    show()

    node(C.Tag.PROOF_PHOTO_ERROR).assertDoesNotExist()
  }

  @Test
  fun whileDisabledTheButtonsDoNothing() {
    show(enabled = false)

    node(C.Tag.PROOF_PHOTO_TAKE).assertIsNotEnabled().performClick()
    node(C.Tag.PROOF_PHOTO_GALLERY).assertIsNotEnabled().performClick()

    assertEquals(emptyList<String>(), calls)
  }

  @Test
  fun whileDisabledRetakeDoesNothing() {
    show(picked = true, enabled = false)

    node(C.Tag.PROOF_PHOTO_RETAKE).performClick()

    assertEquals(emptyList<String>(), calls)
  }

  private companion object {
    const val VENUE = "Café Lumière"
    const val QUEST = "Find the hidden fox"
    val PICKED_PHOTO = ImageBitmap(40, 30)
    // Figma: a 290dp box with 54dp buttons
    val FIGMA_BOX_HEIGHT = 290.dp
    val FIGMA_BUTTON_HEIGHT = 54.dp
  }
}
