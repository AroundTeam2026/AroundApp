// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.dashboard

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.navigation.ComingSoonScreen
import com.github.aroundteam2026.aroundapp.ui.navigation.Routes

/** Registers the venue's dashboard. Until it is built, it shows a "Coming soon" stub. */
fun NavGraphBuilder.dashboardDestination() {
  composable(Routes.DASHBOARD) { ComingSoonScreen(R.string.dashboard_title, C.Tag.DASHBOARD_STUB) }
}

/** Opens the venue's dashboard, without stacking it again if it is already on top. */
fun NavController.navigateToDashboard() = navigate(Routes.DASHBOARD) { launchSingleTop = true }
