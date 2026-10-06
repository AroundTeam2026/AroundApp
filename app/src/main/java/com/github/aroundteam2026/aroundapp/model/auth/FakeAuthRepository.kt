package com.github.aroundteam2026.aroundapp.model.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [AuthRepository] for tests and for running the app without Firebase.
 *
 * Follows Firebase's rules: emails are case-insensitive, passwords need at least
 * [MIN_PASSWORD_LENGTH] characters, and an email can only be registered once.
 */
class FakeAuthRepository(signedInUserId: String? = null) : AuthRepository {

  private data class Account(val uid: String, val password: String)

  private val accounts = mutableMapOf<String, Account>()
  private val googleAccounts = mutableMapOf<String, String>()
  private val _currentUserId = MutableStateFlow(signedInUserId)
  private var nextUid = 1

  override val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

  /** When set, the next call fails with this error, then it is cleared. */
  var nextError: AuthError? = null

  /** Registers an account without signing in, for test setup. Returns its uid. */
  fun addAccount(email: String, password: String): String {
    val uid = "fake-uid-${nextUid++}"
    accounts[normalize(email)] = Account(uid, password)
    return uid
  }

  override suspend fun signUpWithEmail(email: String, password: String): Result<String> {
    consumeNextError()?.let {
      return Result.failure(it)
    }
    val key = normalize(email)
    return when {
      !EMAIL_REGEX.matches(key) -> Result.failure(AuthError.InvalidEmail)
      password.length < MIN_PASSWORD_LENGTH -> Result.failure(AuthError.WeakPassword)
      key in accounts -> Result.failure(AuthError.EmailAlreadyInUse)
      else -> signIn(addAccount(key, password))
    }
  }

  override suspend fun signInWithEmail(email: String, password: String): Result<String> {
    consumeNextError()?.let {
      return Result.failure(it)
    }
    val key = normalize(email)
    if (!EMAIL_REGEX.matches(key)) return Result.failure(AuthError.InvalidEmail)
    val account = accounts[key]
    return if (account != null && account.password == password) {
      signIn(account.uid)
    } else {
      Result.failure(AuthError.InvalidCredentials)
    }
  }

  override suspend fun signInWithGoogle(idToken: String): Result<String> {
    consumeNextError()?.let {
      return Result.failure(it)
    }
    if (idToken.isBlank()) return Result.failure(AuthError.InvalidCredentials)
    return signIn(googleAccounts.getOrPut(idToken) { "fake-uid-${nextUid++}" })
  }

  override fun signOut() {
    _currentUserId.value = null
  }

  private fun signIn(uid: String): Result<String> {
    _currentUserId.value = uid
    return Result.success(uid)
  }

  private fun consumeNextError(): AuthError? = nextError.also { nextError = null }

  private fun normalize(email: String) = email.trim().lowercase()

  companion object {
    const val MIN_PASSWORD_LENGTH = 6
    private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
  }
}
