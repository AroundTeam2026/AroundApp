// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.auth.AuthScreen
import com.github.aroundteam2026.aroundapp.ui.auth.AuthViewModel

/** App entry point; authentication replaces the entire previous flow, including its saved state. */
@Composable
fun SessionNavigation(
    repository: AuthRepository,
    navController: NavHostController = rememberNavController(),
    sessionViewModel: SessionViewModel = viewModel(factory = SessionViewModel.factory(repository)),
    mainScreen: @Composable () -> Unit = { AroundApp() },
) {
  val state by sessionViewModel.uiState.collectAsStateWithLifecycle()
  var appliedSession by rememberSaveable { mutableStateOf<String?>(null) }
  LaunchedEffect(state) {
    val destination =
        when (state) {
          SessionState.Checking -> CHECKING
          SessionState.SignedOut -> AUTH_GRAPH
          is SessionState.SignedIn -> MAIN_GRAPH
        }
    val sessionKey =
        when (val session = state) {
          SessionState.Checking -> CHECKING
          SessionState.SignedOut -> AUTH_GRAPH
          is SessionState.SignedIn -> "user/${session.userId}"
        }
    val alreadyInFlow =
        navController.currentDestination?.hierarchy?.any { it.route == destination } == true
    if (alreadyInFlow && (appliedSession == null || appliedSession == sessionKey)) {
      appliedSession = sessionKey
      return@LaunchedEffect
    }
    appliedSession = sessionKey
    navController.navigate(destination) {
      popUpTo(navController.graph.id) { inclusive = false }
      launchSingleTop = true
    }
  }
  NavHost(navController, startDestination = CHECKING) {
    composable(CHECKING) { SessionLoading() }
    navigation(startDestination = AUTH_FORM, route = AUTH_GRAPH) {
      composable(AUTH_FORM) {
        if (state == SessionState.SignedOut) {
          val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(repository))
          AuthScreen(authViewModel)
        } else {
          SessionLoading()
        }
      }
    }
    navigation(startDestination = MAIN_TABS, route = MAIN_GRAPH) {
      composable(MAIN_TABS) {
        if (state is SessionState.SignedIn) mainScreen() else SessionLoading()
      }
    }
  }
}

@Composable
internal fun SessionLoading() {
  val description = stringResource(R.string.session_checking)
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(
        Modifier.testTag(C.Tag.SESSION_LOADING).semantics { contentDescription = description }
    )
  }
}

private const val CHECKING = "session/checking"
private const val AUTH_GRAPH = "auth"
private const val AUTH_FORM = "auth/form"
private const val MAIN_GRAPH = "main"
private const val MAIN_TABS = "main/tabs"
