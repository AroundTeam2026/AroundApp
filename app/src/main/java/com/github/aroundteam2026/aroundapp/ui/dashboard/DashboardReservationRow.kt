// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.dashboard

import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.initialsOf
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme
import java.time.Clock
import kotlinx.coroutines.delay

/**
 * One reservation on the venue dashboard, as the Figma component `Reservation row`: the quest's
 * title and when it was requested, then the party, led by the explorer who reserved.
 *
 * A pending reservation ends with Decline and Approve; an approved one shows an "Approved" pill
 * instead. Reservations have no visit time yet, so no slot is shown.
 *
 * @param reservation The reservation to draw.
 * @param onDecline Declines it; only a pending reservation offers it.
 * @param onApprove Approves it; only a pending reservation offers it.
 * @param clock Tells how long ago it was requested; read again each time the minute shown changes,
 *   so a dashboard left open keeps up.
 */
@Composable
internal fun DashboardReservationRow(
    reservation: DashboardReservation,
    onDecline: () -> Unit,
    onApprove: () -> Unit,
    modifier: Modifier = Modifier,
    clock: Clock = Clock.systemDefaultZone(),
) {
  val colors = MaterialTheme.colorScheme
  val now = rememberNow(reservation.requestedAt, clock)
  Column(
      modifier
          .fillMaxWidth()
          .clip(MaterialTheme.shapes.large)
          .background(colors.surface)
          .padding(AroundSpacing.Large)
          .testTag("${C.Tag.DASHBOARD_ROW}_${reservation.reservationId}"),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(TEXT_GAP)) {
      Text(
          reservation.questTitle ?: stringResource(R.string.dashboard_unknown_quest),
          Modifier.testTag(C.Tag.DASHBOARD_ROW_TITLE),
          color = colors.onSurface,
          style = AroundTextStyles.RowTitle,
          maxLines = TITLE_MAX_LINES,
          overflow = TextOverflow.Ellipsis,
      )
      Text(
          requestedText(reservation.requestedAt, now),
          Modifier.testTag(C.Tag.DASHBOARD_ROW_REQUESTED),
          color = colors.onSurfaceVariant,
          style = AroundTextStyles.Small,
          maxLines = 1,
      )
    }
    Party(reservation)
    if (!reservation.approved) {
      Row(horizontalArrangement = Arrangement.spacedBy(AroundSpacing.CompactGap)) {
        RowButton(
            R.string.dashboard_decline,
            R.drawable.ic_close,
            background = colors.background,
            content = colors.onSurface,
            tag = C.Tag.DASHBOARD_ROW_DECLINE,
            onClick = onDecline,
        )
        RowButton(
            R.string.dashboard_approve,
            R.drawable.ic_check,
            background = colors.primary,
            content = colors.onPrimary,
            tag = C.Tag.DASHBOARD_ROW_APPROVE,
            onClick = onApprove,
        )
      }
    }
  }
}

/**
 * The [clock]'s time, read again when the minute since [requestedAt] changes, so "Requested … ago"
 * moves on while the row stays on screen instead of keeping the time it was first drawn at.
 */
@Composable
private fun rememberNow(requestedAt: Long, clock: Clock): Long =
    produceState(clock.millis(), requestedAt, clock) {
          while (true) {
            val elapsed = (value - requestedAt).coerceAtLeast(0)
            delay(DateUtils.MINUTE_IN_MILLIS - elapsed % DateUtils.MINUTE_IN_MILLIS)
            value = clock.millis()
          }
        }
        .value

/**
 * "Requested 12 min. ago", in the phone's language; "Requested just now" under a minute, rather
 * than "0 min. ago".
 */
