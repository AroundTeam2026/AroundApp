// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.VenuePin
import com.github.aroundteam2026.aroundapp.ui.quest.QuestAvatar
import com.github.aroundteam2026.aroundapp.ui.quest.QuestChips
import com.github.aroundteam2026.aroundapp.ui.quest.QuestLine

/**
 * A venue's open marker: a card with the venue's avatar on the left, then its featured quest's
 * title, the venue's name, and chips for the quest's reward, the party size it needs and how far
 * the venue is, when they apply. Last comes how many more quests the venue has. A pointer below,
 * whose tip is the bottom centre, marks the venue.
 *
 * The card is always [MarkerDimensions.cardWidth] wide: long text is cut off, and chips that don't
 * fit move to the next line, rather than widening it.
 */
@Composable
fun QuestCard(
    pin: VenuePin,
    modifier: Modifier = Modifier,
    style: MarkerStyle = MarkerDefaults.style(),
) {
  val dimensions = style.dimensions
  val colors = style.colors
  Column(
      modifier.testTag(C.Tag.QUEST_CARD).width(dimensions.cardWidth),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Row(
        Modifier.testTag(C.Tag.QUEST_CARD_BODY)
            .fillMaxWidth()
            .background(colors.surface, style.cardShape)
            .border(dimensions.outlineWidth, colors.outline, style.cardShape)
            .padding(dimensions.cardPadding)
    ) {
      QuestAvatar(pin.avatar, style.avatarStyle, Modifier.testTag(C.Tag.QUEST_CARD_AVATAR))
      Column(
          Modifier.padding(start = dimensions.cardGap).weight(1f),
          verticalArrangement = Arrangement.spacedBy(dimensions.cardLineSpacing),
      ) {
        CardText(pin, style)
      }
    }
    Box(
        Modifier.testTag(C.Tag.QUEST_CARD_POINTER)
            .size(dimensions.pointerWidth, dimensions.pointerHeight)
            .background(colors.surface, style.pointerShape)
    )
  }
}

/** The quest's title, the venue's name, the chips, and how many more quests the venue has. */
@Composable
private fun CardText(pin: VenuePin, style: MarkerStyle) {
  val quest = pin.featuredQuest
  val colors = style.colors
  val typography = style.typography
  QuestLine(quest.title, C.Tag.QUEST_CARD_TITLE, typography.questTitle, colors.onSurface, 2)
  QuestLine(pin.venueName, C.Tag.QUEST_CARD_VENUE, typography.venueName, colors.onSurfaceVariant)
  QuestChips(quest.reward, quest.minPartySize, pin.distanceMeters, style.chipStyle)
  if (pin.otherQuestCount > 0) {
    QuestLine(
        pluralStringResource(
            R.plurals.map_card_more_quests,
            pin.otherQuestCount,
            pin.otherQuestCount,
        ),
        C.Tag.QUEST_CARD_MORE,
        typography.moreQuests,
        colors.onSurfaceVariant,
    )
  }
}
