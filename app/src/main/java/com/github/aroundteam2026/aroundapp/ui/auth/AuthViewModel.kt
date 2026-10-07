// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.AuthError
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
  WRONG_CREDENTIALS,
  EMAIL_TAKEN,
  NETWORK,
  UNKNOWN,
}

/** Everything the sign-in screen shows. */
data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: AuthFormError? = null,
)

/**
 * Holds the sign-in form and talks to [AuthRepository].
 *
 * Input is checked locally first, so obviously invalid forms never reach the repository. While a
 * request runs, further submits are ignored. On success nothing changes here: the repository's
 * `currentUserId` updates, and navigation reacts to that.
 */
class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(AuthUiState())
  val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

  fun onEmailChange(email: String) = _uiState.update { it.copy(email = email, error = null) }

  fun onPasswordChange(password: String) = _uiState.update {
    it.copy(password = password, error = null)
  }

  fun signIn() =
      submit(
          validate = { state ->
            when {
              !isValidEmail(state.email) -> AuthFormError.INVALID_EMAIL
              state.password.isEmpty() -> AuthFormError.EMPTY_PASSWORD
              else -> null
            }
          },
          call = repository::signInWithEmail,
      )

  fun signUp() =
      submit(
          validate = { state ->
            when {
              !isValidEmail(state.email) -> AuthFormError.INVALID_EMAIL
              state.password.length < MIN_PASSWORD_LENGTH -> AuthFormError.PASSWORD_TOO_SHORT
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
    /** Firebase's minimum; checked here so users get feedback before a network call. */
    const val MIN_PASSWORD_LENGTH = 6
    private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    private fun isValidEmail(email: String) = EMAIL_REGEX.matches(email.trim())

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
