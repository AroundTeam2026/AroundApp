// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.resources.C
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Tests the round pin with its pointer, which marks a venue on the map. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuestPinTest {
  @get:Rule val composeTestRule = createComposeRule()

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  @Test
  fun isAsWideAsItsCircleAndTallEnoughForThePointer() {
    composeTestRule.setContent { QuestPin(VenueAvatar.QuestFlag) }

    val dimensions = MarkerDefaults.dimensions
    node(C.Tag.QUEST_PIN)
        .assertWidthIsEqualTo(dimensions.pinDiameter)
        .assertHeightIsEqualTo(dimensions.pinDiameter + dimensions.pointerHeight)
  }

  @Test
  fun followsTheStyleItIsGiven() {
    composeTestRule.setContent {
      QuestPin(
          VenueAvatar.QuestFlag,
          style =
              MarkerDefaults.style(
                  dimensions = MarkerDimensions(pinDiameter = 60.dp, pointerHeight = 20.dp)
              ),
      )
    }

    node(C.Tag.QUEST_PIN).assertWidthIsEqualTo(60.dp).assertHeightIsEqualTo(80.dp)
  }

  @Test
  fun showsTheQuestFlagByDefault() {
    composeTestRule.setContent { QuestPin(VenueAvatar.QuestFlag) }

    node(C.Tag.QUEST_FLAG_AVATAR).assertIsDisplayed()
  }

  @Test
  fun showsTheAvatarItIsGivenCentredInTheCircle() {
    composeTestRule.setContent { QuestPin(TestAvatar("lumen")) }

    val diameter = MarkerDefaults.dimensions.pinDiameter
    val pin = node(C.Tag.QUEST_PIN).getBoundsInRoot()
    val avatar = node(TestAvatar.tagOf("lumen")).getBoundsInRoot()
    assertDpEquals("x", pin.left + diameter / 2, (avatar.left + avatar.right) / 2)
    assertDpEquals("y", pin.top + diameter / 2, (avatar.top + avatar.bottom) / 2)
  }

  @Test
  fun aRingAndPointerSurroundTheLighterCircle() {
    // As in the design: a cream circle in a teal ring, its teal pointer marking the venue
    composeTestRule.setContent {
      val colors = MarkerDefaults.colors().copy(pinRing = Color.Red, pinSurface = Color.Blue)
      QuestPin(TestAvatar("none"), style = MarkerDefaults.style(colors = colors))
    }

    val dimensions = MarkerDefaults.dimensions
    val height = dimensions.pinDiameter + dimensions.pointerHeight
    val image = node(C.Tag.QUEST_PIN).captureToImage()
    // Fractions of the pin's height: the ring's top, just inside it, the middle, near the tip
    fun at(dpFromTop: Float) = image.colorAt(0.5f, dpFromTop / height.value)

    assertTrue("ring", at(dimensions.pinRingWidth.value / 2) == Color.Red)
    assertTrue("inside the ring", at(dimensions.pinRingWidth.value + 2) == Color.Blue)
    assertTrue("centre", at(dimensions.pinDiameter.value / 2) == Color.Blue)
    assertTrue("pointer", at(height.value - 2) == Color.Red)
  }

  @Test
  fun theIconIsDrawnInTheAccentColour() {
    var tint: Color? = null
    composeTestRule.setContent {
      val colors = MarkerDefaults.colors().copy(accent = Color.Green)
      QuestPin(RecordingAvatar { tint = it }, style = MarkerDefaults.style(colors = colors))
    }
    composeTestRule.waitForIdle()

    assertTrue(tint == Color.Green)
  }

  @Test
  fun theShapeIsACircleWithATriangleBelowPointingDown() {
    // Drawn in one colour, so the pixels show the outline: a circle on top, a tip at the bottom
    // centre where the map anchors the pin, and nothing in the corners around them
    val shape = PinShape(pointerWidth = 16.dp)
    composeTestRule.setContent {
      Box(Modifier.testTag("shape").size(40.dp, 60.dp).background(Color.Red, shape))
    }

    val image = node("shape").captureToImage()
    fun filled(x: Float, y: Float) = image.colorAt(x, y) == Color.Red

    assertTrue("circle centre", filled(0.5f, 1 / 3f))
    assertTrue("circle's left edge", filled(0.05f, 1 / 3f))
    assertTrue("near the tip", filled(0.5f, 0.95f))
    assertTrue("top-left corner", !filled(0.03f, 0.03f))
    assertTrue("top-right corner", !filled(0.97f, 0.03f))
    assertTrue("beside the tip, left", !filled(0.1f, 0.95f))
    assertTrue("beside the tip, right", !filled(0.9f, 0.95f))
  }

  @Test
  fun aPointerWiderThanTheCircleIsKeptWithinIt() {
    // A bad style value must not draw outside the pin's bounds, where the map would cut it off
    val shape = PinShape(pointerWidth = 100.dp)
    composeTestRule.setContent {
      Box(Modifier.testTag("shape").size(40.dp, 60.dp).background(Color.Red, shape))
    }

    val image = node("shape").captureToImage()
    assertTrue("circle centre", image.colorAt(0.5f, 1 / 3f) == Color.Red)
    assertTrue("near the tip", image.colorAt(0.5f, 0.97f) == Color.Red)
  }
}

/** An avatar that reports the tint it is drawn in. */
private class RecordingAvatar(private val onTint: (Color) -> Unit) : VenueAvatar {
  @androidx.compose.runtime.Composable
  override fun Content(tint: Color, modifier: Modifier) {
    onTint(tint)
  }
}

/** The colour at ([x], [y]), given as fractions of the width and height. */
private fun ImageBitmap.colorAt(x: Float, y: Float): Color =
    toPixelMap()[(x * (width - 1)).toInt(), (y * (height - 1)).toInt()]
