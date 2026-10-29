// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.QuestAvatar
import com.github.aroundteam2026.aroundapp.ui.quest.QuestAvatarStyle
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles

/**
 * Draws one of the venue's quests. The page calls it for each listed quest, in order.
 *
 * @param quest The quest to draw.
 * @param selected Whether the explorer picked it to accept.
 * @param onClick Picks the quest, or unpicks it if it was picked.
 * @param onDetails Opens the quest's details in a [QuestDetailSheet].
 */
typealias VenuePageQuestRow =
    @Composable
    (quest: VenuePageQuest, selected: Boolean, onClick: () -> Unit, onDetails: () -> Unit) -> Unit

/**
 * A venue's page, showing what [viewModel] holds. It sits inside the app's scaffold, which already
 * keeps it clear of the system bars.
 *
 * @param viewModel Loads the venue and its quests, and keeps the picked one.
 * @param onBack Called when the explorer taps the back arrow.
 * @param questRow Draws each quest; a [VenueQuestRow] unless replaced.
 * @param bottomBar Sits under the list once the venue is loaded, given the picked quest or null; an
 *   [AcceptQuestBar] that accepts through [viewModel] unless replaced.
 */
@Composable
fun VenuePageScreen(
    viewModel: VenuePageViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    questRow: VenuePageQuestRow = DefaultQuestRow,
    bottomBar: (@Composable (selectedQuest: VenuePageQuest?) -> Unit)? = null,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val acceptBar: @Composable (VenuePageQuest?) -> Unit = {
    AcceptQuestBar(state, viewModel::accept)
  }
  VenuePageContent(
      state = state,
      onBack = onBack,
      onQuestTapped = viewModel::onQuestSelected,
      onRetry = viewModel::onRetry,
      onQuestDetails = viewModel::onQuestDetailsOpened,
      onQuestDetailsClosed = viewModel::onQuestDetailsClosed,
      onAccept = viewModel::accept,
      modifier = modifier,
      questRow = questRow,
      bottomBar = bottomBar ?: acceptBar,
  )
}

/**
 * Stateless page: the top bar, then what [state] says the page can show, with the details of
 * [VenuePageUiState.detailQuest] in a sheet on top. Under the list sits [bottomBar], or by default
 * an [AcceptQuestBar] whose button calls [onAccept].
 */
@Composable
fun VenuePageContent(
    state: VenuePageUiState,
    onBack: () -> Unit,
    onQuestTapped: (questId: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onQuestDetails: (questId: String) -> Unit = {},
    onQuestDetailsClosed: () -> Unit = {},
    onAccept: () -> Unit = {},
    questRow: VenuePageQuestRow = DefaultQuestRow,
    bottomBar: @Composable (selectedQuest: VenuePageQuest?) -> Unit = {
      AcceptQuestBar(state, onAccept)
    },
) {
  Column(
      modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .testTag(C.Tag.VENUE_PAGE_SCREEN)
  ) {
    TopBar(onBack)
    when (state.status) {
      VenuePageStatus.LOADING ->
          Centered { CircularProgressIndicator(Modifier.testTag(C.Tag.VENUE_PAGE_LOADING)) }
      VenuePageStatus.NOT_FOUND ->
          Centered {
            Message(R.string.venue_page_not_found, Modifier.testTag(C.Tag.VENUE_PAGE_NOT_FOUND))
          }
      VenuePageStatus.FAILED -> Centered { Failed(onRetry) }
      VenuePageStatus.LOADED -> {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().testTag(C.Tag.VENUE_PAGE_QUESTS),
            contentPadding = PaddingValues(AroundSpacing.ScreenHorizontal),
            verticalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
        ) {
          item { Header(state) }
          item {
            if (state.quests.isEmpty()) {
              Message(
                  R.string.venue_page_no_quests,
                  Modifier.fillMaxWidth()
                      .padding(top = AroundSpacing.ExtraLarge)
                      .testTag(C.Tag.VENUE_PAGE_EMPTY),
              )
            } else {
              Quests(state, questRow, onQuestTapped, onQuestDetails)
            }
          }
        }
        bottomBar(state.selectedQuest)
        state.detailQuest?.let { quest ->
          key(quest.questId) { QuestDetailSheet(quest, onQuestDetailsClosed) }
        }
      }
    }
  }
}

/** The round back button, on the start side. */
@Composable
private fun TopBar(onBack: () -> Unit) {
  Box(
      Modifier.fillMaxWidth()
          .padding(horizontal = AroundSpacing.ScreenHorizontal, vertical = AroundSpacing.Medium)
  ) {
    Surface(
        onClick = onBack,
        shape = AroundPillShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.size(BACK_SIZE).testTag(C.Tag.VENUE_PAGE_BACK),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
            painterResource(R.drawable.ic_chevron_left),
            stringResource(R.string.back),
            tint = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}

/** The card listing the venue's quests under a header that counts them, a line between each. */
@Composable
private fun Quests(
    state: VenuePageUiState,
    questRow: VenuePageQuestRow,
    onQuestTapped: (questId: String) -> Unit,
    onQuestDetails: (questId: String) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Column(
      Modifier.fillMaxWidth()
          .clip(QUESTS_SHAPE)
          .background(colors.surface)
          .padding(
              start = AroundSpacing.Large,
              end = AroundSpacing.Large,
              top = AroundSpacing.Large,
              bottom = AroundSpacing.ExtraSmall,
          )
          .testTag(C.Tag.VENUE_PAGE_QUESTS_CARD),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.ExtraSmall),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
          stringResource(R.string.venue_page_quests),
          Modifier.weight(1f).semantics { heading() },
          color = colors.onSurface,
          style = AroundTextStyles.OverlayTitle,
      )
      Text(
          pluralStringResource(
              R.plurals.venue_page_active_quests,
              state.quests.size,
              state.quests.size,
          ),
          Modifier.testTag(C.Tag.VENUE_PAGE_QUEST_COUNT),
          color = colors.onSurfaceVariant,
          style = AroundTextStyles.Caption,
      )
    }
    Column(Modifier.selectableGroup()) {
      state.quests.forEachIndexed { index, quest ->
        key(quest.questId) {
          if (index > 0) HorizontalDivider(thickness = DIVIDER_WIDTH, color = colors.outline)
          questRow(
              quest,
              quest.questId == state.selectedQuestId,
              { onQuestTapped(quest.questId) },
              { onQuestDetails(quest.questId) },
          )
        }
      }
    }
  }
}

