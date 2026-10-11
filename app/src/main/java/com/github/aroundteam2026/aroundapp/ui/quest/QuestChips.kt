// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
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
    style: QuestChipStyle,
    modifier: Modifier = Modifier,
) {
  val partySize = minPartySize.takeIf { it > 1 }
  if (reward == null && partySize == null && distanceMeters == null) return
  val spacing = Arrangement.spacedBy(style.spacing)
  FlowRow(
      modifier.testTag(C.Tag.QUEST_CHIPS),
      horizontalArrangement = spacing,
      verticalArrangement = spacing,
  ) {
    reward?.let { Chip(rewardText(it), C.Tag.QUEST_CHIP_REWARD, style.reward, style) }
    partySize?.let {
      Chip(
          stringResource(R.string.map_card_party_size, it),
          C.Tag.QUEST_CHIP_PARTY,
          style.party,
          style,
      )
    }
    distanceMeters?.let {
      Chip(
          distanceText(it),
          C.Tag.QUEST_CHIP_DISTANCE,
          style.distance,
          style,
      )
    }
  }
}

/** A one-line chip of [text] in [colors], ringed when they have an outline. */
@Composable
private fun Chip(text: String, tag: String, colors: ChipColors, style: QuestChipStyle) {
  Box(
      Modifier.background(colors.container, style.shape)
          .then(
              colors.outline?.let {
                Modifier.border(BorderStroke(style.outlineWidth, it), style.shape)
              } ?: Modifier
          )
          .padding(horizontal = style.horizontalPadding, vertical = style.verticalPadding)
  ) {
    QuestLine(text, tag, style.textStyle, colors.content)
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

/** What a [reward] chip says, e.g. "10% off", "CHF 5 off" or "Free coffee". */
@Composable
private fun rewardText(reward: Reward): String =
    when (reward) {
      is Reward.Discount -> {
        val amount = formatRewardAmount(reward.amount, reward.unit, currentLocale())
        when (reward.unit) {
          DiscountUnit.PERCENT -> stringResource(R.string.map_card_reward_percent_off, amount)
          DiscountUnit.CHF -> stringResource(R.string.map_card_reward_chf_off, amount)
        }
      }
      is Reward.FreeItem -> stringResource(R.string.map_card_reward_free_item, reward.name)
      is Reward.Other -> reward.description
    }
