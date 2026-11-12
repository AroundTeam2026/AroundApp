// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Shared Figma corners: fields, buttons, cards and sheets. */
val AroundShapes =
    Shapes(
        extraSmall = RoundedCornerShape(14.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

/** Fully rounded chips, avatars and navigation bars, independent of their size. */
val AroundPillShape = CircleShape

/** The corners the shared Material shapes don't cover: inset fields, options, cards and sheets. */
object AroundCorners {
  val ProgressSegment = RoundedCornerShape(2.dp)
  val InsetField = RoundedCornerShape(12.dp)
  val SegmentedOption = RoundedCornerShape(9.dp)
  val FilterOption = RoundedCornerShape(10.dp)
  val LocationBanner = RoundedCornerShape(18.dp)
  val Overlay = RoundedCornerShape(32.dp)
  val BottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

  /** Stat tiles and review cards. */
  val Card = RoundedCornerShape(18.dp)

  /** Photo boxes and the cards of a quest's status and list. */
  val Photo = RoundedCornerShape(22.dp)

  /** The sheet over the venue's area map. */
  val TopSheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

  /** The app bar's rounded bottom. */
  val Header = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
}
