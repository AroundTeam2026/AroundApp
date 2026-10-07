// Co-authored-by: OpenAI Codex <noreply@openai.com>
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
}

/** Called by the auth screen after a successful Venue sign-up. */
fun NavHostController.navigateToVenueInitialization() {
  navigate(VenueOnboardingRoutes.INITIALIZATION) { launchSingleTop = true }
}

/**
 * Adds venue setup to the auth owner's NavHost. The location screen is supplied by its owner;
 * [locationScreen] receives the saved owner's uid and a callback to return to initialization.
 *
 * Initialization is scoped to its back-stack entry, so its name and saved venue survive Back from
 * location. Returning to it does not immediately navigate forward again.
 */
fun NavGraphBuilder.venueOnboardingDestinations(
    navController: NavHostController,
    authRepository: AuthRepository,
    venueRepository: VenueRepository,
    locationScreen: @Composable (venueId: String, onBack: () -> Unit) -> Unit,
) {
  composable(VenueOnboardingRoutes.INITIALIZATION) { entry ->
    val initializationViewModel =
        viewModel<VenueInitializationViewModel>(viewModelStoreOwner = entry) {
          VenueInitializationViewModel(authRepository, venueRepository)
        }
    VenueInitializationScreen(
        viewModel = initializationViewModel,
        onBack = { navController.popBackStack() },
        onContinueToLocation = { venueId ->
          navController.navigate("venue_location/${android.net.Uri.encode(venueId)}") {
            launchSingleTop = true
          }
        },
    )
  }
  composable(
      VenueOnboardingRoutes.LOCATION,
      arguments = listOf(navArgument("venueId") { type = NavType.StringType }),
  ) { entry ->
    locationScreen(requireNotNull(entry.arguments?.getString("venueId"))) {
      navController.popBackStack()
    }
  }
}
