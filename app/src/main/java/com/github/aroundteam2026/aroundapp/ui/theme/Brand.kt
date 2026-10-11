// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.github.aroundteam2026.aroundapp.R

// The design's raw tokens; AroundAppTheme maps them to Material roles.

/** The design's colours. */
object AroundColors {
  /** The brand purple: primary actions, rewards. */
  val Purple = Color(0xFF4D0092)
  val PrimaryDark = Color(0xFF36006A)
  /** The secondary teal: the venue's avatar, its ring on the map, distances. */
  val Teal = Color(0xFF6FA8A0)
  /** Second-draft teal; [Teal] preserves existing screen assets. */
  val Tertiary = Color(0xFF5EA89D)
  val Secondary = Color(0xFF62BAAD)
  val Panel = Color(0xFFF6F8DE)
  val Lavender = Color(0xFFF3E8FF)
  val BabyBlue = Color(0xFFDDF1EE)
  val MapLabel = Color(0xFF8A7560)
  val Sage = Color(0xFF3E8C80)
  val Error = Color(0xFFA3312A)
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
  val DarkSurface = Color(0xFF383838)
  val DarkMuted = Color(0xFFB5B6A6)
  val DarkLine = Color(0xFF474547)
  val DarkPanel = Color(0xFF333333)
  val DarkLavender = Color(0xFF3F2E52)
  val DarkBabyBlue = Color(0xFF2F4744)
  val DarkMapLabel = Color(0xFFC9B8A4)
  val DarkError = Color(0xFFE5736B)
}

/** The design's fonts: Bricolage Grotesque for titles, Instrument Sans for everything else. */
@OptIn(ExperimentalTextApi::class)
object AroundFonts {
  /** Titles in bold and illustrated map labels in medium. */
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
          ),
          Font(
              R.font.venue_bricolage_grotesque,
              weight = FontWeight.Medium,
              variationSettings =
                  FontVariation.Settings(
                      FontVariation.weight(500),
                      FontVariation.Setting("wdth", 100f),
                      FontVariation.opticalSizing(14.sp),
                  ),
          ),
      )

  /** Body text and controls, in regular, medium and semibold weights. */
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
          Font(
              R.font.venue_instrument_sans,
              weight = FontWeight.Medium,
              variationSettings =
                  FontVariation.Settings(
                      FontVariation.weight(500),
                      FontVariation.Setting("wdth", 100f),
                  ),
          ),
      )
}
