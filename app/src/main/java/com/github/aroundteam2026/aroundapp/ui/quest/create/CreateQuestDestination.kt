// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.github.aroundteam2026.aroundapp.ui.navigation.Routes

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
