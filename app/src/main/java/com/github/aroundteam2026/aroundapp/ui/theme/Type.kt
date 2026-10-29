// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Shared UI styles read from the light and dark mockups; unspecified line height means Figma Auto.
 *
 * The helpers that build them live in here, not beside [Typography]: else reading a style before
 * the theme ever ran would build [Typography] from styles that don't exist yet.
 */
object AroundTextStyles {
  /**
   * Creates a bold Bricolage Grotesque heading using Figma text metrics.
   *
   * @param size Font size in sp.
   * @param height Line height in sp; null preserves Figma Auto line height.
   * @param tracking Letter spacing in sp; defaults to -3% of the font size.
   */
  private fun heading(size: Int, height: Int? = null, tracking: Double = -size * 0.03) =
      TextStyle(
          fontFamily = AroundFonts.Title,
          fontWeight = FontWeight.Bold,
          fontSize = size.sp,
          lineHeight = height?.sp ?: TextUnit.Unspecified,
          letterSpacing = tracking.sp,
      )

  /**
   * Creates an Instrument Sans body or control style with zero letter spacing.
   *
   * @param size Font size in sp.
   * @param height Line height in sp; null preserves Figma Auto line height.
   * @param weight Font weight for the text's role in the design.
   */
  private fun body(size: Int, height: Int? = null, weight: FontWeight = FontWeight.Normal) =
      TextStyle(
          fontFamily = AroundFonts.Body,
          fontWeight = weight,
          fontSize = size.sp,
          lineHeight = height?.sp ?: TextUnit.Unspecified,
          letterSpacing = 0.sp,
      )

  val Hero = heading(38, 41)
  val SetupTitle = heading(32, 35)
  val PageTitle = heading(30)
  val EmptyPageTitle = heading(30, 33)
  val SheetTitle = heading(24, 26)
  val OverlayTitle = heading(22, tracking = 0.0)
  val AvatarLarge = heading(20, tracking = 0.0)
  val CardHeading = heading(20, tracking = 0.0)
  val AvatarSmall = heading(13, tracking = 0.0)
  val Tagline = body(17)
  val BodyLarge = body(16, 24)
  val TextArea = body(16, 23)
  val FieldValue = body(16)
  val Description = body(15, 22)
  val RewardDetails = body(15, 21)
  val Body = body(15)
  val BodyMedium = body(14, 20)
  val InlineBody = body(14)
  val Caption = body(13)
  val RowDescription = body(13, 18)
  val Small = body(12)
  val Button = body(16, weight = FontWeight.SemiBold)
  val CardTitle = body(17, weight = FontWeight.SemiBold)
  val RowTitle = body(16, weight = FontWeight.SemiBold)
  val Subtitle = body(15, weight = FontWeight.SemiBold)
  val FieldLabel = body(14, weight = FontWeight.SemiBold)
  val Helper = body(13, weight = FontWeight.SemiBold)
  val Badge = body(12, weight = FontWeight.SemiBold)
  val Label = body(11, weight = FontWeight.SemiBold)
  val Chip = body(14, weight = FontWeight.Medium)
  val FilterChip = body(13, weight = FontWeight.Medium)
  val MapLabel =
      TextStyle(
          fontFamily = AroundFonts.Title,
          fontWeight = FontWeight.Medium,
          fontSize = 11.sp,
          letterSpacing = 0.44.sp,
      )
  val MapWaterLabel = MapLabel.copy(fontSize = 13.sp, letterSpacing = 3.12.sp)
}

val Typography =
    Typography(
        displayLarge = AroundTextStyles.Hero,
        displayMedium = AroundTextStyles.SetupTitle,
        displaySmall = AroundTextStyles.EmptyPageTitle,
        headlineLarge = AroundTextStyles.SetupTitle,
        headlineMedium = AroundTextStyles.PageTitle,
        headlineSmall = AroundTextStyles.SheetTitle,
        titleLarge = AroundTextStyles.CardTitle,
        titleMedium = AroundTextStyles.Subtitle,
        titleSmall = AroundTextStyles.FieldLabel,
        bodyLarge = AroundTextStyles.BodyLarge,
        bodyMedium = AroundTextStyles.BodyMedium,
        bodySmall = AroundTextStyles.Caption,
        labelLarge = AroundTextStyles.Button,
        labelMedium = AroundTextStyles.Chip,
        labelSmall = AroundTextStyles.Badge,
    )
