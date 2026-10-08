// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colour tokens of the design's Sprint 1 mockups, named as in Figma. Read them through
 * [AroundTheme.colors], which follows dark mode.
 *
 * @property ink Main text and icons.
 * @property muted Secondary text, and unselected tabs.
 * @property primary The brand green: selected items, primary actions, distances.
 * @property onPrimary Text and icons on [primary].
 * @property paper The warm background behind the map and its header.
 * @property surface Cards, bars and buttons above the background.
 * @property line Hairlines and outlines.
 * @property mint Light green highlights, such as the selected tab's pill.
 * @property saffron The quest markers' ring.
 * @property mapLabel Place names drawn on the map.
 * @property sage Water names drawn on the map.
 * @property bg The neutral background of list screens.
 * @property error Errors.
 * @property babyBlue A tile background.
 * @property cream A tile background.
 */
@Immutable
data class AroundColorScheme(
    val ink: Color,
    val muted: Color,
    val primary: Color,
    val onPrimary: Color,
    val paper: Color,
    val surface: Color,
    val line: Color,
    val mint: Color,
    val saffron: Color,
    val mapLabel: Color,
    val sage: Color,
    val bg: Color,
    val error: Color,
    val babyBlue: Color,
    val cream: Color,
)

/** The tokens exactly as the Figma file defines them. */
val LightAroundColors =
    AroundColorScheme(
        ink = Color(0xFF1E1A1D),
        muted = Color(0xFF6B6166),
        primary = Color(0xFF005248),
        onPrimary = Color(0xFFFFFFFF),
        paper = Color(0xFFFFF8EC),
        surface = Color(0xFFFFFFFF),
        line = Color(0xFFE2E6E5),
        mint = Color(0xFFC3FCF2),
        saffron = Color(0xFFEAA443),
        mapLabel = Color(0xFF8A7560),
        sage = Color(0xFF4B8078),
        bg = Color(0xFFF6F7F6),
        error = Color(0xFFA3312A),
        babyBlue = Color(0xFFCFE6F5),
        cream = Color(0xFFFFECCC),
    )

/**
 * Dark versions of the tokens, which the Figma file lacks: warm darks that sit with the map's night
 * style, a lighter green so it reads on them, and deep tile colours that keep their hue.
 */
val DarkAroundColors =
    AroundColorScheme(
        ink = Color(0xFFEDE7EA),
        muted = Color(0xFFB0A6AB),
        primary = Color(0xFF7FD3C4),
        onPrimary = Color(0xFF00201C),
        paper = Color(0xFF2A2622),
        surface = Color(0xFF332F2C),
        line = Color(0xFF4A4541),
        mint = Color(0xFF1D4E47),
        saffron = Color(0xFFF2B65E),
        mapLabel = Color(0xFFB3A894),
        sage = Color(0xFF8DC0B8),
        bg = Color(0xFF1E1C1D),
        error = Color(0xFFFFB4AB),
        babyBlue = Color(0xFF27445A),
        cream = Color(0xFF4A3B24),
    )

/** The tokens in use: [AroundAppTheme] provides them, and the light ones apply outside it. */
val LocalAroundColors = staticCompositionLocalOf { LightAroundColors }

/** The design's theme, beside Material's. */
object AroundTheme {
  /** The colour tokens, light or dark with the theme. */
  val colors: AroundColorScheme
    @Composable @ReadOnlyComposable get() = LocalAroundColors.current
}
