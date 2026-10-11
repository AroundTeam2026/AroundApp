// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

// Exact Figma colors mapped to Material roles.
// The extra paper, cream, sage and accent-text roles are available through AroundTheme.colors.
internal val LightColorScheme =
    lightColorScheme(
        primary = AroundColors.Purple,
        onPrimary = AroundColors.Cream,
        secondary = AroundColors.Secondary,
        onSecondary = AroundColors.Cream,
        tertiary = AroundColors.Tertiary,
        onTertiary = AroundColors.Cream,
        background = AroundColors.Cream,
        onBackground = AroundColors.Ink,
        surface = AroundColors.Paper,
        onSurface = AroundColors.Ink,
        surfaceVariant = AroundColors.Paper,
        onSurfaceVariant = AroundColors.InkMuted,
        outline = AroundColors.Line,
        outlineVariant = AroundColors.Line,
        surfaceTint = AroundColors.Purple,
        error = AroundColors.Error,
        onError = AroundColors.Cream,
        surfaceContainerLowest = AroundColors.Cream,
        surfaceContainerLow = AroundColors.Paper,
        surfaceContainer = AroundColors.Paper,
        surfaceContainerHigh = AroundColors.Paper,
        surfaceContainerHighest = AroundColors.Paper,
    )

internal val DarkColorScheme =
    darkColorScheme(
        primary = AroundColors.Purple,
        onPrimary = AroundColors.Cream,
        secondary = AroundColors.Secondary,
        onSecondary = AroundColors.Cream,
        tertiary = AroundColors.Tertiary,
        onTertiary = AroundColors.Cream,
        background = AroundColors.Ink,
        onBackground = AroundColors.Cream,
        surface = AroundColors.DarkSurface,
        onSurface = AroundColors.Cream,
        surfaceVariant = AroundColors.DarkSurface,
        onSurfaceVariant = AroundColors.DarkMuted,
        outline = AroundColors.DarkLine,
        outlineVariant = AroundColors.DarkLine,
        surfaceTint = AroundColors.Purple,
        error = AroundColors.DarkError,
        onError = AroundColors.Ink,
        surfaceContainerLowest = AroundColors.Ink,
        surfaceContainerLow = AroundColors.DarkSurface,
        surfaceContainer = AroundColors.DarkSurface,
        surfaceContainerHigh = AroundColors.DarkSurface,
        surfaceContainerHighest = AroundColors.DarkSurface,
    )
