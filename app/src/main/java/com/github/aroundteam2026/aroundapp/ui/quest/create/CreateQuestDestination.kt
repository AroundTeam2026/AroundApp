// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.github.aroundteam2026.aroundapp.ui.navigation.Routes

private const val TAG = "CreateQuestDestination"

/**
 * Registers the form where a venue creates a quest.
 *
 * @param onClose Called when the venue closes the form, or once its quest is saved.
 * @param factory Builds the form's ViewModel; tests pass one on fakes.
 */
fun NavGraphBuilder.createQuestDestination(
    onClose: () -> Unit,
    factory: ViewModelProvider.Factory = CreateQuestViewModel.factory(),
) {
  composable(Routes.CREATE_QUEST) {
    CreateQuestScreen(viewModel(factory = factory), onClose = onClose, onCreated = { onClose() })
  }
}

/** Opens the create-quest form, without stacking it again if it is already on top. */
fun NavController.navigateToCreateQuest() = navigate(Routes.CREATE_QUEST) { launchSingleTop = true }

/**
 * Registers the form where a venue edits a quest, which takes the quest's id as [Routes.QUEST_ID].
 *
 * @param onClose Called when the venue closes the form, once its changes are saved, or if the quest
 *   can't be read.
 * @param factory Builds the form's ViewModel for a quest's id; tests pass one on fakes.
 */
fun NavGraphBuilder.editQuestDestination(
    onClose: () -> Unit,
    factory: (String) -> ViewModelProvider.Factory = CreateQuestViewModel::factory,
) {
  composable(
      Routes.EDIT_QUEST,
      arguments = listOf(navArgument(Routes.QUEST_ID) { type = NavType.StringType }),
  ) { entry ->
    // The route only matches a non-blank id, so the argument is always there
    val questId = requireNotNull(entry.arguments?.getString(Routes.QUEST_ID))
    // Keyed by the quest: single-top compares the route pattern, so two quick taps on different
    // quests could otherwise keep the first quest's ViewModel
    CreateQuestScreen(
        viewModel(key = questId, factory = factory(questId)),
        onClose = onClose,
        onCreated = { onClose() },
    )
  }
}

/**
 * Opens the form editing the quest [questId], without stacking it again if a double tap already
 * opened it.
 *
 * A blank [questId] matches no route, so it opens nothing and is logged instead of crashing.
 */
fun NavController.navigateToEditQuest(questId: String) {
  if (questId.isBlank()) {
    Log.w(TAG, "Not editing a quest: the quest id is blank")
    return
  }
  navigate(Routes.editQuest(questId)) { launchSingleTop = true }
}
