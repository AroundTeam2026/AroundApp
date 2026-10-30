// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.dashboard

import android.content.res.Configuration
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.QuestAvatar
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar
import com.github.aroundteam2026.aroundapp.ui.quest.currentLocale
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme
import com.github.aroundteam2026.aroundapp.ui.venuepage.venueAvatarStyle
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The venue's Dashboard tab, as the Figma frame `Venue dashboard`: a header with the venue and
 * today's date, the pending and active quests counts, then the reservations that need an answer and
 * the approved ones. Approve and Decline answer a pending reservation.
 *
 * Figma's "Today" count is left out: reservations have no visit time yet (see
 * [VenueDashboardUiState]).
 */
@Composable
fun VenueDashboardScreen(viewModel: VenueDashboardViewModel, modifier: Modifier = Modifier) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  VenueDashboardContent(
      state,
      onApprove = viewModel::approve,
      onDecline = viewModel::decline,
      onRetry = viewModel::retry,
      modifier = modifier,
  )
}

/**
 * Stateless dashboard showing [state]. [onApprove] and [onDecline] take a reservation's id;
 * [onRetry] listens again after a failure. [clock] dates the header and the rows.
 */
@Composable
internal fun VenueDashboardContent(
    state: VenueDashboardUiState,
    onApprove: (String) -> Unit,
    onDecline: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    clock: Clock = Clock.systemDefaultZone(),
) {
  Box(
      modifier
          .fillMaxSize()
          .background(AroundTheme.colors.panel)
          .padding(
              if (state.status == VenueDashboardStatus.FAILED) AroundSpacing.ScreenHorizontal
              else 0.dp
          ),
      contentAlignment = Alignment.Center,
  ) {
    when (state.status) {
      VenueDashboardStatus.LOADING ->
          CircularProgressIndicator(Modifier.testTag(C.Tag.DASHBOARD_LOADING))
      VenueDashboardStatus.FAILED -> LoadFailed(onRetry)
      VenueDashboardStatus.LOADED -> Dashboard(state, onApprove, onDecline, clock)
    }
  }
}

/** The loaded dashboard as one scrolling list, so the sections scroll with the header. */
@Composable
private fun Dashboard(
    state: VenueDashboardUiState,
    onApprove: (String) -> Unit,
    onDecline: (String) -> Unit,
    clock: Clock,
) {
  // Many pending requests would push "Upcoming" far down, so only the first few show at first
  var showAllPending by rememberSaveable { mutableStateOf(false) }
  val pending =
      if (showAllPending) state.needsAnswer else state.needsAnswer.take(PENDING_SHOWN_FIRST)
  LazyColumn(
      Modifier.fillMaxSize().testTag(C.Tag.DASHBOARD_LIST),
      contentPadding = PaddingValues(AroundSpacing.ScreenHorizontal),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
  ) {
    item { Header(state.venueName, state.avatar, clock) }
    item { Summary(state, Modifier.padding(top = AroundSpacing.ExtraSmall)) }
    if (state.isEmpty) {
      item { NoReservations() }
    } else {
      section(R.string.dashboard_needs_answer, state.needsAnswer.size, C.Tag.DASHBOARD_NEEDS_ANSWER)
      reservations(pending, onApprove, onDecline, clock)
      if (pending.size < state.needsAnswer.size) {
        item {
          TextButton(
              onClick = { showAllPending = true },
              modifier = Modifier.fillMaxWidth().testTag(C.Tag.DASHBOARD_SHOW_ALL),
          ) {
            Text(
                stringResource(R.string.dashboard_show_all, state.needsAnswer.size),
                color = AroundTheme.colors.accentText,
                style = AroundTextStyles.Subtitle,
            )
          }
        }
      }
      section(R.string.dashboard_upcoming, state.upcoming.size, C.Tag.DASHBOARD_UPCOMING)
      reservations(state.upcoming, onApprove, onDecline, clock)
    }
  }
}

/** "Dashboard", the venue's name and today's date, and the venue's avatar. */
@Composable
private fun Header(venueName: String, avatar: VenueAvatar?, clock: Clock) {
  val colors = MaterialTheme.colorScheme
  val locale = currentLocale()
  val today =
      DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, DAY_SKELETON), locale)
          .format(LocalDate.now(clock))
  Row(
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TEXT_GAP)) {
      Text(
          stringResource(R.string.dashboard_title),
          color = colors.onBackground,
          style = AroundTextStyles.PageTitle,
      )
      Text(
          if (venueName.isEmpty()) today
          else stringResource(R.string.dashboard_venue_and_date, venueName, today),
          Modifier.testTag(C.Tag.DASHBOARD_VENUE),
          color = colors.onSurfaceVariant,
          style = AroundTextStyles.Caption,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
    if (avatar != null) {
      QuestAvatar(
          avatar,
          venueAvatarStyle().copy(size = HEADER_AVATAR_SIZE, textStyle = HEADER_AVATAR_STYLE),
      )
    }
  }
}

/** The counts, side by side; the pending one in the accent colour. */
@Composable
private fun Summary(state: VenueDashboardUiState, modifier: Modifier) {
  val ink = MaterialTheme.colorScheme.onSurface
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(AroundSpacing.CompactGap)) {
    Stat(
        state.pendingCount,
        R.string.dashboard_stat_pending,
        AroundTheme.colors.accentText,
        C.Tag.DASHBOARD_STAT_PENDING,
    )
    Stat(
        state.activeQuestsCount,
        R.string.dashboard_stat_active_quests,
        ink,
        C.Tag.DASHBOARD_STAT_ACTIVE_QUESTS,
    )
  }
}

