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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.user.PendingRoleStore
import com.github.aroundteam2026.aroundapp.model.user.ProfileSetup
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.UserRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.auth.RegistrationFlow

/**
 * Resolves stored roles inside the session gate. Registration shares one ProfileSetup with the
 * router; profile persistence and each role's home are supplied by their owners.
 *
 * This scaffold leaves both home slots empty by default. Production activation belongs to the
 * Firestore integration PR: supply a persistent UserRepository and real role destinations there.
 * Never supply a fake in MainActivity.
 */
@Composable
fun RoleNavigation(
    authRepository: AuthRepository,
    userRepository: UserRepository,
    pendingRoleStore: PendingRoleStore,
    explorerScreen: @Composable () -> Unit = {},
    venueScreen: @Composable (userId: String) -> Unit = {},
) {
  val setup =
      remember(authRepository, userRepository, pendingRoleStore) {
        ProfileSetup(authRepository, userRepository, pendingRoleStore)
      }
  val router: RoleRoutingViewModel =
      viewModel(factory = RoleRoutingViewModel.factory(authRepository, userRepository, setup))
  val state by router.uiState.collectAsStateWithLifecycle()
  SessionNavigation(
      repository = authRepository,
      authFlow = { RegistrationFlow(authRepository, setup, router::retryProfile) },
      mainScreen = { uid ->
        // Session and profile observers can emit on different frames; never show another uid's
        // home.
        val destination =
            when (val resolved = state) {
              is RoleSessionState.SignedIn ->
                  if (resolved.userId != uid) AppDestination.LOADING
                  else
                      when (resolved.role) {
                        null -> AppDestination.ROLE_SELECTION
                        Role.EXPLORER -> AppDestination.EXPLORER_HOME
                        Role.VENUE -> AppDestination.VENUE_ENTRY
                      }
              is RoleSessionState.ProfileUnavailable ->
                  if (resolved.userId == uid) AppDestination.PROFILE_ERROR
                  else AppDestination.LOADING
              else -> AppDestination.LOADING
            }
        val flows = rememberSaveableStateHolder()
        // Keep a resolved home's state while its profile is checking or unavailable.
        if (
            destination in
                listOf(
                    AppDestination.ROLE_SELECTION,
                    AppDestination.EXPLORER_HOME,
                    AppDestination.VENUE_ENTRY,
                )
        ) {
          SideEffect {
            listOf(AppDestination.EXPLORER_HOME, AppDestination.VENUE_ENTRY)
                .filter { it != destination }
                .forEach { flows.removeState(it.name) }
          }
        }
        AppRoot(
            destination = destination,
            authFlow = {},
            roleSelection = {
              RegistrationFlow(
                  authRepository,
                  setup,
                  router::retryProfile,
                  completeRegistration = true,
              )
            },
            explorerHome = {
              flows.SaveableStateProvider(AppDestination.EXPLORER_HOME.name) { explorerScreen() }
            },
            venueEntry = {
              flows.SaveableStateProvider(AppDestination.VENUE_ENTRY.name) {
                venueScreen(requireNotNull(uid))
              }
            },
            profileError = { ProfileError(router::retryProfile) },
        )
      },
  )
}

/** A failed profile read is distinct from an absent profile; retry keeps the auth session. */
@Composable
private fun ProfileError(onRetry: () -> Unit) {
  Column(
      Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp).testTag(C.Tag.ROLE_PROFILE_ERROR),
      verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(stringResource(R.string.role_profile_error))
    Button(onClick = onRetry, modifier = Modifier.testTag(C.Tag.ROLE_PROFILE_RETRY)) {
      Text(stringResource(R.string.role_profile_retry))
    }
  }
}
