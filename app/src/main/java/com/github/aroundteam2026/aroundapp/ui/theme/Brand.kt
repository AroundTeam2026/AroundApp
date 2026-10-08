// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.github.aroundteam2026.aroundapp.R

// The design's tokens, for the screens that already follow it, until the app's theme does.

/**
 * The earlier design's colours, which the quest markers still use until they follow the Sprint 1
 * design. New code reads [AroundTheme.colors] instead.
 */
object AroundColors {
  /** The brand purple: primary actions, rewards. */
  val Purple = Color(0xFF4D0092)
  /** The secondary teal: the venue's avatar, its ring on the map, distances. */
  val Teal = Color(0xFF6FA8A0)
  /** The cream background, and text on purple. */
  val Cream = Color(0xFFFDFFE8)
  /** Cards and sheets, above the cream. */
  val Paper = Color(0xFFFFFFF6)
  /** Main text. */
  val Ink = Color(0xFF2D2D2D)
  /** Secondary text. */
  val InkMuted = Color(0xFF6E6C66)
  /** Hairlines and outlines. */
  val Line = Color(0xFFE3E5CF)
}

/** The design's fonts: Bricolage Grotesque for titles, Instrument Sans for everything else. */
@OptIn(ExperimentalTextApi::class)
object AroundFonts {
  /** Titles, in bold. */
  val Title =
      FontFamily(
          Font(
              R.font.venue_bricolage_grotesque,
              weight = FontWeight.Bold,
              variationSettings =
                  FontVariation.Settings(
                      FontVariation.weight(700),
                      FontVariation.Setting("wdth", 100f),
                      FontVariation.opticalSizing(14.sp),
                  ),
          )
      )

  /** Body text, regular or semibold. */
  val Body =
      FontFamily(
          Font(
              R.font.venue_instrument_sans,
              weight = FontWeight.Normal,
              variationSettings =
                  FontVariation.Settings(
                      FontVariation.weight(400),
                      FontVariation.Setting("wdth", 100f),
                  ),
          ),
          Font(
              R.font.venue_instrument_sans,
              weight = FontWeight.SemiBold,
              variationSettings =
                  FontVariation.Settings(
                      FontVariation.weight(600),
                      FontVariation.Setting("wdth", 100f),
                  ),
          ),
      )
}
