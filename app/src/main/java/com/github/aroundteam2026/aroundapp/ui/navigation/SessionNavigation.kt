// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.ui.auth.AuthScreen
import com.github.aroundteam2026.aroundapp.ui.auth.AuthViewModel
import com.github.aroundteam2026.aroundapp.ui.auth.GoogleCredentialSessionEffect
import com.github.aroundteam2026.aroundapp.ui.profile.ProfileViewModel

/**
 * Selects loading, auth or the existing tabs from the repository session. Saved tab state survives
 * a new ViewModel checking the same session after recreation, but is discarded on sign-out or an
 * account change. AppRoot disposes the inactive flow and its Back handlers; no root back stack is
 * retained. Signed-in ViewModels survive recreation and are cleared with their session. Role
 * routing is a separate integration task.
 *
 * @param mainScreen receives the uid whose retained signed-in store owns this composition.
 * @param authFlow allows the role router to supply the shared registration form.
 */
@Composable
fun SessionNavigation(
    repository: AuthRepository,
    sessionViewModel: SessionViewModel = viewModel(factory = SessionViewModel.factory(repository)),
    mainScreen: @Composable (userId: String) -> Unit = { userId ->
      val profileViewModel: ProfileViewModel =
          viewModel(factory = ProfileViewModel.factory(repository))
      AroundApp(explorerUid = userId, onSignOut = profileViewModel::signOut)
    },
    authFlow: @Composable () -> Unit = {
      val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(repository))
      AuthScreen(authViewModel)
    },
) {
  GoogleCredentialSessionEffect(repository)
  val state by sessionViewModel.uiState.collectAsStateWithLifecycle()
  val savedFlows = rememberSaveableStateHolder()
  var savedUserId by rememberSaveable { mutableStateOf<String?>(null) }
  if (state != SessionState.Checking) {
    val userId = (state as? SessionState.SignedIn)?.userId
    SideEffect {
      sessionViewModel.retainSignedInStore(userId)
      if (savedUserId != userId) {
        savedUserId?.let(savedFlows::removeState)
        savedUserId = userId
      }
    }
  }
  val destination =
      when (state) {
        SessionState.Checking -> AppDestination.LOADING
        SessionState.SignedOut -> AppDestination.AUTH
        is SessionState.SignedIn -> AppDestination.EXPLORER_HOME
      }
  AppRoot(
      destination = destination,
      authFlow = authFlow,
      explorerHome = {
        val session = state as SessionState.SignedIn
        key(session.userId) {
          CompositionLocalProvider(
              LocalViewModelStoreOwner provides sessionViewModel.signedInStoreOwner(session.userId)
          ) {
            savedFlows.SaveableStateProvider(session.userId) { mainScreen(session.userId) }
          }
        }
      },
      roleSelection = {},
      venueEntry = {},
      profileError = {},
  )
}
