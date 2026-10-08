// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.UserRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.auth.AuthScreen
import com.github.aroundteam2026.aroundapp.ui.auth.AuthViewModel

/**
 * Role-aware integration entry point. Screen owners supply role selection and the Venue flow; both
 * receive the authenticated uid. Selection persists the role through [userRepository], whose
 * updates drive navigation. Production wiring requires a persistent user repository.
 */
@Composable
fun RoleNavigation(
    authRepository: AuthRepository,
    userRepository: UserRepository,
    roleSelectionScreen: @Composable (userId: String) -> Unit,
    venueScreen: @Composable (userId: String) -> Unit,
    explorerScreen: @Composable () -> Unit = { AroundApp() },
    navController: NavHostController = rememberNavController(),
    routingViewModel: RoleRoutingViewModel =
        viewModel(factory = RoleRoutingViewModel.factory(authRepository, userRepository)),
) {
  val state by routingViewModel.uiState.collectAsStateWithLifecycle()
  val flow = state.destination()
  var appliedSession by rememberSaveable { mutableStateOf<String?>(null) }
  LaunchedEffect(state) {
    val uid =
        when (val session = state) {
          is RoleSessionState.SignedIn -> session.userId
          is RoleSessionState.ProfileUnavailable -> session.userId
          else -> ""
        }
    val sessionKey = "$flow/$uid"
    val alreadyInFlow =
        navController.currentDestination?.hierarchy?.any { it.route == flow } == true
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
  NavHost(navController, startDestination = CHECKING) {
    composable(CHECKING) { SessionLoading() }
    navigation(startDestination = AUTH_FORM, route = AUTH) {
      composable(AUTH_FORM) {
        if (state == RoleSessionState.SignedOut) {
          val authViewModel: AuthViewModel =
              viewModel(factory = AuthViewModel.factory(authRepository))
          AuthScreen(authViewModel)
        } else SessionLoading()
      }
    }
    navigation(startDestination = EXPLORER_HOME, route = EXPLORER) {
      composable(EXPLORER_HOME) { if (flow == EXPLORER) explorerScreen() else SessionLoading() }
    }
    navigation(startDestination = VENUE_HOME, route = VENUE) {
      composable(VENUE_HOME) {
        val session = state as? RoleSessionState.SignedIn
        if (session?.role == Role.VENUE) venueScreen(session.userId) else SessionLoading()
      }
    }
    composable(ROLE_SELECTION) {
      val session = state as? RoleSessionState.SignedIn
      if (session != null && session.role == null) roleSelectionScreen(session.userId)
      else SessionLoading()
    }
    composable(PROFILE_ERROR) {
      if (state is RoleSessionState.ProfileUnavailable) {
        ProfileError(routingViewModel::retryProfile, authRepository::signOut)
      } else SessionLoading()
    }
  }
}

/** Maps resolved authentication and role state to a graph; failures do not enter onboarding. */
private fun RoleSessionState.destination(): String =
    when (this) {
      RoleSessionState.Checking -> CHECKING
      RoleSessionState.SignedOut -> AUTH
      is RoleSessionState.ProfileUnavailable -> PROFILE_ERROR
      is RoleSessionState.SignedIn ->
          when (role) {
            null -> ROLE_SELECTION
            Role.EXPLORER -> EXPLORER
            Role.VENUE -> VENUE
          }
    }

@Composable
private fun ProfileError(onRetry: () -> Unit, onSignOut: () -> Unit) {
  Column(
      Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
        stringResource(R.string.session_profile_error),
        Modifier.testTag(C.Tag.SESSION_ERROR).semantics { liveRegion = LiveRegionMode.Polite },
    )
    Button(onClick = onRetry, modifier = Modifier.testTag(C.Tag.SESSION_RETRY)) {
      Text(stringResource(R.string.session_retry))
    }
    Button(onClick = onSignOut, modifier = Modifier.testTag(C.Tag.SESSION_SIGN_OUT)) {
      Text(stringResource(R.string.auth_sign_out))
    }
  }
}

private const val CHECKING = "role-session/checking"
private const val AUTH = "role-session/auth"
private const val AUTH_FORM = "role-session/auth/form"
private const val EXPLORER = "role-session/explorer"
private const val EXPLORER_HOME = "role-session/explorer/home"
private const val VENUE = "role-session/venue"
private const val VENUE_HOME = "role-session/venue/home"
private const val ROLE_SELECTION = "role-session/selection"
private const val PROFILE_ERROR = "role-session/error"
