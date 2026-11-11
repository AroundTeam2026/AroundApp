// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.github.aroundteam2026.aroundapp.ui.quest.create.CreateQuestViewModel
import com.github.aroundteam2026.aroundapp.ui.quest.create.createQuestDestination
import com.github.aroundteam2026.aroundapp.ui.quest.create.editQuestDestination
import com.github.aroundteam2026.aroundapp.ui.quest.create.navigateToCreateQuest
import com.github.aroundteam2026.aroundapp.ui.quest.create.navigateToEditQuest
import com.github.aroundteam2026.aroundapp.ui.review.defaultReviewProofFactory
import com.github.aroundteam2026.aroundapp.ui.review.navigateToReviewProof
import com.github.aroundteam2026.aroundapp.ui.review.reviewProofDestination

/**
 * Registers the venue home and the screens it opens, in the venue's NavHost. Those screens are
 * shown full screen above the home, so that their own bottom bars don't sit on the home's tabs.
 *
 * The home keeps its tabs' back stack while the form is open, so closing the form, or saving its
 * quest, returns to the Quests tab it was opened from. Tapping a quest there opens the same form to
 * edit it, and returns likewise. Tapping a proof on the dashboard opens the Review proof screen,
 * and leaving it returns to the dashboard.
 *
 * @param navController the venue's NavHost, between the home and the screens above it.
 * @param onSignOut signs the venue out, from the Profile tab.
 * @param createQuestFactory builds the create-quest form's ViewModel; tests pass one on fakes.
 * @param venueQuestsFactory builds the Quests tab's ViewModel; tests pass one on fakes.
 * @param dashboardFactory builds the Dashboard tab's ViewModel; tests pass one on fakes.
 * @param editQuestFactory builds the edit-quest form's ViewModel for a quest's id; tests pass one
 *   on fakes.
 * @param reviewProofFactory builds the Review proof screen's ViewModel for a completion's id; tests
 *   pass one on fakes.
 */
fun NavGraphBuilder.venueDestinations(
    navController: NavHostController,
    onSignOut: () -> Unit,
    createQuestFactory: ViewModelProvider.Factory,
    venueQuestsFactory: ViewModelProvider.Factory,
    dashboardFactory: ViewModelProvider.Factory,
    reviewProofFactory: (String) -> ViewModelProvider.Factory = ::defaultReviewProofFactory,
    editQuestFactory: (String) -> ViewModelProvider.Factory = CreateQuestViewModel::factory,
) {
  composable(Routes.VENUE_HOME) {
    VenueHome(
        onCreateQuest = navController::navigateToCreateQuest,
        onEditQuest = navController::navigateToEditQuest,
        onSignOut = onSignOut,
        venueQuestsFactory = venueQuestsFactory,
        dashboardFactory = dashboardFactory,
        onOpenProof = navController::navigateToReviewProof,
    )
  }
  // By route: a second tap on X during the form's exit fade must not also pop the home
  createQuestDestination(
      onClose = { navController.popBackStack(Routes.CREATE_QUEST, inclusive = true) },
      factory = createQuestFactory,
  )
  // By route too: the form also closes itself once its changes are saved
  editQuestDestination(
      onClose = { navController.popBackStack(Routes.EDIT_QUEST, inclusive = true) },
      factory = editQuestFactory,
  )
  // By route too: the screen also closes itself once the proof is reviewed
  reviewProofDestination(
      onClose = { navController.popBackStack(Routes.REVIEW_PROOF, inclusive = true) },
      factory = reviewProofFactory,
  )
}
