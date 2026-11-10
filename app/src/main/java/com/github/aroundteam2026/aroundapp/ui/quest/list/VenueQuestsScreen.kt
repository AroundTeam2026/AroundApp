// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.list

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles

/**
 * The venue's Quests tab: its quests under a "My quests" heading, newest first, each with its
 * status. The heading's + button calls [onCreateQuest]; while the venue has no quests, the empty
 * state's button does instead. A quest's pencil calls [onEditQuest] with its id; its box archives
 * it, once confirmed, or unarchives an archived one.
 */
@Composable
fun VenueQuestsScreen(
    viewModel: VenueQuestsViewModel,
    onCreateQuest: () -> Unit,
    modifier: Modifier = Modifier,
    onEditQuest: (String) -> Unit = {},
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  VenueQuestsContent(
      state,
      onCreateQuest,
      viewModel::retry,
      modifier,
      onEditQuest,
      viewModel::setArchived,
  )
}

/**
 * Stateless Quests tab showing [state]; [onRetry] listens to the quests again after a failure, and
 * [onSetArchived] archives a quest (true) or makes it active again (false).
 */
@Composable
internal fun VenueQuestsContent(
    state: VenueQuestsUiState,
    onCreateQuest: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onEditQuest: (String) -> Unit = {},
    onSetArchived: (Quest, Boolean) -> Unit = { _, _ -> },
) {
  val empty = state is VenueQuestsUiState.Loaded && state.quests.isEmpty()
  Column(modifier.fillMaxSize().padding(AroundSpacing.ScreenHorizontal)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(
          stringResource(R.string.venue_quests_title),
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier.weight(1f),
      )
      // The empty state's own button replaces it
      if (!empty) {
        FilledIconButton(
            onClick = onCreateQuest,
            modifier = Modifier.testTag(C.Tag.VENUE_NEW_QUEST),
        ) {
          Icon(
              painterResource(R.drawable.ic_plus),
              contentDescription = stringResource(R.string.create_quest_heading),
          )
        }
      }
    }
    when (state) {
      VenueQuestsUiState.Loading ->
          Centered { CircularProgressIndicator(Modifier.testTag(C.Tag.VENUE_QUESTS_LOADING)) }
      VenueQuestsUiState.Failed -> Centered { LoadFailed(onRetry) }
      is VenueQuestsUiState.Loaded ->
          if (empty) Centered { NoQuests(onCreateQuest) }
          else QuestList(state.quests, onEditQuest, onSetArchived)
    }
  }
}

/**
 * The quests as cards, below the heading; a card's pencil calls [onEditQuest] with its id, and its
 * box [onSetArchived] with the quest and whether to archive it.
 */
@Composable
private fun QuestList(
    quests: List<Quest>,
    onEditQuest: (String) -> Unit,
    onSetArchived: (Quest, Boolean) -> Unit,
) {
  LazyColumn(
      Modifier.fillMaxSize().testTag(C.Tag.VENUE_QUESTS_LIST),
      contentPadding = PaddingValues(vertical = AroundSpacing.ExtraLarge),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
  ) {
    items(quests, key = Quest::id) { quest ->
      QuestCard(quest, { onEditQuest(quest.id) }, { onSetArchived(quest, it) })
    }
  }
}

/**
 * A quest's title, status, a pencil that calls [onEdit] and a box that calls [onSetArchived]: true
 * after the venue confirms archiving, false at once for an archived quest. A quest that isn't
 * active is muted.
 */
@Composable
private fun QuestCard(quest: Quest, onEdit: () -> Unit, onSetArchived: (Boolean) -> Unit) {
  val active = quest.status == QuestStatus.ACTIVE
  val archived = quest.status == QuestStatus.ARCHIVED
  var asking by rememberSaveable { mutableStateOf(false) }
  val colors = MaterialTheme.colorScheme
  val shape = MaterialTheme.shapes.large
  Row(
      Modifier.fillMaxWidth()
          .background(colors.surface, shape)
          .border(BorderStroke(CARD_BORDER, colors.outline), shape)
          .padding(AroundSpacing.CardPadding),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
  ) {
    Text(
        quest.title,
        style = AroundTextStyles.CardTitle,
        color = if (active) colors.onSurface else colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).testTag(C.Tag.VENUE_QUEST_TITLE),
    )
    StatusPill(
        statusLabel(quest.status),
        background = if (active) colors.tertiary else colors.outline,
        content = if (active) colors.onTertiary else colors.onSurfaceVariant,
    )
    CardButton(
        R.drawable.ic_edit,
        stringResource(R.string.venue_quests_edit, quest.title),
        C.Tag.VENUE_QUEST_EDIT,
        onEdit,
    )
    // Only archiving asks first, since it hides the quest from explorers
    CardButton(
        R.drawable.ic_archive,
        stringResource(
            if (archived) R.string.venue_quests_unarchive else R.string.venue_quests_archive,
            quest.title,
        ),
        C.Tag.VENUE_QUEST_ARCHIVE,
    ) {
      if (archived) onSetArchived(false) else asking = true
    }
  }
  if (asking) ArchiveDialog(quest.title, { asking = false }) { onSetArchived(true) }
}

