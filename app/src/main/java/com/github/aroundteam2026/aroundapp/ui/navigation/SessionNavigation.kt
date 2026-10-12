// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
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
      authFlow = { SessionAuthFlow(repository) },
      explorerHome = {
        val session = state as SessionState.SignedIn
        savedFlows.SaveableStateProvider(session.userId) { mainScreen() }
      },
      roleSelection = {},
      venueEntry = {},
      profileError = {},
  )
}

/** Owns form state only while the auth slot is present; leaving it clears credentials and jobs. */
@Composable
private fun SessionAuthFlow(repository: AuthRepository) {
  val owner = remember {
    object : ViewModelStoreOwner {
      override val viewModelStore = ViewModelStore()
    }
  }
  DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
  val authViewModel: AuthViewModel =
      viewModel(viewModelStoreOwner = owner, factory = AuthViewModel.factory(repository))
  AuthScreen(authViewModel)
}
