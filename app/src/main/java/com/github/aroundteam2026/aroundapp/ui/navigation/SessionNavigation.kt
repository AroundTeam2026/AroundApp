// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.ui.auth.AuthScreen
import com.github.aroundteam2026.aroundapp.ui.auth.AuthViewModel

/**
 * Selects loading, auth or the existing tabs from the repository session. Saved tab state survives
 * a new ViewModel checking the same session after recreation. Role routing and account-transition
 * back-stack policy are separate integration tasks.
 */
@Composable
fun SessionNavigation(
    repository: AuthRepository,
    sessionViewModel: SessionViewModel = viewModel(factory = SessionViewModel.factory(repository)),
    mainScreen: @Composable () -> Unit = { AroundApp() },
) {
  val state by sessionViewModel.uiState.collectAsStateWithLifecycle()
  val savedFlows = rememberSaveableStateHolder()
  val destination =
      when (state) {
        SessionState.Checking -> AppDestination.LOADING
        SessionState.SignedOut -> AppDestination.AUTH
        is SessionState.SignedIn -> AppDestination.EXPLORER_HOME
      }
  AppRoot(
      destination = destination,
      authFlow = {
        val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(repository))
        AuthScreen(authViewModel)
      },
      explorerHome = {
        val session = state as SessionState.SignedIn
        savedFlows.SaveableStateProvider(session.userId) { mainScreen() }
      },
      roleSelection = {},
      venueEntry = {},
      profileError = {},
  )
}
