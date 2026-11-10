// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.SaveState
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import kotlinx.coroutines.launch

/**
 * The form a venue fills in to create or edit a quest. [onClose] leaves it without saving, but
 * neither it nor Back can leave once the quest is being saved, and [onCreated] gets the id of the
 * quest once it is saved. A failed save keeps the form: creating shows a snackbar once, editing
 * shows a message above the button until the next save. A venue that couldn't be read can be read
 * again from the form. A quest to edit that can't be read closes the form through [onClose].
 */
@Composable
fun CreateQuestScreen(
    viewModel: CreateQuestViewModel,
    onClose: () -> Unit,
    onCreated: (questId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val saveFailed = stringResource(R.string.create_quest_save_failed)
  val currentOnCreated by rememberUpdatedState(onCreated)
  val currentOnClose by rememberUpdatedState(onClose)
  LaunchedEffect(state.questUnavailable) { if (state.questUnavailable) currentOnClose() }
  LaunchedEffect(state.saveState) {
    when (val save = state.saveState) {
      is SaveState.Saved -> currentOnCreated(save.id)
      // Editing shows the failure above its button, as long as it lasts
      SaveState.Failed ->
          if (!state.editing) {
            // Launched in the screen's scope, not in this effect: clearing the error changes the
            // effect's key, which would cancel a snackbar shown from inside it
            scope.launch { snackbarHostState.showSnackbar(saveFailed) }
            // Cleared at once, so the snackbar doesn't show again when the screen is recreated
            viewModel.clearSaveError()
          }
      SaveState.Idle,
      SaveState.Saving -> Unit
    }
  }
  // Leaving would cancel the save the venue is waiting on, though its write may still happen
  BackHandler(enabled = state.saveState == SaveState.Saving) {}
  CreateQuestContent(
      state = state,
      onTitleChange = viewModel::updateTitle,
      onDescriptionChange = viewModel::updateDescription,
      onRequirementsChange = viewModel::updateRequirements,
      completionActions =
          QuestCompletionActions(
              location =
                  QuestLocationActions(
                      onAtVenue = viewModel::placeAtVenue,
                      onElsewhere = viewModel::placeElsewhere,
                      onMove = viewModel::moveQuest,
                      onAddressQueryChange = viewModel::updateAddressQuery,
                      onAddressSearch = viewModel::searchAddress,
                      onAddressClear = viewModel::clearAddress,
                      onAddressPick = viewModel::pickAddress,
                  ),
              onProofTypeChange = viewModel::updateProofType,
              onRadiusPresetSelect = viewModel::selectRadiusPreset,
              onCustomRadiusSelect = viewModel::selectCustomRadius,
              onRadiusChange = viewModel::updateRadius,
              onIncrementPartySize = viewModel::incrementPartySize,
              onDecrementPartySize = viewModel::decrementPartySize,
              onExactPartySizeChange = viewModel::updateExactPartySize,
          ),
      rewardActions =
          QuestRewardActions(
              onTypeSelect = viewModel::selectRewardType,
              onDiscountAmountChange = viewModel::updateDiscountAmount,
              onDiscountUnitChange = viewModel::updateDiscountUnit,
              onItemNameChange = viewModel::updateRewardItemName,
              onSpecificsChange = viewModel::updateRewardSpecifics,
              onDescriptionChange = viewModel::updateRewardDescription,
              onExpiryDateChange = viewModel::updateExpiryDate,
          ),
      onSubmit = viewModel::submit,
      onClose = onClose,
      onRetryVenue = viewModel::retryVenue,
      snackbarHostState = snackbarHostState,
      modifier = modifier,
  )
}

/**
 * Stateless form, scrolling above the sticky [CreateQuestBar]. [onRetryVenue] reads the venue again
 * after it couldn't be read.
 */
@Composable
fun CreateQuestContent(
    state: CreateQuestUiState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onRequirementsChange: (String) -> Unit,
    completionActions: QuestCompletionActions,
    rewardActions: QuestRewardActions,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    onRetryVenue: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  Scaffold(
      modifier = modifier,
      containerColor = MaterialTheme.colorScheme.background,
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      bottomBar = { CreateQuestBar(state, onSubmit, onRetryVenue) },
  ) { padding ->
    Column(
        Modifier.fillMaxSize()
            .padding(padding)
            // The bar stays under the keyboard, so the form makes room for the keyboard itself
            .consumeWindowInsets(padding)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = AroundSpacing.ScreenHorizontal,
                vertical = AroundSpacing.ExtraLarge,
            )
            // While its changes are saved, the form fades, as it can't be edited
            .alpha(if (state.editing && state.saveState == SaveState.Saving) SAVING_ALPHA else 1f)
            .testTag(C.Tag.CREATE_QUEST_SCREEN)
    ) {
      // Also disabled once saved, as the screen is closing and a second close would pop its caller
      CreateQuestHeader(
          onClose,
          closeEnabled = state.saveState == SaveState.Idle || state.saveState == SaveState.Failed,
          editing = state.editing,
      )
      Spacer(Modifier.height(AroundSpacing.ExtraLarge))
      QuestTextFields(state, onTitleChange, onDescriptionChange, onRequirementsChange)
      Spacer(Modifier.height(AroundSpacing.Medium))
      QuestCompletionRules(state, completionActions)
      Spacer(Modifier.height(AroundSpacing.SetupGap))
      RewardFields(state.reward, rewardActions)
    }
  }
}

