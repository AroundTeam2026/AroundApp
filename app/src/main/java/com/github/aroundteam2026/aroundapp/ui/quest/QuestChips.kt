// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    reward?.let {
      val colors = if (it is Reward.FreeItem) style.freeItem else style.reward
      Chip(rewardText(it), C.Tag.QUEST_CHIP_REWARD, colors, style, style.rewardIcon)
    }
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

/** A one-line chip of [text] in [colors], after [icon] if any, ringed when they have an outline. */
@Composable
private fun Chip(
    text: String,
    tag: String,
    colors: ChipColors,
    style: QuestChipStyle,
    icon: ChipIcon? = null,
) {
  Row(
      Modifier.background(colors.container, style.shape)
          .then(
              colors.outline?.let {
                Modifier.border(BorderStroke(style.outlineWidth, it), style.shape)
              } ?: Modifier
          )
          .padding(
              start = icon?.startPadding ?: style.horizontalPadding,
              end = style.horizontalPadding,
              top = style.verticalPadding,
              bottom = style.verticalPadding,
          ),
      horizontalArrangement = Arrangement.spacedBy(icon?.gap ?: 0.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    icon?.let {
      Icon(
          painterResource(it.icon),
          contentDescription = null,
          Modifier.size(it.size),
          tint = colors.content,
      )
    }
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

/** The app's language, which decides how numbers are written; recomposes if it changes. */
@Composable internal fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

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
