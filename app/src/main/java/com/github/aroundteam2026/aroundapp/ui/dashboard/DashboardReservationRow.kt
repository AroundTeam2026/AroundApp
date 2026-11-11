// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.dashboard

import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
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
 * instead. While the reservation has an [DashboardReservation.approveBlock], Approve is disabled
 * and the reason is shown under the buttons; Decline stays enabled. Reservations have no visit time
 * yet, so no slot is shown.
 *
 * @param reservation The reservation to draw.
 * @param onDecline Declines it; only a pending reservation offers it.
 * @param onApprove Approves it; only a pending reservation offers it.
 * @param saving Whether its answer is being saved: both buttons are disabled meanwhile, so the
 *   venue can't answer twice.
 * @param clock Tells how long ago it was requested; read again each time the minute shown changes,
 *   so a dashboard left open keeps up.
 */
@Composable
internal fun DashboardReservationRow(
    reservation: DashboardReservation,
    onDecline: () -> Unit,
    onApprove: () -> Unit,
    modifier: Modifier = Modifier,
    saving: Boolean = false,
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
            enabled = !saving,
            onClick = onDecline,
        )
        RowButton(
            R.string.dashboard_approve,
            R.drawable.ic_check,
            background = colors.primary,
            content = colors.onPrimary,
            tag = C.Tag.DASHBOARD_ROW_APPROVE,
            enabled = !saving && reservation.approveBlock == null,
            onClick = onApprove,
        )
      }
      reservation.approveBlock?.let {
        Text(
            approveBlockText(it),
            Modifier.fillMaxWidth().testTag(C.Tag.DASHBOARD_ROW_APPROVE_BLOCKED),
            color = colors.onSurfaceVariant,
            style = AroundTextStyles.Caption,
        )
      }
    }
  }
}

/** Why Approve is disabled, e.g. "Needs 2 more explorers before you can approve". */
@Composable
private fun approveBlockText(block: ApproveBlock): String =
    when (block) {
      is ApproveBlock.TooFew ->
          pluralStringResource(R.plurals.dashboard_approve_needs_more, block.missing, block.missing)
      is ApproveBlock.TooMany ->
          pluralStringResource(R.plurals.dashboard_approve_needs_exactly, block.size, block.size)
      // ApproveBlock.QuestMissing, the only other kind
      else -> stringResource(R.string.dashboard_approve_quest_missing)
    }

/**
 * The [clock]'s time, read again when the minute since [requestedAt] changes, so "Requested … ago"
 * (or a proof's "sent … ago") moves on while the row stays on screen instead of keeping the time it
 * was first drawn at.
 */
@Composable
internal fun rememberNow(requestedAt: Long, clock: Clock): Long =
    produceState(clock.millis(), requestedAt, clock) {
          while (true) {
            val elapsed = (value - requestedAt).coerceAtLeast(0)
            delay(DateUtils.MINUTE_IN_MILLIS - elapsed % DateUtils.MINUTE_IN_MILLIS)
            value = clock.millis()
          }
        }
        .value

/**
 * How long before [now] [time] was, e.g. "12 min. ago" in the phone's language, or null under a
 * minute, which reads better as "just now" than "0 min. ago".
 */
internal fun timeAgo(time: Long, now: Long): String? =
    if (now - time < DateUtils.MINUTE_IN_MILLIS) null
    else
        DateUtils.getRelativeTimeSpanString(
                time,
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            )
            .toString()

/** "Requested 12 min. ago", or "Requested just now" under a minute. */
@Composable
private fun requestedText(requestedAt: Long, now: Long): String =
    timeAgo(requestedAt, now)?.let { stringResource(R.string.dashboard_requested, it) }
        ?: stringResource(R.string.dashboard_requested_just_now)

/**
 * The party: its first members' avatars, the explorer who reserved with a "Leader" tag when others
 * come along, then who else is in it and its size, e.g. "with Lea M., Max K. · 3 of 4 joined". An
 * approved reservation ends the line with its "Approved" pill.
 */
