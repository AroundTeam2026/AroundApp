// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.VenuePin

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
      CardAvatar(pin.avatar, style)
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

/** The venue's avatar on its round background, in the initials' text style. */
@Composable
private fun CardAvatar(avatar: VenueAvatar, style: MarkerStyle) {
  val dimensions = style.dimensions
  Box(
      Modifier.testTag(C.Tag.QUEST_CARD_AVATAR)
          .size(dimensions.cardAvatarSize)
          .background(style.colors.secondary, style.avatarShape)
          .padding(dimensions.cardAvatarPadding)
  ) {
    CompositionLocalProvider(LocalTextStyle provides style.typography.initials) {
      avatar.Content(tint = style.colors.onSecondary, modifier = Modifier.fillMaxSize())
    }
  }
}

/** The quest's title, the venue's name, the chips, and how many more quests the venue has. */
@Composable
private fun CardText(pin: VenuePin, style: MarkerStyle) {
  val quest = pin.featuredQuest
  val colors = style.colors
  val typography = style.typography
  CardLine(quest.title, C.Tag.QUEST_CARD_TITLE, typography.questTitle, colors.onSurface, 2)
  CardLine(pin.venueName, C.Tag.QUEST_CARD_VENUE, typography.venueName, colors.onSurfaceVariant)
  QuestChips(quest.reward, quest.minPartySize, pin.distanceMeters, style = style)
  if (pin.otherQuestCount > 0) {
    CardLine(
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

/** One line of the card, cut off with an ellipsis past [maxLines]. */
@Composable
internal fun CardLine(
    text: String,
    tag: String,
    style: TextStyle,
    color: Color,
    maxLines: Int = 1,
) {
  Text(
      text,
      Modifier.testTag(tag),
      color = color,
      style = style,
      maxLines = maxLines,
      overflow = TextOverflow.Ellipsis,
  )
}
