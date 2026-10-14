// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import android.util.Log
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.github.aroundteam2026.aroundapp.ui.navigation.Routes

private const val TAG = "VenuePageDestination"

/**
 * Registers a venue's page, which takes the venue's id as [Routes.VENUE_ID].
 *
 * @param onBack Called when the explorer taps the page's back arrow.
 */
fun NavGraphBuilder.venuePageDestination(onBack: () -> Unit) {
  composable(
      Routes.VENUE_PAGE,
      arguments = listOf(navArgument(Routes.VENUE_ID) { type = NavType.StringType }),
  ) { entry ->
    // The route only matches a non-blank id, so the argument is always there
    val venueId = requireNotNull(entry.arguments?.getString(Routes.VENUE_ID))
    VenuePageScreen(viewModel(factory = VenuePageViewModel.factory(venueId)), onBack)
  }
}

/**
 * Opens the page of the venue with [venueId]. Not single-top, so opening another venue's page from
 * a venue's page stacks it, and Back returns to the first venue.
 *
 * A blank [venueId] matches no page, so it opens nothing and is logged instead of crashing.
 */
fun NavController.navigateToVenuePage(venueId: String) {
  if (venueId.isBlank()) {
    Log.w(TAG, "Not opening a venue page: the venue id is blank")
    return
  }
  navigate(Routes.venuePage(venueId))
}