@Composable
private fun Party(reservation: DashboardReservation) {
  val colors = MaterialTheme.colorScheme
  Row(
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    val name = reservation.explorerName ?: stringResource(R.string.dashboard_unknown_explorer)
    Avatars(reservation)
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
        if (reservation.partySize > 1) LeaderTag()
      }
      Text(
          partyText(reservation),
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
 * Who else is in the party and its size: "with Lea M., Max K. +2 · 5 of 6 joined", naming at most
 * [NAMED_OTHERS] members after the leader and counting the rest, those without a name included. The
 * size is "6 joined · at least 4" for a quest that takes at least a group, "Party of 5" for one
 * that takes explorers alone too, or "Solo visit · Party of 1" for a lone explorer.
 */
@Composable
private fun partyText(reservation: DashboardReservation): String {
  val others = reservation.members.drop(1)
  val named = others.mapNotNull { it.name }.take(NAMED_OTHERS)
  val unnamed = others.size - named.size
  val with =
      if (others.isEmpty()) null
      else {
        val more =
            if (unnamed > 0) stringResource(R.string.dashboard_more_explorers, unnamed) else ""
        // "with +1" when none of the others can be named
        stringResource(R.string.dashboard_party_with, "${named.joinToString(", ")} $more".trim())
      }
  val group = reservation.groupSize
  val size =
      when {
        group != null ->
            pluralStringResource(
                if (reservation.exactGroupSize) R.plurals.dashboard_party_joined_of
                else R.plurals.dashboard_party_joined_at_least,
                reservation.partySize,
                reservation.partySize,
                group,
            )
        reservation.partySize == 1 -> stringResource(R.string.dashboard_party_solo)
        else ->
            pluralStringResource(
                R.plurals.dashboard_party_size,
                reservation.partySize,
                reservation.partySize,
            )
      }
  return with?.let { stringResource(R.string.dashboard_party_with_size, it, size) } ?: size
}

/**
 * The first [MAX_AVATARS] members, overlapping, the leader on top on the primary colour, then "+N"
 * for the rest. A party not read yet shows the leader's initials alone. The texts beside them name
 * the party, so screen readers skip the avatars.
 */
@Composable
private fun Avatars(reservation: DashboardReservation) {
  val colors = MaterialTheme.colorScheme
  val members = reservation.members.ifEmpty { listOf(DashboardMember(reservation.explorerName)) }
  val shown = members.take(MAX_AVATARS)
  Row(
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.SegmentGap),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box {
      shown.forEachIndexed { index, member ->
        val leader = index == 0
        Avatar(
            // The leader's name may only be stored with the reservation
            member.copy(name = if (leader) reservation.explorerName else member.name),
            background = if (leader) colors.primary else colors.tertiary,
            content = if (leader) colors.onPrimary else colors.onTertiary,
            modifier =
                Modifier.padding(start = (AVATAR_SIZE - AVATAR_OVERLAP) * index)
                    .zIndex((shown.size - index).toFloat()),
        )
      }
    }
    if (members.size > MAX_AVATARS) {
      Text(
          stringResource(R.string.dashboard_more_explorers, members.size - MAX_AVATARS),
          Modifier.testTag(C.Tag.DASHBOARD_ROW_MORE),
          color = colors.onSurfaceVariant,
          style = AroundTextStyles.Label,
          maxLines = 1,
      )
    }
  }
}

/** [member]'s picture, or else their initials on [background], ringed with the row's colour. */
@Composable
private fun Avatar(
    member: DashboardMember,
    background: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
  val picture = member.picture
  Box(
      modifier
          .size(AVATAR_SIZE)
          .border(AVATAR_RING, MaterialTheme.colorScheme.surface, AroundPillShape)
          .padding(AVATAR_RING)
          .clip(AroundPillShape)
          .background(background)
          .clearAndSetSemantics {
            testTag =
                if (picture != null) C.Tag.DASHBOARD_ROW_PICTURE else C.Tag.DASHBOARD_ROW_INITIALS
          },
      contentAlignment = Alignment.Center,
  ) {
    if (picture != null) {
      Image(
          picture,
          contentDescription = null,
          Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
      )
    } else {
      Text(
          member.name?.let(::initialsOf) ?: stringResource(R.string.dashboard_unknown_initials),
          color = content,
          style = AroundTextStyles.Label,
          maxLines = 1,
      )
    }
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

/**
 * One of the pending row's two buttons, sharing the row's width with the other. Disabled, it keeps
 * its colours, faded.
 */
@Composable
private fun RowScope.RowButton(
    @StringRes label: Int,
    @DrawableRes icon: Int,
    background: Color,
    content: Color,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
  Button(
      onClick = onClick,
      modifier = Modifier.weight(1f).height(BUTTON_HEIGHT).testTag(tag),
      enabled = enabled,
      shape = AroundPillShape,
      colors =
          ButtonDefaults.buttonColors(
              containerColor = background,
              contentColor = content,
              disabledContainerColor = background.copy(alpha = DISABLED_ALPHA),
              disabledContentColor = content.copy(alpha = DISABLED_ALPHA),
          ),
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
private const val MAX_AVATARS = 3
private const val NAMED_OTHERS = 2
// Material's opacity for disabled content
private const val DISABLED_ALPHA = 0.38f
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
internal fun DashboardReservationRowPreview() {
  val now = System.currentTimeMillis()
  AroundAppTheme {
    Column(
        Modifier.background(AroundTheme.colors.panel).padding(AroundSpacing.ExtraLarge),
        verticalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
    ) {
      DashboardReservationRow(
          DashboardReservation(
              reservationId = "too-small",
              questTitle = "Find the hidden fox",
              explorerName = "Yusuf S.",
              partySize = 3,
              requestedAt = now - 12 * DateUtils.MINUTE_IN_MILLIS,
              approved = false,
              approveBlock = ApproveBlock.TooFew(1),
              members = membersNamed("Yusuf S.", "Lea M.", "Max K."),
              groupSize = 4,
          ),
          onDecline = {},
          onApprove = {},
      )
      DashboardReservationRow(
          DashboardReservation(
              reservationId = "pending",
              questTitle = "Brunch for six",
              explorerName = "Nora B.",
              partySize = 6,
              requestedAt = now - 30 * DateUtils.MINUTE_IN_MILLIS,
              approved = false,
              members = membersNamed("Nora B.", "Elif T.", "Sam R.", "Ana P.", "Tom L.", "Ida K."),
              groupSize = 6,
              exactGroupSize = true,
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
              members = membersNamed("Camille D."),
          ),
          onDecline = {},
          onApprove = {},
      )
    }
  }
}

private fun membersNamed(vararg names: String) = names.map { DashboardMember(it) }
