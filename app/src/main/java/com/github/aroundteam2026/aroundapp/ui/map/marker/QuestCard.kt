// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.VenuePin
import java.util.Locale

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
  val partySize = quest.minPartySize.takeIf { it > 1 }
  if (quest.reward != null || partySize != null || pin.distanceMeters != null) {
    CardChips(pin, partySize, style)
  }
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

/** The reward, party size and distance chips, wrapping to more lines when they don't fit. */
@Composable
private fun CardChips(pin: VenuePin, partySize: Int?, style: MarkerStyle) {
  val colors = style.colors
  val spacing = Arrangement.spacedBy(style.dimensions.chipSpacing)
  FlowRow(horizontalArrangement = spacing, verticalArrangement = spacing) {
    pin.featuredQuest.reward?.let {
      Chip(it.description, C.Tag.QUEST_CARD_REWARD, colors.accent, colors.onAccent, style)
    }
    partySize?.let {
      Chip(
          stringResource(R.string.map_card_party_size, it),
          C.Tag.QUEST_CARD_PARTY,
          colors.surface,
          colors.onSurface,
          style,
          BorderStroke(style.dimensions.outlineWidth, colors.outline),
      )
    }
    pin.distanceMeters?.let {
      Chip(
          distanceText(it),
          C.Tag.QUEST_CARD_DISTANCE,
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

/** One line of the card, cut off with an ellipsis past [maxLines]. */
@Composable
private fun CardLine(
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
