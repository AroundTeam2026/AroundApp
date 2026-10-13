// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.overview

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.navigation.ComingSoonScreen
import com.github.aroundteam2026.aroundapp.ui.navigation.Routes

/** Registers the overview of quests. Until it is built, it shows a "Coming soon" stub. */
fun NavGraphBuilder.overviewDestination() {
  composable(Routes.OVERVIEW) { ComingSoonScreen(R.string.overview_title, C.Tag.OVERVIEW_STUB) }
}

/** Opens the overview of quests, without stacking it again if it is already on top. */
fun NavController.navigateToOverview() = navigate(Routes.OVERVIEW) { launchSingleTop = true }
