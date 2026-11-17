// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.DarkColorScheme
import com.github.aroundteam2026.aroundapp.ui.theme.LightColorScheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MemberSeatPhotoTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun lightPhotoMatchingTheBackgroundHasAnOutline() {
    assertOutline(darkTheme = false)
  }

  @Test
  fun darkPhotoMatchingTheBackgroundHasAnOutline() {
    assertOutline(darkTheme = true)
  }

  private fun assertOutline(darkTheme: Boolean) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    val background = colors.surface
    val picture =
        Bitmap.createBitmap(PHOTO_SIZE, PHOTO_SIZE, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(background.toArgb()) }
            .asImageBitmap()
    compose.setContent {
      AroundAppTheme(darkTheme = darkTheme) {
        Box(Modifier.background(background)) {
          MemberSeat(PartyMemberUi("ada", "Ada", picture), PHOTO_SIZE.dp)
        }
      }
    }

    val pixels =
        compose.onNodeWithTag(C.Tag.QUEST_DETAIL_MEMBER_PICTURE).captureToImage().toPixelMap()
    val center = pixels.width / 2
    // Native rendering can blend the pixels on either side of the one-dp stroke.
    val outlineLuminance = colors.outline.luminance()
    val backgroundLuminance = background.luminance()
    val edge = pixels[center, 0].luminance()
    val inside = pixels[center, 1].luminance()
    assertTrue(
        "the rendered edge should be near the theme outline",
        abs(edge - outlineLuminance) < abs(backgroundLuminance - outlineLuminance) / 2,
    )
    assertTrue(
        "the next pixel should be closer to the photo than the outline",
        abs(inside - backgroundLuminance) < abs(inside - outlineLuminance),
    )
    assertEquals("untouched corner outside the circle", background, pixels[0, 0])
  }

  private companion object {
    const val PHOTO_SIZE = 36
  }
}
