// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.user.UserPictureRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepositoryProvider
import com.github.aroundteam2026.aroundapp.ui.profile.ProfileViewModel
import com.github.aroundteam2026.aroundapp.ui.quest.create.CreateQuestViewModel
import com.github.aroundteam2026.aroundapp.ui.venue.VenueAreaScreen

/**
 * Draws the area step of onboarding for the venue [venueId].
 *
 * @param venueId id of the venue whose area is set.
 * @param onSaved called once the area is saved.
 * @param onBack called when the venue goes back from the area.
 */
typealias VenueAreaStep =
    @Composable (venueId: String, onSaved: () -> Unit, onBack: () -> Unit) -> Unit

/** The real area step: a [VenueAreaScreen], shared by [VenueEntry] and [VenueEntryNavigation]. */
val defaultVenueAreaStep: VenueAreaStep = { venueId, onSaved, onBack ->
  VenueAreaScreen(venueId, onSaved, onBack)
}

/**
 * Owns the venue onboarding and home graphs inside the authenticated session.
 *
 * Business setup reuses an existing venue instead of overwriting it, and saving its location
 * replaces onboarding with the home, so Back cannot reopen setup. The session gate clears the graph
 * on sign-out, and [VenueEntry] chooses [start] from the venue's saved state. Tests isolate Maps
 * through the location slot, and the form, quest list, dashboard and profile through
 * [createQuestFactory], [venueQuestsFactory], [dashboardFactory] and [venueProfileFactory].
 *
 * @param start where the graph opens: business setup, its area, or the home of a venue already set
 *   up. A venue resuming on its area starts at business setup with the area opened above it.
 * @param navController the graph's controller; [VenueEntry] and tests pass their own to drive it.
 * @param venuePictures stores the venue's picture, from its business details.
 */
@Composable
fun VenueEntryNavigation(
    authRepository: AuthRepository,
    venueRepository: VenueRepository = VenueRepositoryProvider.repository,
    venuePictures: UserPictureRepository,
    locationScreen: VenueAreaStep = defaultVenueAreaStep,
    createQuestFactory: ViewModelProvider.Factory = CreateQuestViewModel.factory(),
    venueQuestsFactory: ViewModelProvider.Factory,
    dashboardFactory: ViewModelProvider.Factory,
    venueProfileFactory: ViewModelProvider.Factory,
    start: VenueEntryStart = VenueEntryStart.Initialization,
    navController: NavHostController = rememberNavController(),
) {
  val profile: ProfileViewModel =
      viewModel(factory = ProfileViewModel.factory(authRepository, proofUploadQueue = null))
  NavHost(
      navController,
      startDestination =
          if (start == VenueEntryStart.Home) Routes.VENUE_HOME
          else VenueOnboardingRoutes.INITIALIZATION,
  ) {
    venueOnboardingDestinations(
        navController,
        authRepository,
        venueRepository,
        venuePictures,
        // Setup is the root right after sign-up: Back signs out instead of trapping the owner.
        initializationOnBack = profile::signOut,
    ) { venueId, onBack ->
      locationScreen(
          venueId,
          {
            navController.navigate(Routes.VENUE_HOME) {
              popUpTo(VenueOnboardingRoutes.INITIALIZATION) { inclusive = true }
              launchSingleTop = true
            }
          },
          onBack,
      )
    }
    venueDestinations(
        navController,
        profile::signOut,
        createQuestFactory,
        VenueTabFactories(venueQuestsFactory, dashboardFactory, venueProfileFactory),
    )
    // Editing from the profile: saving the area returns to the Profile tab
    venueOnboardingDestinations(
        navController,
        authRepository,
        venueRepository,
        venuePictures,
        editing = true,
    ) { venueId, onBack ->
      locationScreen(
          venueId,
          { navController.popBackStack(VenueOnboardingRoutes.EDIT, inclusive = true) },
          onBack,
      )
    }
  }
  if (start is VenueEntryStart.Area) ResumeOnArea(start.venueId, navController)
}

/**
 * Opens the area of the venue [venueId] above its business name, so Back from the area returns to
 * the saved name, as for a venue that just gave it.
 */
@Composable
private fun ResumeOnArea(venueId: String, navController: NavHostController) {
  // Once only, and not again after recreation: the back stack is saved with the area on top
  var resumed by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    if (!resumed) {
      resumed = true
      navController.navigate(VenueOnboardingRoutes.location(venueId)) { launchSingleTop = true }
    }
  }
}
