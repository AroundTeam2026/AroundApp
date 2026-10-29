// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.overview

import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.QuestAvatar
import com.github.aroundteam2026.aroundapp.ui.quest.QuestChips
import com.github.aroundteam2026.aroundapp.ui.quest.QuestLine
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar
import com.github.aroundteam2026.aroundapp.ui.quest.currentLocale
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.venuepage.venueAvatarStyle
import com.github.aroundteam2026.aroundapp.ui.venuepage.venueQuestChipStyle
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * One accepted quest in the overview, as the Figma card `Overview/Quest card` (after the active
 * quest card of F3): where the reservation stands and its slot, then the venue's avatar, the
 * quest's title, the venue's name and address, the party size and the reward chip. Tapping it opens
 * the venue's page.
 *
 * @param row The accepted quest to draw.
 * @param onClick Opens the venue's page.
 * @param clock Tells which day is today, and the time zone the slot is written in.
 */
@Composable
fun OverviewQuestRow(
    row: OverviewRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    clock: Clock = Clock.systemDefaultZone(),
) {
  val colors = MaterialTheme.colorScheme
  Column(
      modifier
          .fillMaxWidth()
          .shadow(CARD_ELEVATION, CARD_SHAPE, ambientColor = CARD_SHADOW, spotColor = CARD_SHADOW)
          .clip(CARD_SHAPE)
          .background(colors.surface)
          .clickable(
              onClickLabel = stringResource(R.string.overview_open_venue, row.venueName),
              role = Role.Button,
              onClick = onClick,
          )
          .padding(AroundSpacing.Large)
          .testTag("${C.Tag.OVERVIEW_ROW}_${row.reservationId}"),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
  ) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Status(row.status, Modifier.weight(1f))
      Slot(row.slotStart, clock)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      QuestAvatar(row.avatar, venueAvatarStyle().copy(size = AVATAR_SIZE))
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(INFO_SPACING)) {
        QuestLine(
            row.questTitle,
            C.Tag.OVERVIEW_ROW_TITLE,
            AroundTextStyles.CardHeading,
            colors.onSurface,
            maxLines = TITLE_MAX_LINES,
        )
        Detail(
            R.drawable.ic_address_pin,
            row.address?.let { stringResource(R.string.overview_venue_address, row.venueName, it) }
                ?: row.venueName,
            C.Tag.OVERVIEW_ROW_VENUE,
        )
        Detail(
            R.drawable.ic_user,
            pluralStringResource(R.plurals.overview_party_size, row.partySize, row.partySize),
            C.Tag.OVERVIEW_ROW_PARTY,
        )
        QuestChips(
            row.reward,
            // The party size has its own line, so the chips leave it out
            minPartySize = QuestLimits.MIN_PARTY_SIZE,
            distanceMeters = null,
            venueQuestChipStyle(),
            Modifier.padding(top = REWARD_TOP),
        )
      }
      Icon(
          painterResource(R.drawable.ic_chevron_right),
          // The card's click label already says where it goes
          contentDescription = null,
          tint = colors.onSurfaceVariant,
      )
    }
  }
}

/**
 * Where the reservation stands, in small capitals: a muted dot and "Waiting for the venue", or a
 * check on the tertiary colour and "Approved".
 */
@Composable
private fun Status(status: OverviewRowStatus, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  Row(
      modifier.testTag(C.Tag.OVERVIEW_ROW_STATUS),
      horizontalArrangement = Arrangement.spacedBy(STATUS_GAP),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    val (text, color) =
        when (status) {
          OverviewRowStatus.WAITING -> {
            Box(Modifier.size(DOT_SIZE).background(colors.onSurfaceVariant, AroundPillShape))
            R.string.overview_status_waiting to colors.onSurfaceVariant
          }
          OverviewRowStatus.APPROVED -> {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                Modifier.size(CHECK_SIZE),
                tint = colors.tertiary,
            )
            R.string.overview_status_approved to colors.onSurface
          }
        }
    Text(
        stringResource(text).uppercase(currentLocale()),
        color = color,
        style = AroundTextStyles.Label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
  }
}

