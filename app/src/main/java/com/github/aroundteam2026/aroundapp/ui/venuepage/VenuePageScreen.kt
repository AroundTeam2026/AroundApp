// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
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
 */
typealias VenuePageQuestRow =
    @Composable (quest: VenuePageQuest, selected: Boolean, onClick: () -> Unit) -> Unit

/**
 * A venue's page, showing what [viewModel] holds. It sits inside the app's scaffold, which already
 * keeps it clear of the system bars.
 *
 * @param viewModel Loads the venue and its quests, and keeps the picked one.
 * @param onBack Called when the explorer taps the back arrow.
 * @param questRow Draws each quest; a plain title until the quest rows land.
 * @param bottomBar Sits under the list once the venue is loaded, given the picked quest or null.
 */
@Composable
fun VenuePageScreen(
    viewModel: VenuePageViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    questRow: VenuePageQuestRow = PlaceholderQuestRow,
    bottomBar: @Composable (selectedQuest: VenuePageQuest?) -> Unit = {},
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  VenuePageContent(
      state = state,
      onBack = onBack,
      onQuestTapped = viewModel::onQuestSelected,
      onRetry = viewModel::onRetry,
      modifier = modifier,
      questRow = questRow,
      bottomBar = bottomBar,
  )
}

/** Stateless page: the top bar, then what [state] says the page can show. */
@Composable
fun VenuePageContent(
    state: VenuePageUiState,
    onBack: () -> Unit,
    onQuestTapped: (questId: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    questRow: VenuePageQuestRow = PlaceholderQuestRow,
    bottomBar: @Composable (selectedQuest: VenuePageQuest?) -> Unit = {},
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
          if (state.quests.isEmpty()) {
            item {
              Message(
                  R.string.venue_page_no_quests,
                  Modifier.fillMaxWidth()
                      .padding(top = AroundSpacing.ExtraLarge)
                      .testTag(C.Tag.VENUE_PAGE_EMPTY),
              )
            }
          }
          items(state.quests, key = { it.questId }) { quest ->
            questRow(quest, quest.questId == state.selectedQuestId) { onQuestTapped(quest.questId) }
          }
        }
        bottomBar(state.selectedQuest)
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
    state.avatar?.let { QuestAvatar(it, avatarStyle(), Modifier.testTag(C.Tag.VENUE_PAGE_AVATAR)) }
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

/** The header's avatar, on the same colours as the map's cards. */
@Composable
private fun avatarStyle(): QuestAvatarStyle {
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

/** The quest's title, tappable, until the real quest rows replace it. */
internal val PlaceholderQuestRow: VenuePageQuestRow = { quest, selected, onClick ->
  Text(
      quest.title,
      Modifier.fillMaxWidth()
          .clickable(onClick = onClick)
          .semantics { this.selected = selected }
          .padding(vertical = AroundSpacing.Small),
      color =
          if (selected) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.onBackground,
      style = AroundTextStyles.CardTitle,
  )
}

private val BACK_SIZE = 44.dp
private val AVATAR_SIZE = 64.dp
private val ADDRESS_ICON_SIZE = 16.dp

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VenuePagePreview() {
  val quests =
      listOf("Bring a friend", "Try our new latte").mapIndexed { i, title ->
        VenuePageQuest(
            questId = "quest-$i",
            title = title,
            description = "",
            requirements = "",
            proofType = ProofType.PHOTO,
            minPartySize = 1,
            reward = null,
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
