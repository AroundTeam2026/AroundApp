// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.user.ProfileSetup
import com.github.aroundteam2026.aroundapp.model.user.RegistrationChoice

/**
 * Content for AppRoot's authFlow and roleSelection slots, sharing the router's [registration] (its
 * [ProfileSetup]).
 *
 * Normal registration records a role before auth emits its uid, and the router calls
 * [ProfileSetup.finish] on that session change. In [completeRegistration] mode only the role
 * choices show, and [onAccountSetupComplete] tells the router to call finish again once the choice
 * reaches disk. Profile errors, retry and the destination belong to the router: this flow never
 * navigates or defaults a missing role.
 */
@Composable
fun RegistrationFlow(
    authRepository: AuthRepository,
    registration: RegistrationChoice,
    onAccountSetupComplete: () -> Unit,
    completeRegistration: Boolean = false,
) {
  // Activity-scoped like SessionNavigation's form: rotation keeps a pending request, which
  // cancelling Task.await() could not stop, and a successful submit clears the credentials.
  val form: AuthViewModel =
      viewModel(
          key = if (completeRegistration) "account-setup" else "registration",
          factory = AuthViewModel.factory(authRepository, registration),
      )
  val state by form.uiState.collectAsStateWithLifecycle()
  LaunchedEffect(state.accountSetupComplete) {
    if (state.accountSetupComplete) {
      onAccountSetupComplete()
      form.onAccountSetupHandled()
    }
  }
  AuthScreen(form, completeRegistration = completeRegistration)
}