/** The slot in a pill on the background colour: "Today · 15:30", or the day, e.g. "Sun 2 Nov". */
@Composable
private fun Slot(slotStart: Long, clock: Clock) {
  val colors = MaterialTheme.colorScheme
  val locale = currentLocale()
  val slot = Instant.ofEpochMilli(slotStart).atZone(clock.zone)
  val day =
      if (slot.toLocalDate() == LocalDate.now(clock)) stringResource(R.string.overview_slot_today)
      else
          DateTimeFormatter.ofPattern(
                  DateFormat.getBestDateTimePattern(locale, DAY_SKELETON),
                  locale,
              )
              .format(slot)
  val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
  val time =
      DateTimeFormatter.ofPattern(
              DateFormat.getBestDateTimePattern(locale, if (is24Hour) HOURS_24 else HOURS_12),
              locale,
          )
          .format(slot)
  Row(
      Modifier.background(colors.background, AroundPillShape)
          .padding(horizontal = AroundSpacing.CompactGap, vertical = SLOT_VERTICAL_PADDING),
      horizontalArrangement = Arrangement.spacedBy(STATUS_GAP),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
        painterResource(R.drawable.ic_clock),
        contentDescription = null,
        Modifier.size(SLOT_ICON_SIZE),
        tint = colors.onSurface,
    )
    OneLine(
        stringResource(R.string.overview_slot, day, time),
        C.Tag.OVERVIEW_ROW_SLOT,
        AroundTextStyles.Badge,
        colors.onSurface,
    )
  }
}

/** A muted line of [text] after a small [icon], cut off with an ellipsis. */
@Composable
private fun Detail(@DrawableRes icon: Int, text: String, tag: String) {
  val color = MaterialTheme.colorScheme.onSurfaceVariant
  Row(
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.ExtraSmall),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(painterResource(icon), contentDescription = null, Modifier.size(DETAIL_ICON_SIZE), color)
    OneLine(text, tag, AroundTextStyles.Caption, color)
  }
}

@Composable
private fun OneLine(text: String, tag: String, style: TextStyle, color: Color) {
  Text(
      text,
      Modifier.testTag(tag),
      color = color,
      style = style,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
  )
}

/** The weekday, day and month, in the order of the device's language. */
private const val DAY_SKELETON = "EEEdMMM"
/** Hours and minutes on the 24-hour clock, or the 12-hour one with AM/PM, as the phone is set. */
private const val HOURS_24 = "Hm"
private const val HOURS_12 = "hm"
private const val TITLE_MAX_LINES = 2
private val CARD_SHAPE = RoundedCornerShape(28.dp)
private val CARD_ELEVATION = 10.dp
// Figma's card shadow: black at 14 %, the same in light and dark mode
private val CARD_SHADOW = Color(0x24000000)
private val AVATAR_SIZE = 52.dp
private val INFO_SPACING = 3.dp
private val REWARD_TOP = 3.dp
private val STATUS_GAP = 6.dp
private val DOT_SIZE = 8.dp
private val CHECK_SIZE = 12.dp
private val SLOT_VERTICAL_PADDING = 6.dp
private val SLOT_ICON_SIZE = 14.dp
private val DETAIL_ICON_SIZE = 13.dp

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OverviewQuestRowPreview() {
  AroundAppTheme {
    Box(
        Modifier.background(MaterialTheme.colorScheme.background).padding(AroundSpacing.ExtraLarge)
    ) {
      OverviewQuestRow(
          OverviewRow(
              reservationId = "reservation-1",
              questId = "fox",
              venueId = "cafe",
              questTitle = "Find the hidden fox",
              reward = Reward.FreeItem("croissant", specifics = null),
              venueName = "Café Lumière",
              address = "Rue de Bourg 12",
              avatar = VenueAvatar.Initials("CL"),
              slotStart = System.currentTimeMillis(),
              partySize = 3,
              status = OverviewRowStatus.APPROVED,
          ),
          onClick = {},
      )
    }
  }
}