@Composable
private fun requestedText(requestedAt: Long, now: Long): String =
    if (now - requestedAt < DateUtils.MINUTE_IN_MILLIS) {
      stringResource(R.string.dashboard_requested_just_now)
    } else {
      stringResource(
          R.string.dashboard_requested,
          DateUtils.getRelativeTimeSpanString(
                  requestedAt,
                  now,
                  DateUtils.MINUTE_IN_MILLIS,
                  DateUtils.FORMAT_ABBREV_RELATIVE,
              )
              .toString(),
      )
    }

/**
 * The party: the avatars, the explorer who reserved with a "Leader" tag when others come along, and
 * the party's size. Only the leader's name is stored, so the others show as a "+N" avatar. An
 * approved reservation ends the line with its "Approved" pill.
 */
@Composable
private fun Party(reservation: DashboardReservation) {
  val colors = MaterialTheme.colorScheme
  val others = reservation.partySize - 1
  Row(
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    val name = reservation.explorerName ?: stringResource(R.string.dashboard_unknown_explorer)
    Avatars(reservation.explorerName, others)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TEXT_GAP)) {
      Row(
          horizontalArrangement = Arrangement.spacedBy(AroundSpacing.SegmentGap),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            name,
            Modifier.weight(1f, fill = false).testTag(C.Tag.DASHBOARD_ROW_EXPLORER),
            color = colors.onSurface,
            style = AroundTextStyles.FieldLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (others > 0) LeaderTag()
      }
      Text(
          if (others == 0) stringResource(R.string.dashboard_party_solo)
          else
              pluralStringResource(
                  R.plurals.dashboard_party_size,
                  reservation.partySize,
                  reservation.partySize,
              ),
          Modifier.testTag(C.Tag.DASHBOARD_ROW_PARTY),
          color = colors.onSurfaceVariant,
          style = AroundTextStyles.Caption,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
    if (reservation.approved) ApprovedPill()
  }
}

/**
 * The leader's initials on the primary colour, overlapped by a "+N" avatar for the [others] in the
 * party, both ringed with the row's colour. The texts beside them say the same, so screen readers
 * skip the avatars.
 */
@Composable
private fun Avatars(leaderName: String?, others: Int) {
  val colors = MaterialTheme.colorScheme
  Box(Modifier.clearAndSetSemantics {}) {
    Avatar(
        leaderName?.let(::initialsOf) ?: stringResource(R.string.dashboard_unknown_initials),
        background = colors.primary,
        content = colors.onPrimary,
        modifier = Modifier.zIndex(1f),
    )
    if (others > 0) {
      Avatar(
          stringResource(R.string.dashboard_more_explorers, others),
          background = colors.background,
          content = colors.onSurface,
          modifier = Modifier.padding(start = AVATAR_SIZE - AVATAR_OVERLAP),
      )
    }
  }
}

@Composable
private fun Avatar(text: String, background: Color, content: Color, modifier: Modifier = Modifier) {
  Box(
      modifier
          .size(AVATAR_SIZE)
          .border(AVATAR_RING, MaterialTheme.colorScheme.surface, AroundPillShape)
          .padding(AVATAR_RING)
          .background(background, AroundPillShape),
      contentAlignment = Alignment.Center,
  ) {
    Text(text, color = content, style = AroundTextStyles.Label, maxLines = 1)
  }
}

/** A crown and "Leader", in the accent colour, on the background colour. */
@Composable
private fun LeaderTag() {
  val accent = AroundTheme.colors.accentText
  Row(
      Modifier.background(MaterialTheme.colorScheme.background, AroundPillShape)
          .padding(
              start = LEADER_START,
              end = LEADER_END,
              top = LEADER_VERTICAL,
              bottom = LEADER_VERTICAL,
          )
          .testTag(C.Tag.DASHBOARD_ROW_LEADER),
      horizontalArrangement = Arrangement.spacedBy(LEADER_ICON_GAP),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
        painterResource(R.drawable.ic_crown),
        contentDescription = null,
        Modifier.width(CROWN_WIDTH).height(CROWN_HEIGHT),
        tint = accent,
    )
    Text(stringResource(R.string.dashboard_leader), color = accent, style = LEADER_STYLE)
  }
}

