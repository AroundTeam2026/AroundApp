// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Figma roles without a direct Material equivalent: accent-text, paper, cream and sage. */
@Immutable
data class AroundThemeColors(
    val accentText: Color,
    val paper: Color,
    val cream: Color,
    val sage: Color,
    val babyBlue: Color,
    val mapLabel: Color,
)

internal val LightThemeColors =
    AroundThemeColors(
        accentText = AroundColors.Purple,
        paper = AroundColors.Panel,
        cream = AroundColors.Lavender,
        sage = AroundColors.Sage,
        babyBlue = AroundColors.BabyBlue,
        mapLabel = AroundColors.MapLabel,
    )

internal val DarkThemeColors =
    AroundThemeColors(
        accentText = AroundColors.Cream,
        paper = AroundColors.DarkPanel,
        cream = AroundColors.DarkLavender,
        sage = AroundColors.Secondary,
        babyBlue = AroundColors.DarkBabyBlue,
        mapLabel = AroundColors.DarkMapLabel,
    )

internal val LocalAroundThemeColors = staticCompositionLocalOf { LightThemeColors }

/** Theme-aware design colors for the asset-import tasks that follow DS0. */
object AroundTheme {
  val colors: AroundThemeColors
    @Composable @ReadOnlyComposable get() = LocalAroundThemeColors.current
}
