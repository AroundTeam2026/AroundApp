// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepositoryProvider
import com.github.aroundteam2026.aroundapp.ui.quest.create.CreateQuestScreen
import com.github.aroundteam2026.aroundapp.ui.quest.create.CreateQuestViewModel
import com.github.aroundteam2026.aroundapp.ui.venue.VenueAreaScreen

private const val VENUE_HOME_ROUTE = "venue_home"
private const val CREATE_QUEST_ROUTE = "venue_create_quest"

/**
 * Owns the existing venue onboarding and home graphs inside the authenticated session. Business
 * setup reuses an existing venue instead of overwriting it. Saving its location replaces onboarding
 * with the home, so Back cannot reopen setup or authentication. The session gate clears the whole
 * graph on sign-out. Child screens own their ViewModels; repository implementations stay outside
 * them.
 *
 * Screen slots let tests exercise graph transitions without a Maps SDK or backend.
 * [createQuestFactory] can supply an isolated repository-backed form, as in createQuestDestination.
 */
@Composable
fun VenueEntryNavigation(
    authRepository: AuthRepository,
    venueRepository: VenueRepository = VenueRepositoryProvider.repository,
    locationScreen: @Composable (String, onSaved: () -> Unit, onBack: () -> Unit) -> Unit =
        { venueId, onSaved, onBack ->
          VenueAreaScreen(venueId, onSaved, onBack)
        },
    homeScreen: @Composable (onCreateQuest: () -> Unit, onSignOut: () -> Unit) -> Unit =
        { onCreateQuest, onSignOut ->
          VenueHome(onCreateQuest, onSignOut)
        },
    createQuestFactory: ViewModelProvider.Factory = CreateQuestViewModel.factory(),
    createQuestScreen: @Composable (onClose: () -> Unit) -> Unit = { onClose ->
      val form: CreateQuestViewModel = viewModel(factory = createQuestFactory)
      CreateQuestScreen(form, onClose = onClose, onCreated = { onClose() })
    },
) {
  val navController = rememberNavController()
  NavHost(navController, startDestination = VenueOnboardingRoutes.INITIALIZATION) {
    venueOnboardingDestinations(navController, authRepository, venueRepository) { venueId, onBack ->
      locationScreen(
          venueId,
          {
            navController.navigate(VENUE_HOME_ROUTE) {
              popUpTo(VenueOnboardingRoutes.INITIALIZATION) { inclusive = true }
              launchSingleTop = true
            }
          },
          onBack,
      )
    }
    composable(VENUE_HOME_ROUTE) {
      homeScreen(
          { navController.navigate(CREATE_QUEST_ROUTE) { launchSingleTop = true } },
          authRepository::signOut,
      )
    }
    composable(CREATE_QUEST_ROUTE) { createQuestScreen { navController.popBackStack() } }
  }
}