/**
 * The round close button, and the screen title centred on the full width: "Edit quest" when
 * [editing].
 */
@Composable
private fun CreateQuestHeader(onClose: () -> Unit, closeEnabled: Boolean, editing: Boolean) {
  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Surface(
        onClick = onClose,
        enabled = closeEnabled,
        shape = AroundPillShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier =
            Modifier.size(44.dp).align(Alignment.CenterStart).testTag(C.Tag.CREATE_QUEST_CLOSE),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
            painterResource(R.drawable.ic_search_clear),
            stringResource(R.string.create_quest_close),
            tint = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
    Text(
        stringResource(if (editing) R.string.edit_quest_heading else R.string.create_quest_heading),
        style = AroundTextStyles.CardTitle,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.semantics { heading() },
    )
  }
}

/** The label above a form section. */
@Composable
internal fun FieldLabel(text: String) {
  Text(text, style = AroundTextStyles.FieldLabel, color = MaterialTheme.colorScheme.onSurface)
}

/** The form fades to this while the changes to a quest are saved, as in Figma. */
private const val SAVING_ALPHA = 0.6f

/** The empty form of a venue with a location, which shows the radius map. */
// Tall enough for the whole form, as a preview can't scroll
@Preview(name = "Light", widthDp = 413, heightDp = 1800)
@Preview(
    name = "Dark",
    widthDp = 413,
    heightDp = 1800,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
internal fun CreateQuestPreview() {
  AroundAppTheme {
    CreateQuestContent(
        state = CreateQuestUiState(venueLocation = Location(46.5220, 6.6330)),
        onTitleChange = {},
        onDescriptionChange = {},
        onRequirementsChange = {},
        completionActions = QuestCompletionActions(),
        rewardActions = QuestRewardActions(),
        onSubmit = {},
        onClose = {},
        onRetryVenue = {},
    )
  }
}

/** A quest being edited, whose changes failed to save, as in Figma's "couldn't save" state. */
@Preview(name = "Light", widthDp = 413, heightDp = 1800)
@Preview(
    name = "Dark",
    widthDp = 413,
    heightDp = 1800,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
internal fun EditQuestPreview() {
  AroundAppTheme {
    CreateQuestContent(
        state =
            CreateQuestUiState(
                title = "Find the hidden fox",
                description =
                    "A tiny ceramic fox hides somewhere in the café. Spot it and snap a photo.",
                requirements = "Order any drink. One photo per party.",
                venueLocation = Location(46.5220, 6.6330),
                editing = true,
                saveState = SaveState.Failed,
            ),
        onTitleChange = {},
        onDescriptionChange = {},
        onRequirementsChange = {},
        completionActions = QuestCompletionActions(),
        rewardActions = QuestRewardActions(),
        onSubmit = {},
        onClose = {},
        onRetryVenue = {},
    )
  }
}
