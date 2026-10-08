// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.ui.theme.AroundColors

/**
 * Everything that decides how quest markers look. The pin reads only this, so following a new
 * design means changing [MarkerDefaults], not the composables.
 *
 * @property pinShape The pin's outline; its pointer's tip must be the bottom centre, where the map
 *   anchors the pin.
 */
@Immutable
data class MarkerStyle(
    val colors: MarkerColors,
    val dimensions: MarkerDimensions,
    val pinShape: Shape,
)

/**
 * The marker colours.
 *
 * @property accent Draws the pin's icon.
 * @property pinRing The ring around the pin's circle, and its pointer.
 * @property pinSurface Inside the pin's ring, behind its icon.
 * @property area Fills the venue's area around its pin; mostly transparent, so the map shows.
 */
@Immutable
data class MarkerColors(
    val accent: Color,
    val pinRing: Color,
    val pinSurface: Color,
    val area: Color,
)

/**
 * The marker sizes.
 *
 * @property pinDiameter Diameter of the pin's circle, which is also the pin's width.
 * @property pinRingWidth Width of the ring around the pin's circle.
 * @property pinIconPadding Space between the pin's edge and its icon.
 * @property pointerWidth Width of the pointer below the pin, where it meets it.
 * @property pointerHeight Height of the pointer below the pin.
 */
@Immutable
data class MarkerDimensions(
    val pinDiameter: Dp = 44.dp,
    val pinRingWidth: Dp = 3.dp,
    val pinIconPadding: Dp = 11.dp,
    val pointerWidth: Dp = 14.dp,
    val pointerHeight: Dp = 10.dp,
)

/** The markers' default style: the design's purple, teal and cream. */
object MarkerDefaults {
  /** The default sizes. */
  val dimensions = MarkerDimensions()

  /** The default colours. */
  fun colors(): MarkerColors =
      MarkerColors(
          accent = AroundColors.Purple,
          pinRing = AroundColors.Teal,
          pinSurface = AroundColors.Cream,
          area = AroundColors.Purple.copy(alpha = 0.22f),
      )

  /** The default style; pass any part to replace it. The pin's shape follows [dimensions]. */
  fun style(
      colors: MarkerColors = colors(),
      dimensions: MarkerDimensions = this.dimensions,
  ): MarkerStyle =
      MarkerStyle(
          colors = colors,
          dimensions = dimensions,
          pinShape = PinShape(dimensions.pointerWidth),
      )
}
