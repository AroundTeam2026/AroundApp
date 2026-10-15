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
import com.github.aroundteam2026.aroundapp.model.user.RegistrationChoice
import com.github.aroundteam2026.aroundapp.model.user.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the form can't go ahead. The screen turns each into a string resource. */
enum class AuthFormError {
  /** The email is malformed. */
  INVALID_EMAIL,
  /** The sign-in password is empty. */
  EMPTY_PASSWORD,
  /** The sign-up password is shorter than the minimum length. */
  PASSWORD_TOO_SHORT,
  /** The sign-up password confirmation does not match the password. */
  PASSWORD_MISMATCH,
  /** The email or password does not match an existing account. */
  WRONG_CREDENTIALS,
  /** An account already uses the sign-up email. */
  EMAIL_TAKEN,
  /** Authentication failed because the service could not be reached. */
  NETWORK,
  /** The registration choice could not be recorded; authentication must not start. */
  ACCOUNT_SETUP,
  /** Authentication failed for an unrecognized reason. */
  UNKNOWN,
  /** No Google credential provider or account is available. */
  GOOGLE_UNAVAILABLE,
  /** This build has no web OAuth client configured. */
  GOOGLE_CONFIGURATION,
}

/** The account chooser's result, before the repository exchanges a token with Firebase. */
sealed interface GoogleCredentialResult {
  /** A Google ID token ready for the repository to exchange. */
  data class Token(val idToken: String) : GoogleCredentialResult

  /** The user dismissed the chooser without selecting an account. */
  data object Cancelled : GoogleCredentialResult

  /** No usable account or credential provider is available on this device. */
  data object Unavailable : GoogleCredentialResult

  /** This build is missing its Web OAuth client configuration. */
  data object Misconfigured : GoogleCredentialResult

  /** The provider failed or returned an unsupported/malformed credential. */
  data object Failed : GoogleCredentialResult
}

/**
 * State of the shared sign-in/sign-up form.
 *
 * @property email Email entered by the user.
 * @property password Password entered by the user.
 * @property passwordConfirmation Repeated password, checked only for sign-up.
 * @property isLoading Whether an authentication request is in progress.
 * @property error Current validation or repository error, or null.
 * @property accountSetupComplete A signed-in account's choice is saved; the router can finish it.
 */
data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val isLoading: Boolean = false,
    val error: AuthFormError? = null,
    val accountSetupComplete: Boolean = false,
)

/**
 * Holds the sign-in/sign-up form and talks to [AuthRepository].
 *
 * Input is checked locally first, so obviously invalid forms never reach the repository. While a
 * request runs, further submits are ignored. Success clears the form state; the repository's
 * `currentUserId` updates, and navigation reacts to that.
 */
class AuthViewModel(
    private val repository: AuthRepository,
    private val registration: RegistrationChoice? = null,
) : ViewModel() {

  private var awaitingGoogleCredential = false

  /** Registration paths need [registration]; auth-only callers keep the existing form. */
  val supportsRegistration: Boolean = registration != null

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

  /** Clears confirmation and the previous form error when switching authentication modes. */
  fun onModeChange() = _uiState.update { it.copy(passwordConfirmation = "", error = null) }

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
          // Clear before auth emits a uid: the router may immediately call ProfileSetup.finish().
          prepare = { registration?.discardChoice() ?: Result.success(Unit) },
          call = repository::signInWithEmail,
      )

  /**
   * Validates the form, durably records [role], then creates the account for the router to finish.
   */
  fun signUp(role: Role = Role.EXPLORER) =
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
          prepare = { registration?.choose(role) ?: Result.success(Unit) },
          call = repository::signUpWithEmail,
      )

  /** Reserves the form while the UI opens the Google account chooser. */
  fun beginGoogleSignIn(): Boolean {
    if (_uiState.value.isLoading) return false
    awaitingGoogleCredential = true
    _uiState.update { it.copy(isLoading = true, error = null) }
    return true
  }

  /** Cancels the chooser only; repository authentication survives UI recreation. */
  fun cancelGoogleSignIn() {
    if (!awaitingGoogleCredential) return
    awaitingGoogleCredential = false
    _uiState.update { it.copy(isLoading = false) }
  }

  /** Handles a plain credential outcome; the role router owns profile completion. */
  fun onGoogleCredential(result: GoogleCredentialResult) {
    if (!awaitingGoogleCredential) return
    awaitingGoogleCredential = false
    when (result) {
      is GoogleCredentialResult.Token ->
          authenticate(
              prepare = { registration?.discardChoice() ?: Result.success(Unit) },
              call = { repository.signInWithGoogle(result.idToken) },
          )
      GoogleCredentialResult.Cancelled -> _uiState.update { it.copy(isLoading = false) }
      GoogleCredentialResult.Unavailable -> fail(AuthFormError.GOOGLE_UNAVAILABLE)
      GoogleCredentialResult.Misconfigured -> fail(AuthFormError.GOOGLE_CONFIGURATION)
      GoogleCredentialResult.Failed -> fail(AuthFormError.UNKNOWN)
    }
  }

  /**
   * Records an existing account's choice without authenticating again. The router owns finish().
   */
  fun completeRegistration(role: Role) {
    if (_uiState.value.isLoading || _uiState.value.accountSetupComplete) return
    if (repository.currentUserId.value == null || registration == null) {
      _uiState.update { it.copy(error = AuthFormError.ACCOUNT_SETUP) }
      return
    }
    _uiState.update { it.copy(isLoading = true, error = null) }
    viewModelScope.launch {
      val result = registration.choose(role)
      _uiState.update {
        it.copy(
            isLoading = false,
            accountSetupComplete = result.isSuccess,
            error = if (result.isFailure) AuthFormError.ACCOUNT_SETUP else null,
        )
      }
    }
  }

  /** Acknowledges the notification after the router has been asked to resolve the profile again. */
  fun onAccountSetupHandled() = _uiState.update { it.copy(accountSetupComplete = false) }

  private fun submit(
      validate: (AuthUiState) -> AuthFormError?,
      prepare: suspend () -> Result<Unit>,
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
    authenticate(prepare) { call(state.email.trim(), state.password) }
  }

  private fun fail(error: AuthFormError) = _uiState.update {
    it.copy(isLoading = false, error = error)
  }

  private fun authenticate(
      prepare: suspend () -> Result<Unit>,
      call: suspend () -> Result<String>,
  ) {
    viewModelScope.launch {
      if (prepare().isFailure) {
        _uiState.update { it.copy(isLoading = false, error = AuthFormError.ACCOUNT_SETUP) }
        return@launch
      }
      val result = call()
      _uiState.update {
        if (result.isSuccess) AuthUiState()
        else it.copy(isLoading = false, error = result.exceptionOrNull()?.let(::toFormError))
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
    fun factory(
        repository: AuthRepository,
        registration: RegistrationChoice? = null,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { AuthViewModel(repository, registration) }
    }
  }
}
