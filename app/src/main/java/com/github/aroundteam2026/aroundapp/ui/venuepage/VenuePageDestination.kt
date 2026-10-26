// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import android.util.Log
import androidx.compose.runtime.Composable
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
 * @param page Draws the page of the venue with the given id, with [onBack] as its back action.
 */
fun NavGraphBuilder.venuePageDestination(
    onBack: () -> Unit,
    page: @Composable (venueId: String, onBack: () -> Unit) -> Unit,
) {
  composable(
      Routes.VENUE_PAGE,
      arguments = listOf(navArgument(Routes.VENUE_ID) { type = NavType.StringType }),
  ) { entry ->
    // The route only matches a non-blank id, so the argument is always there
    val venueId = requireNotNull(entry.arguments?.getString(Routes.VENUE_ID))
    page(venueId, onBack)
  }
}

/**
 * A venue's page as the app shows it to the explorer [explorerUid], with the app's repositories.
 */
@Composable
fun VenuePage(venueId: String, explorerUid: String, onBack: () -> Unit) =
    VenuePageScreen(viewModel(factory = VenuePageViewModel.factory(venueId, explorerUid)), onBack)

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
