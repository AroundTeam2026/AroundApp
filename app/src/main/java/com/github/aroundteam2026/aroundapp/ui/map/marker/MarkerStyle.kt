// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.aroundteam2026.aroundapp.ui.theme.AroundColors
import com.github.aroundteam2026.aroundapp.ui.theme.AroundFonts

/**
 * Everything that decides how quest markers look. The pin and the card read only this, so following
 * a new design means changing [MarkerDefaults], not the composables.
 *
 * @property pinShape The pin's outline; its pointer's tip must be the bottom centre, where the map
 *   anchors the pin.
 * @property cardShape The card's outline, above its pointer.
 * @property pointerShape The card's pointer; its tip must be the bottom centre.
 * @property avatarShape The outline of the avatar's background on the card.
 * @property chipShape The outline of the card's chips.
 */
@Immutable
data class MarkerStyle(
    val colors: MarkerColors,
    val typography: MarkerTypography,
    val dimensions: MarkerDimensions,
    val pinShape: Shape,
    val cardShape: Shape,
    val pointerShape: Shape,
    val avatarShape: Shape,
    val chipShape: Shape,
)

/**
 * The marker colours.
 *
 * @property accent Draws the pin's icon, and fills the reward chip.
 * @property onAccent Text on [accent].
 * @property secondary Fills the avatar's background on the card, and the distance chip.
 * @property onSecondary The avatar and text on [secondary].
 * @property pinRing The ring around the pin's circle, and its pointer.
 * @property pinSurface Inside the pin's ring, behind its icon.
 * @property surface Fills the card and its pointer.
 * @property onSurface The card's main text, and the party chip's.
 * @property onSurfaceVariant The card's secondary text.
 * @property outline Rings the card and the party chip.
 * @property area Fills the venue's area around its pin; mostly transparent, so the map shows.
 */
@Immutable
data class MarkerColors(
    val accent: Color,
    val onAccent: Color,
    val secondary: Color,
    val onSecondary: Color,
    val pinRing: Color,
    val pinSurface: Color,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val area: Color,
)

/**
 * The card's text styles.
 *
 * @property questTitle The featured quest's title.
 * @property venueName The venue's name, under the title.
 * @property chip The text of the chips.
 * @property moreQuests How many more quests the venue has.
 * @property initials The venue's initials in its avatar.
 */
@Immutable
data class MarkerTypography(
    val questTitle: TextStyle,
    val venueName: TextStyle,
    val chip: TextStyle,
    val moreQuests: TextStyle,
    val initials: TextStyle,
)

/**
 * The marker sizes.
 *
 * @property pinDiameter Diameter of the pin's circle, which is also the pin's width.
 * @property pinRingWidth Width of the ring around the pin's circle.
 * @property pinIconPadding Space between the pin's edge and its icon.
 * @property pointerWidth Width of the pointer below the pin and the card, where it meets them.
 * @property pointerHeight Height of the pointer below the pin and the card.
 * @property outlineWidth Width of the line around the card and the party chip.
 * @property cardWidth Width of the card, whatever it says.
 * @property cardPadding Space between the card's edge and its content.
 * @property cardCornerRadius Radius of the card's corners.
 * @property cardAvatarSize Size of the avatar's background on the left of the card.
 * @property cardAvatarPadding Space between the avatar's background and the avatar.
 * @property cardGap Space between the avatar and the text.
 * @property cardLineSpacing Space between the card's lines.
 * @property chipSpacing Space between chips, across and down.
 * @property chipHorizontalPadding Space on either side of a chip's text.
 * @property chipVerticalPadding Space above and below a chip's text.
 */
@Immutable
data class MarkerDimensions(
    val pinDiameter: Dp = 44.dp,
    val pinRingWidth: Dp = 3.dp,
    val pinIconPadding: Dp = 11.dp,
    val pointerWidth: Dp = 14.dp,
    val pointerHeight: Dp = 10.dp,
    val outlineWidth: Dp = 1.dp,
    val cardWidth: Dp = 248.dp,
    val cardPadding: Dp = 14.dp,
    val cardCornerRadius: Dp = 24.dp,
    val cardAvatarSize: Dp = 44.dp,
    val cardAvatarPadding: Dp = 4.dp,
    val cardGap: Dp = 12.dp,
    val cardLineSpacing: Dp = 4.dp,
    val chipSpacing: Dp = 6.dp,
    val chipHorizontalPadding: Dp = 10.dp,
    val chipVerticalPadding: Dp = 3.dp,
)

/** The markers' default style: the design's purple, teal and cream, in its fonts. */
object MarkerDefaults {
  /** The default sizes. */
  val dimensions = MarkerDimensions()

  /** The default colours. */
  fun colors(): MarkerColors =
      MarkerColors(
          accent = AroundColors.Purple,
          onAccent = AroundColors.Cream,
          secondary = AroundColors.Teal,
          onSecondary = AroundColors.Paper,
          pinRing = AroundColors.Teal,
          pinSurface = AroundColors.Cream,
          surface = AroundColors.Paper,
          onSurface = AroundColors.Ink,
          onSurfaceVariant = AroundColors.InkMuted,
          outline = AroundColors.Line,
          area = AroundColors.Purple.copy(alpha = 0.22f),
      )

  /** The default text styles. */
  fun typography(): MarkerTypography =
      MarkerTypography(
          questTitle =
              TextStyle(
                  fontFamily = AroundFonts.Body,
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 16.sp,
                  lineHeight = 20.sp,
              ),
          venueName =
              TextStyle(fontFamily = AroundFonts.Body, fontSize = 13.sp, lineHeight = 17.sp),
          chip =
              TextStyle(
                  fontFamily = AroundFonts.Body,
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 12.sp,
                  lineHeight = 16.sp,
              ),
          moreQuests =
              TextStyle(fontFamily = AroundFonts.Body, fontSize = 12.sp, lineHeight = 16.sp),
          initials =
              TextStyle(
                  fontFamily = AroundFonts.Title,
                  fontWeight = FontWeight.Bold,
                  fontSize = 16.sp,
                  lineHeight = 16.sp,
              ),
      )

  /** The default style; pass any part to replace it. The shapes follow [dimensions]. */
  fun style(
      colors: MarkerColors = colors(),
      typography: MarkerTypography = typography(),
      dimensions: MarkerDimensions = this.dimensions,
  ): MarkerStyle =
      MarkerStyle(
          colors = colors,
          typography = typography,
          dimensions = dimensions,
          pinShape = PinShape(dimensions.pointerWidth),
          cardShape = RoundedCornerShape(dimensions.cardCornerRadius),
          pointerShape = PointerShape,
          avatarShape = CircleShape,
          chipShape = CircleShape,
      )
}
