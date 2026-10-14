// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.ChipColors
import com.github.aroundteam2026.aroundapp.ui.quest.ChipIcon
import com.github.aroundteam2026.aroundapp.ui.quest.QuestChipStyle
import com.github.aroundteam2026.aroundapp.ui.quest.QuestChips
import com.github.aroundteam2026.aroundapp.ui.quest.QuestLine
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles

/**
 * One of a venue's quests on its page, as in the Figma venue page: the quest's title, description,
 * and chips for its reward and the party size it needs, the same chips as the map's cards, with a
 * chevron on the end. Tapping it picks the quest, or unpicks it; a picked row sits on the page's
 * background, inset from the edges.
 *
 * @param quest The quest to draw.
 * @param selected Whether the explorer picked it to accept.
 * @param onClick Picks the quest, or unpicks it if it was picked.
 */
@Composable
fun VenueQuestRow(
    quest: VenuePageQuest,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  Row(
      modifier
          .fillMaxWidth()
          .testTag(C.Tag.VENUE_QUEST_ROW)
          .clip(PICKED_SHAPE)
          .background(if (selected) colors.background else Color.Transparent)
          .selectable(selected = selected, role = Role.Button, onClick = onClick)
          .padding(
              horizontal = if (selected) PICKED_INSET else 0.dp,
              vertical = AroundSpacing.Medium,
          ),
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
        Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(AroundSpacing.ExtraSmall),
    ) {
      QuestLine(
          quest.title,
          C.Tag.VENUE_QUEST_ROW_TITLE,
          AroundTextStyles.RowTitle,
          colors.onSurface,
          TITLE_MAX_LINES,
      )
      if (quest.description.isNotBlank()) {
        QuestLine(
            quest.description,
            C.Tag.VENUE_QUEST_ROW_DESCRIPTION,
            AroundTextStyles.RowDescription,
            colors.onSurfaceVariant,
            DESCRIPTION_MAX_LINES,
        )
      }
      QuestChips(
          quest.reward,
          quest.minPartySize,
          distanceMeters = null,
          chipStyle(),
          Modifier.padding(top = AroundSpacing.ExtraSmall),
      )
    }
    Icon(
        painterResource(R.drawable.ic_quest_row_chevron),
        contentDescription = null,
        tint = colors.onSurfaceVariant,
    )
  }
}

/**
 * The row's chips: a free item's reward on the tertiary colour, other rewards on the primary, both
 * after a gift; the party size ringed on the surface.
 */
@Composable
private fun chipStyle(): QuestChipStyle {
  val colors = MaterialTheme.colorScheme
  return QuestChipStyle(
      reward = ChipColors(colors.primary, colors.onPrimary),
      party = ChipColors(colors.surface, colors.onSurface, colors.outline),
      distance = ChipColors(colors.secondary, colors.onSecondary),
      textStyle = AroundTextStyles.Badge,
      shape = AroundPillShape,
      spacing = CHIP_SPACING,
      horizontalPadding = AroundSpacing.Medium,
      verticalPadding = CHIP_VERTICAL_PADDING,
      outlineWidth = OUTLINE_WIDTH,
      freeItem = ChipColors(colors.tertiary, colors.onTertiary),
      rewardIcon =
          ChipIcon(
              R.drawable.ic_gift,
              size = GIFT_SIZE,
              gap = CHIP_SPACING,
              startPadding = AroundSpacing.CompactGap,
          ),
  )
}

private const val TITLE_MAX_LINES = 2
private const val DESCRIPTION_MAX_LINES = 3
private val PICKED_SHAPE = RoundedCornerShape(14.dp)
private val PICKED_INSET = AroundSpacing.Small
private val OUTLINE_WIDTH = 1.dp
private val CHIP_SPACING = 6.dp
private val CHIP_VERTICAL_PADDING = 5.dp
private val GIFT_SIZE = 14.dp

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VenueQuestRowPreview() {
  val quest =
      VenuePageQuest(
          questId = "quest-0",
          title = "Find the hidden fox",
          description =
              "Spot the little ceramic fox hidden somewhere in the café and snap a photo.",
          requirements = "",
          proofType = ProofType.PHOTO,
          minPartySize = 1,
          reward = Reward.FreeItem("croissant", specifics = null),
      )
  AroundAppTheme {
    Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(AroundSpacing.Large)) {
      VenueQuestRow(quest, selected = true, onClick = {})
      VenueQuestRow(
          quest.copy(
              questId = "quest-1",
              title = "Latte art moment",
              minPartySize = 2,
              reward = Reward.Discount(15.0, DiscountUnit.PERCENT, specifics = null),
          ),
          selected = false,
          onClick = {},
      )
    }
  }
}
