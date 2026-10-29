// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.overview

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.navigation.AroundHeader
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import java.time.Clock

/**
 * The overview of the quests the explorer [explorerUid] accepted, which the Quests tab shows, with
 * the app's repositories. It is the tab's own screen rather than a route, so the Quests tab stays
 * selected while it is shown.
 *
 * @param onOpenVenue Opens the page of the venue with this id, when its card is tapped.
 */
@Composable
fun OverviewScreen(explorerUid: String, onOpenVenue: (venueId: String) -> Unit) =
    OverviewScreen(viewModel(factory = OverviewViewModel.factory(explorerUid)), onOpenVenue)

/** The overview showing what [viewModel] holds; see the other [OverviewScreen]. */
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    onOpenVenue: (venueId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  OverviewContent(state, onOpenVenue, viewModel::retry, modifier)
}

/**
 * Stateless overview, as the Figma frame "Explorer quests · accepted": the [AroundHeader], the
 * title, then what [state] says it can show. It sits inside the app's scaffold, which already keeps
 * it clear of the system bars.
 *
 * @param onOpenVenue Opens the page of the venue with this id, when its card is tapped.
 * @param onRetry Called when the explorer taps "Try again" after a failure.
 * @param clock Tells which day is today, and the time zone the slots are written in.
 */
@Composable
fun OverviewContent(
    state: OverviewUiState,
    onOpenVenue: (venueId: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    clock: Clock = Clock.systemDefaultZone(),
) {
  Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    AroundHeader()
    Title(state)
    when (state.status) {
      OverviewStatus.LOADING ->
          Centered { CircularProgressIndicator(Modifier.testTag(C.Tag.OVERVIEW_LOADING)) }
      OverviewStatus.FAILED -> Centered { Failed(onRetry) }
      OverviewStatus.LOADED ->
          if (state.rows.isEmpty()) Centered { Empty() } else Rows(state.rows, onOpenVenue, clock)
    }
  }
}

/** "My quests", and how many there are once there are some. */
@Composable
private fun Title(state: OverviewUiState) {
  Column(
      Modifier.padding(
          start = AroundSpacing.ScreenHorizontal,
          end = AroundSpacing.ScreenHorizontal,
          top = AroundSpacing.ExtraLarge,
      ),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.ExtraSmall),
  ) {
    Text(
        stringResource(R.string.overview_title),
        Modifier.semantics { heading() }.testTag(C.Tag.OVERVIEW_TITLE),
        color = MaterialTheme.colorScheme.onBackground,
        style = AroundTextStyles.PageTitle,
    )
    if (state.status == OverviewStatus.LOADED && state.rows.isNotEmpty()) {
      Text(
          pluralStringResource(R.plurals.overview_count, state.rows.size, state.rows.size),
          Modifier.testTag(C.Tag.OVERVIEW_COUNT),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = AroundTextStyles.InlineBody,
      )
    }
  }
}

/** The accepted quests, one card each, in the order of [rows]. */
@Composable
private fun Rows(rows: List<OverviewRow>, onOpenVenue: (venueId: String) -> Unit, clock: Clock) {
  LazyColumn(
      Modifier.fillMaxSize().testTag(C.Tag.OVERVIEW_LIST),
      contentPadding =
          PaddingValues(
              start = AroundSpacing.ScreenHorizontal,
              end = AroundSpacing.ScreenHorizontal,
              top = AroundSpacing.Large,
              bottom = AroundSpacing.ExtraLarge,
          ),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.Large),
  ) {
    items(rows, key = { it.reservationId }) { row ->
      OverviewQuestRow(row, onClick = { onOpenVenue(row.venueId) }, clock = clock)
    }
  }
}

/** The quest flag on the tertiary colour, then why the list is empty, as in the Figma. */
@Composable
private fun Empty() {
  val colors = MaterialTheme.colorScheme
  Column(
      Modifier.testTag(C.Tag.OVERVIEW_EMPTY),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
  ) {
    Box(
        Modifier.size(ILLUSTRATION_SIZE).background(colors.tertiary, AroundPillShape),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          painterResource(R.drawable.ic_quests),
          contentDescription = null,
          Modifier.size(ILLUSTRATION_ICON_SIZE),
          tint = colors.onTertiary,
      )
    }
    Text(
        stringResource(R.string.overview_empty_title),
        Modifier.padding(top = AroundSpacing.ExtraSmall),
        color = colors.onBackground,
        style = AroundTextStyles.SheetTitle,
        textAlign = TextAlign.Center,
    )
    Message(R.string.overview_empty_body)
  }
}

/** Why the overview couldn't load, and a button to try again. */
@Composable
private fun Failed(onRetry: () -> Unit) {
  Column(
      Modifier.testTag(C.Tag.OVERVIEW_FAILED),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.Large),
  ) {
    Message(R.string.overview_failed)
    Button(onRetry, Modifier.testTag(C.Tag.OVERVIEW_RETRY)) {
      Text(stringResource(R.string.overview_retry), style = AroundTextStyles.Button)
    }
  }
}

/** Fills the rest of the screen and centres [content] in it. */
@Composable
private fun Centered(content: @Composable () -> Unit) {
  Box(
      Modifier.fillMaxSize().padding(horizontal = AroundSpacing.ScreenHorizontal),
      contentAlignment = Alignment.Center,
  ) {
    content()
  }
}

/** A short centred message about the overview. */
@Composable
private fun Message(text: Int) {
  Text(
      stringResource(text),
      Modifier.fillMaxWidth(),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = AroundTextStyles.Description,
      textAlign = TextAlign.Center,
  )
}

private val ILLUSTRATION_SIZE = 136.dp
private val ILLUSTRATION_ICON_SIZE = 48.dp

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OverviewPreview() {
  AroundAppTheme {
    OverviewContent(
        OverviewUiState(
            status = OverviewStatus.LOADED,
            rows =
                listOf(
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
                    )
                ),
        ),
        onOpenVenue = {},
        onRetry = {},
    )
  }
}
