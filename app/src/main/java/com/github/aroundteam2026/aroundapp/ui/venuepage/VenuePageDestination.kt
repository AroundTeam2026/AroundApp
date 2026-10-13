// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.navigation.ComingSoonScreen
import com.github.aroundteam2026.aroundapp.ui.navigation.Routes

/**
 * Registers a venue's page, which takes the venue's id as [Routes.VENUE_ID]. Until the page is
 * built, it shows a "Coming soon" stub.
 */
fun NavGraphBuilder.venuePageDestination() {
  composable(
      Routes.VENUE_PAGE,
      arguments = listOf(navArgument(Routes.VENUE_ID) { type = NavType.StringType }),
  ) {
    ComingSoonScreen(R.string.venue_page_title, C.Tag.VENUE_PAGE_STUB)
  }
}

/**
 * Opens the page of the venue with [venueId]. Not single-top, so opening another venue's page from
 * a venue's page stacks it, and Back returns to the first venue.
 */
fun NavController.navigateToVenuePage(venueId: String) = navigate(Routes.venuePage(venueId))