/** A check and "Approved", on the tertiary colour. */
@Composable
private fun ApprovedPill() {
  val colors = MaterialTheme.colorScheme
  Row(
      Modifier.background(colors.tertiary, AroundPillShape)
          .padding(start = PILL_START, end = PILL_END, top = PILL_VERTICAL, bottom = PILL_VERTICAL)
          .testTag(C.Tag.DASHBOARD_ROW_APPROVED),
      horizontalArrangement = Arrangement.spacedBy(PILL_ICON_GAP),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
        painterResource(R.drawable.ic_check),
        contentDescription = null,
        Modifier.size(PILL_ICON_SIZE),
        tint = colors.onTertiary,
    )
    Text(
        stringResource(R.string.dashboard_approved),
        color = colors.onTertiary,
        style = AroundTextStyles.Badge,
    )
  }
}

/** One of the pending row's two buttons, sharing the row's width with the other. */
@Composable
private fun RowScope.RowButton(
    @StringRes label: Int,
    @DrawableRes icon: Int,
    background: Color,
    content: Color,
    tag: String,
    onClick: () -> Unit,
) {
  Button(
      onClick = onClick,
      modifier = Modifier.weight(1f).height(BUTTON_HEIGHT).testTag(tag),
      shape = AroundPillShape,
      colors = ButtonDefaults.buttonColors(containerColor = background, contentColor = content),
      contentPadding = PaddingValues(horizontal = AroundSpacing.Small),
  ) {
    Icon(painterResource(icon), contentDescription = null, Modifier.size(BUTTON_ICON_SIZE))
    Text(
        stringResource(label),
        Modifier.padding(start = BUTTON_ICON_GAP),
        style = AroundTextStyles.Subtitle,
        maxLines = 1,
    )
  }
}

private const val TITLE_MAX_LINES = 2
private val TEXT_GAP = 2.dp
private val AVATAR_SIZE = 32.dp
private val AVATAR_RING = 2.5.dp
private val AVATAR_OVERLAP = 8.dp
private val LEADER_START = 6.dp
private val LEADER_END = 7.dp
private val LEADER_VERTICAL = 2.dp
private val LEADER_ICON_GAP = 3.dp
private val CROWN_WIDTH = 12.dp
private val CROWN_HEIGHT = 8.dp
// Figma's "Leader" tag is 10 sp, one step under the app's smallest label
private val LEADER_STYLE = AroundTextStyles.Label.copy(fontSize = 10.sp)
private val PILL_START = 9.dp
private val PILL_END = 11.dp
private val PILL_VERTICAL = 5.dp
private val PILL_ICON_GAP = 5.dp
private val PILL_ICON_SIZE = 10.dp
private val BUTTON_HEIGHT = 44.dp
private val BUTTON_ICON_SIZE = 12.dp
private val BUTTON_ICON_GAP = 6.dp

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DashboardReservationRowPreview() {
  val now = System.currentTimeMillis()
  AroundAppTheme {
    Column(
        Modifier.background(AroundTheme.colors.panel).padding(AroundSpacing.ExtraLarge),
        verticalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
    ) {
      DashboardReservationRow(
          DashboardReservation(
              reservationId = "pending",
              questTitle = "Find the hidden fox",
              explorerName = "Yusuf S.",
              partySize = 3,
              requestedAt = now - 12 * DateUtils.MINUTE_IN_MILLIS,
              approved = false,
          ),
          onDecline = {},
          onApprove = {},
      )
      DashboardReservationRow(
          DashboardReservation(
              reservationId = "approved",
              questTitle = "Find the hidden fox",
              explorerName = "Camille D.",
              partySize = 1,
              requestedAt = now - 3 * DateUtils.HOUR_IN_MILLIS,
              approved = true,
          ),
          onDecline = {},
          onApprove = {},
      )
    }
  }
}
