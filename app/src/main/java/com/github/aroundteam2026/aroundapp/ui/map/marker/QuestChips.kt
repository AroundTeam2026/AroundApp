// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.resources.C
import java.util.Locale

/**
 * Chips for a quest's [reward], the party size it needs when that is more than one person, and how
 * far its venue is when [distanceMeters] is known, wrapping to more lines when they don't fit.
 * Draws nothing when none of them apply.
 */
@Composable
fun QuestChips(
    reward: Reward?,
    minPartySize: Int,
    distanceMeters: Double?,
    modifier: Modifier = Modifier,
    style: MarkerStyle = MarkerDefaults.style(),
) {
  val partySize = minPartySize.takeIf { it > 1 }
  if (reward == null && partySize == null && distanceMeters == null) return
  val colors = style.colors
  val spacing = Arrangement.spacedBy(style.dimensions.chipSpacing)
  FlowRow(
      modifier.testTag(C.Tag.QUEST_CHIPS),
      horizontalArrangement = spacing,
      verticalArrangement = spacing,
  ) {
    reward?.let {
      Chip(it.description, C.Tag.QUEST_CHIP_REWARD, colors.accent, colors.onAccent, style)
    }
    partySize?.let {
      Chip(
          stringResource(R.string.map_card_party_size, it),
          C.Tag.QUEST_CHIP_PARTY,
          colors.surface,
          colors.onSurface,
          style,
          BorderStroke(style.dimensions.outlineWidth, colors.outline),
      )
    }
    distanceMeters?.let {
      Chip(
          distanceText(it),
          C.Tag.QUEST_CHIP_DISTANCE,
          colors.secondary,
          colors.onSecondary,
          style,
      )
    }
  }
}

/** A one-line pill of [text] on [container], with an optional [border]. */
@Composable
private fun Chip(
    text: String,
    tag: String,
    container: Color,
    content: Color,
    style: MarkerStyle,
    border: BorderStroke? = null,
) {
  val dimensions = style.dimensions
  Box(
      Modifier.background(container, style.chipShape)
          .then(border?.let { Modifier.border(it, style.chipShape) } ?: Modifier)
          .padding(
              horizontal = dimensions.chipHorizontalPadding,
              vertical = dimensions.chipVerticalPadding,
          )
  ) {
    CardLine(text, tag, style.typography.chip, content)
  }
}

/**
 * How far [meters] is, in the app's language. The distance is from where the explorer was located
 * when the map opened, not where they are now.
 */
@Composable
private fun distanceText(meters: Double): String {
  val distance = formatDistance(meters, currentLocale())
  val unit =
      when (distance.unit) {
        DistanceUnit.METERS -> R.string.map_card_distance_meters
        DistanceUnit.KILOMETERS -> R.string.map_card_distance_kilometers
      }
  return stringResource(unit, distance.amount)
}

/** The app's language, which decides the distance's decimal separator; recomposes if it changes. */
@Composable private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]
