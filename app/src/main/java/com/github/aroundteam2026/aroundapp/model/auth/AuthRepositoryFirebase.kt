// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex
package com.github.aroundteam2026.aroundapp.model.auth

import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * [AuthRepository] backed by Firebase Authentication.
 *
 * Firebase keeps the session on the device, so a signed-in user is still signed in after a restart.
 */
class AuthRepositoryFirebase(private val auth: FirebaseAuth = Firebase.auth) : AuthRepository {

  private val _currentUserId = MutableStateFlow(auth.currentUser?.uid)
  override val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

  init {
    // Also catches changes made outside this class, such as an expired or deleted account.
    // The listener lives as long as the app, since AppContainer creates one instance.
    auth.addAuthStateListener { _currentUserId.value = it.currentUser?.uid }
  }

  override suspend fun signUpWithEmail(email: String, password: String): Result<String> = authCall {
    val normalizedEmail = email.trim()
    if (!AuthInputValidation.isValidEmail(normalizedEmail)) throw AuthError.InvalidEmail
    if (password.length < AuthInputValidation.MIN_PASSWORD_LENGTH) throw AuthError.WeakPassword
    auth.createUserWithEmailAndPassword(normalizedEmail, password).await().user?.uid
  }

  override suspend fun signInWithEmail(email: String, password: String): Result<String> = authCall {
    val normalizedEmail = email.trim()
    if (!AuthInputValidation.isValidEmail(normalizedEmail)) throw AuthError.InvalidEmail
    if (password.isEmpty()) throw AuthError.InvalidCredentials
    auth.signInWithEmailAndPassword(normalizedEmail, password).await().user?.uid
  }

  override suspend fun signInWithGoogle(idToken: String): Result<String> = authCall {
    if (idToken.isBlank()) throw AuthError.InvalidCredentials
    val credential = GoogleAuthProvider.getCredential(idToken, null)
    auth.signInWithCredential(credential).await().user?.uid
  }

  override fun signOut() {
    auth.signOut()
    _currentUserId.value = null
  }

  /** Runs a Firebase call, updates [currentUserId] right away and maps failures to [AuthError]. */
  private suspend fun authCall(call: suspend () -> String?): Result<String> =
      try {
        val uid = call()
        if (uid != null) {
          _currentUserId.value = uid
          Result.success(uid)
        } else {
          Result.failure(AuthError.Unknown())
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e.toAuthError())
      }
}

/** Maps a Firebase exception to an [AuthError]. */
internal fun Throwable.toAuthError(): AuthError =
    when (this) {
      is AuthError -> this
      // Must come before FirebaseAuthInvalidCredentialsException, which is its parent class.
      is FirebaseAuthWeakPasswordException -> AuthError.WeakPassword
      is FirebaseAuthUserCollisionException -> AuthError.EmailAlreadyInUse
      is FirebaseAuthInvalidCredentialsException ->
          if (errorCode == "ERROR_INVALID_EMAIL") AuthError.InvalidEmail
          else AuthError.InvalidCredentials
      is FirebaseAuthInvalidUserException -> AuthError.InvalidCredentials
      is FirebaseNetworkException -> AuthError.Network
      else -> AuthError.Unknown(this)
    }
