// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController

/** Keeps a restored stack while checking; a resolved account/flow change replaces it. */
@Composable
internal fun SessionFlowEffect(
    flow: String,
    sessionKey: String,
    checkingRoute: String,
    navController: NavHostController,
) {
  var appliedSession by rememberSaveable { mutableStateOf<String?>(null) }
  LaunchedEffect(flow, sessionKey) {
    val destination = navController.currentDestination
    if (flow == checkingRoute && destination != null && destination.route != checkingRoute) {
      return@LaunchedEffect
    }
    val alreadyInFlow = destination?.hierarchy?.any { it.route == flow } == true
    if (alreadyInFlow && (appliedSession == null || appliedSession == sessionKey)) {
      appliedSession = sessionKey
      return@LaunchedEffect
    }
    appliedSession = sessionKey
    navController.navigate(flow) {
      popUpTo(navController.graph.id) { inclusive = false }
      launchSingleTop = true
    }
  }
}
