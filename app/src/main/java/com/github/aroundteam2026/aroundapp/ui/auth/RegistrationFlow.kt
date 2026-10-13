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
 * Normal registration records a role before auth emits its uid. The router calls
 * [ProfileSetup.finish] on that session change, including after a restart. In
 * [completeRegistration] mode, only the registration choices are shown: [onAccountSetupComplete]
 * tells the router to call finish again after the signed-in user's choice reaches disk. Profile
 * errors/retry and the final destination belong to the router. This flow never navigates or
 * defaults a missing role.
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
