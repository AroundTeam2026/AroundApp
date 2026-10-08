// Co-authored-by: OpenAI Codex
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [AuthRepository] for tests and for running the app without Firebase.
 *
 * Follows Firebase's rules: emails are case-insensitive, passwords need at least
 * [AuthInputValidation.MIN_PASSWORD_LENGTH] characters, and an email can only be registered once.
 * Generated uids never collide with [signedInUserId] or with each other.
 *
 * Safe to call from several threads at once: every operation runs under one lock, so two concurrent
 * sign-ups with the same email can't both succeed.
 */
class FakeAuthRepository(signedInUserId: String? = null) : AuthRepository {

  private data class Account(val uid: String, val password: String)

  /** Guards every field below. No operation suspends while holding it. */
  private val lock = Any()

  private val accounts = mutableMapOf<String, Account>()
  private val googleAccounts = mutableMapOf<String, String>()
  private val _currentUserId = MutableStateFlow(signedInUserId)
  private var nextUid = 1

  /** Every uid in use, including [signedInUserId], so new accounts never reuse one. */
  private val usedUids = mutableSetOf<String>().apply { signedInUserId?.let { add(it) } }

  override val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

  /** When set, the next call fails with this error and changes nothing, then it is cleared. */
  @Volatile var nextError: AuthError? = null

  /** Registers an account without signing in, for test setup. Returns its uid. */
  fun addAccount(email: String, password: String): String =
      synchronized(lock) {
        val uid = newUid()
        accounts[normalize(email)] = Account(uid, password)
        uid
      }

  override suspend fun signUpWithEmail(email: String, password: String): Result<String> {
    return synchronized(lock) {
      consumeNextError()?.let {
        return Result.failure(it)
      }
      val key = normalize(email)
      when {
        !AuthInputValidation.isValidEmail(key) -> Result.failure(AuthError.InvalidEmail)
        password.length < AuthInputValidation.MIN_PASSWORD_LENGTH ->
            Result.failure(AuthError.WeakPassword)
        key in accounts -> Result.failure(AuthError.EmailAlreadyInUse)
        else -> signIn(addAccount(key, password))
      }
    }
  }

  override suspend fun signInWithEmail(email: String, password: String): Result<String> {
    return synchronized(lock) {
      consumeNextError()?.let {
        return Result.failure(it)
      }
      val key = normalize(email)
      if (!AuthInputValidation.isValidEmail(key)) return Result.failure(AuthError.InvalidEmail)
      val account = accounts[key]
      if (account != null && account.password == password) {
        signIn(account.uid)
      } else {
        Result.failure(AuthError.InvalidCredentials)
      }
    }
  }

  override suspend fun signInWithGoogle(idToken: String): Result<String> {
    return synchronized(lock) {
      consumeNextError()?.let {
        return Result.failure(it)
      }
      if (idToken.isBlank()) return Result.failure(AuthError.InvalidCredentials)
      signIn(googleAccounts.getOrPut(idToken) { newUid() })
    }
  }

  override fun signOut() {
    synchronized(lock) { _currentUserId.value = null }
  }

  /** Must be called while holding [lock]. */
  private fun signIn(uid: String): Result<String> {
    _currentUserId.value = uid
    return Result.success(uid)
  }

  /**
   * Returns the next `fake-uid-N` that isn't already in use. Must be called while holding [lock].
   */
  private fun newUid(): String {
    var uid: String
    do {
      uid = "fake-uid-${nextUid++}"
    } while (uid in usedUids)
    usedUids += uid
    return uid
  }

  /** Must be called while holding [lock]. */
  private fun consumeNextError(): AuthError? = nextError.also { nextError = null }

  private fun normalize(email: String) = email.trim().lowercase()
}
