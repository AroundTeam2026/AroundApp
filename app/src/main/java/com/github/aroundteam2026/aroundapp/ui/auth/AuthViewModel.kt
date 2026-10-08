// Co-authored-by: OpenAI Codex
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.AuthError
import com.github.aroundteam2026.aroundapp.model.auth.AuthInputValidation
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the form can't go ahead. The screen turns each into a string resource. */
enum class AuthFormError {
  INVALID_EMAIL,
  EMPTY_PASSWORD,
  PASSWORD_TOO_SHORT,
  PASSWORD_MISMATCH,
  WRONG_CREDENTIALS,
  EMAIL_TAKEN,
  NETWORK,
  UNKNOWN,
}

/**
 * State of the shared sign-in/sign-up form.
 *
 * @property email Email entered by the user.
 * @property password Password entered by the user.
 * @property passwordConfirmation Repeated password, checked only for sign-up.
 * @property isLoading Whether an authentication request is in progress.
 * @property error Current validation or repository error, or null.
 */
data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val isLoading: Boolean = false,
    val error: AuthFormError? = null,
)

/**
 * Holds the sign-in/sign-up form and talks to [AuthRepository].
 *
 * Input is checked locally first, so obviously invalid forms never reach the repository. While a
 * request runs, further submits are ignored. On success nothing changes here: the repository's
 * `currentUserId` updates, and navigation reacts to that.
 */
class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(AuthUiState())
  /** Observable form state; successful authentication updates [AuthRepository.currentUserId]. */
  val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

  /** Updates the email and clears the previous form error. */
  fun onEmailChange(email: String) = _uiState.update { it.copy(email = email, error = null) }

  /** Updates the password and clears the previous form error. */
  fun onPasswordChange(password: String) = _uiState.update {
    it.copy(password = password, error = null)
  }

  /** Updates the sign-up password confirmation and clears the previous form error. */
  fun onPasswordConfirmationChange(passwordConfirmation: String) = _uiState.update {
    it.copy(passwordConfirmation = passwordConfirmation, error = null)
  }

  /** Validates and signs in; ignores submissions while a request is in progress. */
  fun signIn() =
      submit(
          validate = { state ->
            when {
              !AuthInputValidation.isValidEmail(state.email) -> AuthFormError.INVALID_EMAIL
              state.password.isEmpty() -> AuthFormError.EMPTY_PASSWORD
              else -> null
            }
          },
          call = repository::signInWithEmail,
      )

  /** Validates email, password length and confirmation before creating an account. */
  fun signUp() =
      submit(
          validate = { state ->
            when {
              !AuthInputValidation.isValidEmail(state.email) -> AuthFormError.INVALID_EMAIL
              state.password.length < AuthInputValidation.MIN_PASSWORD_LENGTH ->
                  AuthFormError.PASSWORD_TOO_SHORT
              state.password != state.passwordConfirmation -> AuthFormError.PASSWORD_MISMATCH
              else -> null
            }
          },
          call = repository::signUpWithEmail,
      )

  private fun submit(
      validate: (AuthUiState) -> AuthFormError?,
      call: suspend (email: String, password: String) -> Result<String>,
  ) {
    val state = _uiState.value
    if (state.isLoading) return
    val invalid = validate(state)
    if (invalid != null) {
      _uiState.update { it.copy(error = invalid) }
      return
    }
    _uiState.update { it.copy(isLoading = true, error = null) }
    viewModelScope.launch {
      val result = call(state.email.trim(), state.password)
      _uiState.update {
        it.copy(isLoading = false, error = result.exceptionOrNull()?.let(::toFormError))
      }
    }
  }

  companion object {
    private fun toFormError(error: Throwable): AuthFormError =
        when (error) {
          AuthError.InvalidEmail -> AuthFormError.INVALID_EMAIL
          AuthError.WeakPassword -> AuthFormError.PASSWORD_TOO_SHORT
          AuthError.InvalidCredentials -> AuthFormError.WRONG_CREDENTIALS
          AuthError.EmailAlreadyInUse -> AuthFormError.EMAIL_TAKEN
          AuthError.Network -> AuthFormError.NETWORK
          else -> AuthFormError.UNKNOWN
        }

    /** Builds the ViewModel with its repository, for `viewModel(factory = ...)` in Compose. */
    fun factory(repository: AuthRepository): ViewModelProvider.Factory = viewModelFactory {
      initializer { AuthViewModel(repository) }
    }
  }
}
