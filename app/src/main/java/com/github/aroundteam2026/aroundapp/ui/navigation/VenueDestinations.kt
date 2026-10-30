// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.github.aroundteam2026.aroundapp.ui.quest.create.createQuestDestination
import com.github.aroundteam2026.aroundapp.ui.quest.create.navigateToCreateQuest

/**
 * Registers the venue home and the screens it opens, in the venue's NavHost. Those screens are
 * shown full screen above the home, so that their own bottom bars don't sit on the home's tabs.
 *
 * The home keeps its tabs' back stack while the form is open, so closing the form, or saving its
 * quest, returns to the Quests tab it was opened from.
 *
 * @param navController the venue's NavHost, between the home and the screens above it.
 * @param onSignOut signs the venue out, from the Profile tab.
 * @param createQuestFactory builds the create-quest form's ViewModel; tests pass one on fakes.
 * @param venueQuestsFactory builds the Quests tab's ViewModel; tests pass one on fakes.
 * @param dashboardFactory builds the Dashboard tab's ViewModel; tests pass one on fakes.
 */
fun NavGraphBuilder.venueDestinations(
    navController: NavHostController,
    onSignOut: () -> Unit,
    createQuestFactory: ViewModelProvider.Factory,
    venueQuestsFactory: ViewModelProvider.Factory,
    dashboardFactory: ViewModelProvider.Factory,
) {
  composable(Routes.VENUE_HOME) {
    VenueHome(
        onCreateQuest = navController::navigateToCreateQuest,
        onSignOut = onSignOut,
        venueQuestsFactory = venueQuestsFactory,
        dashboardFactory = dashboardFactory,
    )
  }
  // By route: a second tap on X during the form's exit fade must not also pop the home
  createQuestDestination(
      onClose = { navController.popBackStack(Routes.CREATE_QUEST, inclusive = true) },
      factory = createQuestFactory,
  )
}