/** The venue's avatar, name and, when it gave one, address. */
@Composable
private fun Header(state: VenuePageUiState) {
  Row(
      Modifier.fillMaxWidth()
          .padding(bottom = AroundSpacing.Small)
          .testTag(C.Tag.VENUE_PAGE_HEADER),
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Large),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    state.avatar?.let {
      QuestAvatar(it, venueAvatarStyle(), Modifier.testTag(C.Tag.VENUE_PAGE_AVATAR))
    }
    Column(verticalArrangement = Arrangement.spacedBy(AroundSpacing.ExtraSmall)) {
      Text(
          state.venueName,
          Modifier.semantics { heading() }.testTag(C.Tag.VENUE_PAGE_NAME),
          color = MaterialTheme.colorScheme.onBackground,
          style = AroundTextStyles.SheetTitle,
      )
      state.address?.let { address ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(AroundSpacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
              painterResource(R.drawable.ic_address_pin),
              contentDescription = null,
              Modifier.size(ADDRESS_ICON_SIZE),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
              address,
              Modifier.testTag(C.Tag.VENUE_PAGE_ADDRESS),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = AroundTextStyles.InlineBody,
          )
        }
      }
    }
  }
}

/**
 * A venue's avatar as its page's header shows it, on the same colours as the map's cards. The
 * overview's rows use it too, at their own size.
 */
@Composable
internal fun venueAvatarStyle(): QuestAvatarStyle {
  val colors = MaterialTheme.colorScheme
  return QuestAvatarStyle(
      size = AVATAR_SIZE,
      padding = AroundSpacing.ExtraSmall,
      background = colors.tertiary,
      content = colors.onTertiary,
      shape = AroundPillShape,
      textStyle = AroundTextStyles.AvatarLarge,
  )
}

/** Why the page couldn't load, and a button to try again. */
@Composable
private fun Failed(onRetry: () -> Unit) {
  Column(
      Modifier.testTag(C.Tag.VENUE_PAGE_FAILED),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.Large),
  ) {
    Message(R.string.venue_page_failed)
    Button(onRetry, Modifier.testTag(C.Tag.VENUE_PAGE_RETRY)) {
      Text(stringResource(R.string.venue_page_retry), style = AroundTextStyles.Button)
    }
  }
}

/** Fills the rest of the page and centres [content] in it. */
@Composable
private fun Centered(content: @Composable () -> Unit) {
  Box(
      Modifier.fillMaxSize().padding(horizontal = AroundSpacing.ScreenHorizontal),
      contentAlignment = Alignment.Center,
  ) {
    content()
  }
}

/** A short centred message about the page. */
@Composable
private fun Message(text: Int, modifier: Modifier = Modifier) {
  Text(
      stringResource(text),
      modifier,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = AroundTextStyles.Description,
      textAlign = TextAlign.Center,
  )
}

/** Each quest as a [VenueQuestRow]. */
internal val DefaultQuestRow: VenuePageQuestRow = { quest, selected, onClick, onDetails ->
  VenueQuestRow(quest, selected, onClick, onDetails = onDetails)
}

private val BACK_SIZE = 44.dp
private val AVATAR_SIZE = 64.dp
private val ADDRESS_ICON_SIZE = 16.dp
private val QUESTS_SHAPE = RoundedCornerShape(22.dp)
private val DIVIDER_WIDTH = 1.dp

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VenuePagePreview() {
  val quests =
      listOf(
              "Find the hidden fox" to Reward.FreeItem("croissant", specifics = null),
              "Latte art moment" to Reward.Discount(15.0, DiscountUnit.PERCENT, specifics = null),
          )
          .mapIndexed { i, (title, reward) ->
            VenuePageQuest(
                questId = "quest-$i",
                title = title,
                description = "Snap a photo of it and show it at the counter.",
                requirements = "",
                proofType = ProofType.PHOTO,
                minPartySize = 1,
                reward = reward,
            )
          }
  AroundAppTheme {
    VenuePageContent(
        state =
            VenuePageUiState(
                status = VenuePageStatus.LOADED,
                venueName = "Café Lumière",
                avatar = VenueAvatar.Initials("CL"),
                address = "Rue de Bourg 12, Lausanne",
                quests = quests,
                selectedQuestId = "quest-0",
            ),
        onBack = {},
        onQuestTapped = {},
        onRetry = {},
    )
  }
}
