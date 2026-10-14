// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.aroundteam2026.aroundapp.ui.quest.create.CreateQuestViewModel
import com.github.aroundteam2026.aroundapp.ui.quest.create.createQuestDestination
import com.github.aroundteam2026.aroundapp.ui.quest.create.navigateToCreateQuest

/**
 * The venue's NavHost: it opens on the venue home, and shows the screens the home opens full screen
 * above it, so that their own bottom bars don't sit on the home's tabs.
 *
 * @param onSignOut signs the venue out, from the Profile tab.
 * @param createQuestFactory builds the create-quest form's ViewModel; tests pass one on fakes.
 */
@Composable
fun VenueNavigation(
    onSignOut: () -> Unit,
    navController: NavHostController = rememberNavController(),
    createQuestFactory: ViewModelProvider.Factory = CreateQuestViewModel.factory(),
) {
  NavHost(navController, startDestination = Routes.VENUE_HOME) {
    venueDestinations(navController, onSignOut, createQuestFactory)
  }
}

/**
 * Registers the venue home and the screens it opens, for a NavHost that reaches the home.
 *
 * The home keeps its tabs' back stack while the form is open, so closing the form, or saving its
 * quest, returns to the Quests tab it was opened from.
 */
fun NavGraphBuilder.venueDestinations(
    navController: NavHostController,
    onSignOut: () -> Unit,
    createQuestFactory: ViewModelProvider.Factory = CreateQuestViewModel.factory(),
) {
  composable(Routes.VENUE_HOME) {
    VenueHome(onCreateQuest = navController::navigateToCreateQuest, onSignOut = onSignOut)
  }
  // By route: a second tap on X during the form's exit fade must not also pop the home
  createQuestDestination(
      onClose = { navController.popBackStack(Routes.CREATE_QUEST, inclusive = true) },
      factory = createQuestFactory,
  )
}
