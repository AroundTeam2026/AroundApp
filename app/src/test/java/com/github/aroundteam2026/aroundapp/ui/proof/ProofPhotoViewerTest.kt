// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.proof

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The full-screen proof photo: what it says, its zoom, and closing it with Close or Back. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w413dp-h917dp-mdpi")
class ProofPhotoViewerTest {
  @get:Rule val compose = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()
  private var closes = 0

  private fun show(subtitle: String? = SUBTITLE) {
    compose.setContent {
      ProofPhotoViewer(
          ImageBitmap(PHOTO_WIDTH, PHOTO_HEIGHT),
          DESCRIPTION,
          TITLE,
          subtitle,
          onClose = { closes++ },
      )
    }
  }

  private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

  private fun zoomLabel(level: String) = context.getString(R.string.proof_viewer_zoom_level, level)

  private fun zoomShown() =
      node(C.Tag.PROOF_VIEWER_ZOOM)
          .fetchSemanticsNode()
          .config[SemanticsProperties.Text]
          .joinToString()

  private fun pinchOut() {
    node(C.Tag.PROOF_VIEWER_IMAGE).performTouchInput {
      pinch(
          start0 = center - Offset(PINCH_START, 0f),
          end0 = center - Offset(PINCH_END, 0f),
          start1 = center + Offset(PINCH_START, 0f),
          end1 = center + Offset(PINCH_END, 0f),
      )
    }
    compose.waitForIdle()
  }

  @Test
  fun opensFullScreenWithWhoAndWhatAtTheTop() {
    show()

    node(C.Tag.PROOF_VIEWER).assertIsDisplayed()
    compose.onNodeWithContentDescription(DESCRIPTION, useUnmergedTree = true).assertIsDisplayed()
    node(C.Tag.PROOF_VIEWER_TITLE).assertTextEquals(TITLE)
    node(C.Tag.PROOF_VIEWER_SUBTITLE).assertTextEquals(SUBTITLE)
    node(C.Tag.PROOF_VIEWER_ZOOM).assertTextEquals(zoomLabel("1"))
    compose.onNodeWithText(context.getString(R.string.proof_viewer_hint)).assertIsDisplayed()
  }

  @Test
  fun withoutASubtitleOnlyTheTitleIsShown() {
    show(subtitle = null)

    node(C.Tag.PROOF_VIEWER_TITLE).assertTextEquals(TITLE)
    node(C.Tag.PROOF_VIEWER_SUBTITLE).assertDoesNotExist()
  }

  @Test
  fun aPinchZoomsInAndADoubleTapFitsThePhotoAgain() {
    show()

    pinchOut()
    // Touch slop takes a little of the 3× pinch, so only check that it zoomed in
    val level = zoomShown().removeSuffix(zoomLabel("")).toFloat()
    assertTrue("Zoomed to $level", level > MIN_ZOOM)

    node(C.Tag.PROOF_VIEWER_IMAGE).performTouchInput { doubleClick() }
    compose.waitForIdle()
    node(C.Tag.PROOF_VIEWER_ZOOM).assertTextEquals(zoomLabel("1"))
  }

  @Test
  fun closeClosesIt() {
    show()

    node(C.Tag.PROOF_VIEWER_CLOSE).performClick()

    assertEquals(1, closes)
  }

  @Test
  fun backClosesIt() {
    show()

    Espresso.pressBack()
    compose.waitForIdle()

    assertEquals(1, closes)
  }

  @Test
  fun thePreviewShowsTheViewer() {
    compose.setContent { ProofPhotoViewerPreview() }

    node(C.Tag.PROOF_VIEWER).assertIsDisplayed()
  }

  private companion object {
    const val TITLE = "yusuf.s"
    const val SUBTITLE = "Find the hidden fox · sent 2 min. ago"
    const val DESCRIPTION = "Photo sent by yusuf.s"
    const val PHOTO_WIDTH = 400
    const val PHOTO_HEIGHT = 300
    // Fingers 100px apart moved 300px apart: a 3× pinch
    const val PINCH_START = 50f
    const val PINCH_END = 150f
  }
}