/** One count, big, above what it counts. [tag] marks the count. */
@Composable
private fun RowScope.Stat(value: Int, @StringRes label: Int, color: Color, tag: String) {
  Column(
      Modifier.weight(1f)
          .background(MaterialTheme.colorScheme.surface, STAT_SHAPE)
          .padding(horizontal = AroundSpacing.InsetPadding, vertical = AroundSpacing.Medium),
      verticalArrangement = Arrangement.spacedBy(TEXT_GAP),
  ) {
    Text(value.toString(), Modifier.testTag(tag), color = color, style = STAT_STYLE)
    Text(
        stringResource(label),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = AroundTextStyles.Small,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
  }
}

/**
 * A section's heading, [title], with its [count] in a badge, a little apart from what is above.
 * Left out when the section is empty.
 */
private fun LazyListScope.section(@StringRes title: Int, count: Int, tag: String) {
  if (count == 0) return
  item(key = tag) {
    Row(
        Modifier.padding(top = AroundSpacing.Small).testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      val colors = MaterialTheme.colorScheme
      Text(stringResource(title), color = colors.onBackground, style = AroundTextStyles.CardHeading)
      Text(
          count.toString(),
          Modifier.background(colors.primary, AroundPillShape)
              .padding(horizontal = AroundSpacing.Small, vertical = BADGE_VERTICAL),
          color = colors.onPrimary,
          style = AroundTextStyles.Badge,
      )
    }
  }
}

private fun LazyListScope.reservations(
    reservations: List<DashboardReservation>,
    onApprove: (String) -> Unit,
    onDecline: (String) -> Unit,
    clock: Clock,
) {
  items(reservations, key = DashboardReservation::reservationId) {
    DashboardReservationRow(
        it,
        onDecline = { onDecline(it.reservationId) },
        onApprove = { onApprove(it.reservationId) },
        clock = clock,
    )
  }
}

/** No reservation to show yet: why, and where they will come from. */
@Composable
private fun NoReservations() {
  Column(
      Modifier.fillMaxWidth()
          .padding(top = AroundSpacing.HeadingGap)
          .testTag(C.Tag.DASHBOARD_EMPTY),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
  ) {
    Text(
        stringResource(R.string.dashboard_empty_title),
        color = MaterialTheme.colorScheme.onBackground,
        style = AroundTextStyles.SheetTitle,
        textAlign = TextAlign.Center,
    )
    Text(
        stringResource(R.string.dashboard_empty_body),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = AroundTextStyles.Description,
        textAlign = TextAlign.Center,
    )
  }
}

/** Why the dashboard couldn't be loaded, and a button to try again. */
@Composable
private fun LoadFailed(onRetry: () -> Unit) {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.Large),
  ) {
    Text(
        stringResource(R.string.dashboard_failed),
        Modifier.testTag(C.Tag.DASHBOARD_FAILED),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = AroundTextStyles.Description,
        textAlign = TextAlign.Center,
    )
    Button(onRetry, Modifier.testTag(C.Tag.DASHBOARD_RETRY)) {
      Text(stringResource(R.string.dashboard_retry), style = AroundTextStyles.Button)
    }
  }
}

/** How many pending reservations show before "Show all", as the Figma note on long lists asks. */
internal const val PENDING_SHOWN_FIRST = 3
/** The weekday, day and month, in the order of the device's language. */
private const val DAY_SKELETON = "EEEdMMM"
private val TEXT_GAP = 2.dp
private val STAT_SHAPE = RoundedCornerShape(18.dp)
// Figma's counts are 26 sp, between the app's page and sheet titles
private val STAT_STYLE = AroundTextStyles.PageTitle.copy(fontSize = 26.sp)
private val BADGE_VERTICAL = 2.dp
private val HEADER_AVATAR_SIZE = 48.dp
private val HEADER_AVATAR_STYLE = AroundTextStyles.AvatarLarge.copy(fontSize = 17.sp)

@Preview(name = "Light", heightDp = 917)
@Preview(name = "Dark", heightDp = 917, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VenueDashboardPreview() {
  val now = System.currentTimeMillis()
  fun reservation(id: String, title: String, name: String, party: Int, approved: Boolean) =
      DashboardReservation(
          reservationId = id,
          questTitle = title,
          explorerName = name,
          partySize = party,
          requestedAt = now - 12 * DateUtils.MINUTE_IN_MILLIS,
          approved = approved,
      )
  AroundAppTheme {
    VenueDashboardContent(
        VenueDashboardUiState(
            status = VenueDashboardStatus.LOADED,
            venueName = "Café Lumière",
            avatar = VenueAvatar.Initials("CL"),
            needsAnswer =
                listOf(
                    reservation("a", "Find the hidden fox", "Yusuf S.", 3, approved = false),
                    reservation("b", "Brunch for six", "Nora B.", 6, approved = false),
                ),
            upcoming =
                listOf(reservation("c", "Find the hidden fox", "Camille D.", 1, approved = true)),
            activeQuestsCount = 2,
        ),
        onApprove = {},
        onDecline = {},
        onRetry = {},
    )
  }
}

@Preview(name = "Empty, light")
@Preview(name = "Empty, dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VenueDashboardEmptyPreview() {
  AroundAppTheme {
    VenueDashboardContent(
        VenueDashboardUiState(status = VenueDashboardStatus.LOADED, venueName = "Café Lumière"),
        onApprove = {},
        onDecline = {},
        onRetry = {},
    )
  }
}
