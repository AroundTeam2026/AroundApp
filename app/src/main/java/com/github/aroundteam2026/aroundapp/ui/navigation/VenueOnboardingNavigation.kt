// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.ui.venue.VenueInitializationScreen
import com.github.aroundteam2026.aroundapp.ui.venue.VenueInitializationViewModel

/** Destinations shared by the auth router and venue setup screens. */
object VenueOnboardingRoutes {
  const val INITIALIZATION = "venue_initialization"
  const val LOCATION = "venue_location/{venueId}"

  /** The route to the location step of the venue [venueId], encoded so any id fits. */
  fun location(venueId: String): String = "venue_location/${android.net.Uri.encode(venueId)}"

  /** The same two steps, opened from "Edit details" on the venue's Profile tab. */
  const val EDIT = "venue_edit"
  const val EDIT_LOCATION = "venue_edit_location/{venueId}"
}

/** Opens the venue's details for editing, from "Edit details" on its Profile tab. */
fun NavHostController.navigateToVenueEdit() {
  navigate(VenueOnboardingRoutes.EDIT) { launchSingleTop = true }
}

/** Called by the auth screen after a successful Venue sign-up. */
fun NavHostController.navigateToVenueInitialization() {
  navigate(VenueOnboardingRoutes.INITIALIZATION) { launchSingleTop = true }
}

/**
 * Adds venue setup to its owner's NavHost. The location screen is supplied by its owner;
 * [locationScreen] receives the saved owner's uid and a callback to return to initialization.
 *
 * Initialization is scoped to its back-stack entry, so its name and saved venue survive Back from
 * location. Returning to it does not immediately navigate forward again. Pass null for
 * [initializationOnBack] when initialization is the graph root, so system Back leaves the flow.
 * With [editing], the same steps edit an existing venue from its profile, on their own routes.
 */
fun NavGraphBuilder.venueOnboardingDestinations(
    navController: NavHostController,
    authRepository: AuthRepository,
    venueRepository: VenueRepository,
    initializationOnBack: (() -> Unit)? = { navController.popBackStack() },
    editing: Boolean = false,
    locationScreen: @Composable (venueId: String, onBack: () -> Unit) -> Unit,
) {
  val start = if (editing) VenueOnboardingRoutes.EDIT else VenueOnboardingRoutes.INITIALIZATION
  val location =
      if (editing) VenueOnboardingRoutes.EDIT_LOCATION else VenueOnboardingRoutes.LOCATION
  composable(start) { entry ->
    val initializationViewModel =
        viewModel<VenueInitializationViewModel>(viewModelStoreOwner = entry) {
          VenueInitializationViewModel(authRepository, venueRepository, editing = editing)
        }
    VenueInitializationScreen(
        viewModel = initializationViewModel,
        onBack = initializationOnBack,
        onContinueToLocation = { venueId ->
          navController.navigate(location.replace("{venueId}", android.net.Uri.encode(venueId))) {
            launchSingleTop = true
          }
        },
    )
  }
  composable(
      location,
      arguments = listOf(navArgument("venueId") { type = NavType.StringType }),
  ) { entry ->
    locationScreen(requireNotNull(entry.arguments?.getString("venueId"))) {
      navController.popBackStack()
    }
  }
}