/** Asks before archiving the quest [title]: [onArchive] once confirmed; both buttons [onClose]. */
@Composable
private fun ArchiveDialog(title: String, onClose: () -> Unit, onArchive: () -> Unit) {
  AlertDialog(
      onDismissRequest = onClose,
      confirmButton = {
        TextButton(
            onClick = {
              onClose()
              onArchive()
            },
            colors =
                ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.testTag(C.Tag.VENUE_QUEST_ARCHIVE_CONFIRM),
        ) {
          Text(stringResource(R.string.venue_quests_archive_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = onClose, Modifier.testTag(C.Tag.VENUE_QUEST_ARCHIVE_KEEP)) {
          Text(stringResource(R.string.venue_quests_archive_keep))
        }
      },
      title = { Text(stringResource(R.string.venue_quests_archive_title)) },
      text = { Text(stringResource(R.string.venue_quests_archive_text, title)) },
  )
}

/** One of Figma's round card buttons, showing [icon]; TalkBack reads [description]. */
@Composable
private fun CardButton(
    @DrawableRes icon: Int,
    description: String,
    tag: String,
    onClick: () -> Unit,
) {
  FilledIconButton(
      onClick = onClick,
      shape = AroundPillShape,
      colors =
          IconButtonDefaults.filledIconButtonColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onSurface,
          ),
      modifier = Modifier.size(CARD_BUTTON_SIZE).testTag(tag),
  ) {
    Icon(
        painterResource(icon),
        contentDescription = description,
        modifier = Modifier.size(CARD_ICON_SIZE),
    )
  }
}

/** A quest's status as a small pill. */
@Composable
private fun StatusPill(@StringRes label: Int, background: Color, content: Color) {
  Text(
      stringResource(label),
      style = AroundTextStyles.Badge,
      color = content,
      modifier =
          Modifier.background(background, AroundPillShape)
              .padding(horizontal = AroundSpacing.CompactGap, vertical = AroundSpacing.ExtraSmall)
              .testTag(C.Tag.VENUE_QUEST_STATUS),
  )
}

@StringRes
private fun statusLabel(status: QuestStatus): Int =
    when (status) {
      QuestStatus.DRAFT -> R.string.venue_quests_status_draft
      QuestStatus.ACTIVE -> R.string.venue_quests_status_active
      QuestStatus.ARCHIVED -> R.string.venue_quests_status_archived
    }

/** The venue has no quests yet: an illustration, why to create one, and a button to. */
@Composable
private fun NoQuests(onCreateQuest: () -> Unit) {
  Column(
      Modifier.width(EMPTY_STATE_WIDTH).testTag(C.Tag.VENUE_QUESTS_EMPTY),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
  ) {
    // Decorative: the title below says it all
    Image(painterResource(R.drawable.ill_no_quests), contentDescription = null)
    Spacer(Modifier.height(AroundSpacing.ExtraSmall))
    Text(
        stringResource(R.string.venue_quests_empty_title),
        style = AroundTextStyles.SheetTitle,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
    Message(R.string.venue_quests_empty_body)
    Spacer(Modifier.height(AroundSpacing.ExtraSmall))
    Button(
        onClick = onCreateQuest,
        shape = MaterialTheme.shapes.medium,
        modifier =
            Modifier.fillMaxWidth().height(BUTTON_HEIGHT).testTag(C.Tag.VENUE_QUESTS_CREATE_FIRST),
    ) {
      Text(stringResource(R.string.venue_quests_create_first), style = AroundTextStyles.Button)
    }
  }
}

/** Why the quests couldn't be loaded, and a button to try again. */
@Composable
private fun LoadFailed(onRetry: () -> Unit) {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.Large),
  ) {
    Message(R.string.venue_quests_failed, Modifier.testTag(C.Tag.VENUE_QUESTS_FAILED))
    Button(onRetry, Modifier.testTag(C.Tag.VENUE_QUESTS_RETRY)) {
      Text(stringResource(R.string.venue_quests_retry), style = AroundTextStyles.Button)
    }
  }
}

/** A centred line of muted text. */
@Composable
private fun Message(@StringRes text: Int, modifier: Modifier = Modifier) {
  Text(
      stringResource(text),
      style = AroundTextStyles.Description,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = modifier,
  )
}

/** Fills the rest of the tab and centres [content] in it. */
@Composable
private fun ColumnScope.Centered(content: @Composable () -> Unit) {
  Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { content() }
}

private val CARD_BORDER = 1.dp
private val CARD_BUTTON_SIZE = 28.dp
private val CARD_ICON_SIZE = 16.dp
private val BUTTON_HEIGHT = 52.dp
/** The width of the empty state's text and button in Figma. */
private val EMPTY_STATE_WIDTH = 260.dp
