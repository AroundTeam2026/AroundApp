package com.github.aroundteam2026.aroundapp.model.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * Signs users up, in and out, and exposes who is signed in.
 *
 * Every call returns the signed-in user's uid on success, or fails with an [AuthError].
 */
interface AuthRepository {
  /** The uid of the signed-in user, or null when nobody is signed in. */
  val currentUserId: StateFlow<String?>

  suspend fun signUpWithEmail(email: String, password: String): Result<String>

  suspend fun signInWithEmail(email: String, password: String): Result<String>

  /** Signs in with a Google ID token obtained through Credential Manager in the UI layer. */
  suspend fun signInWithGoogle(idToken: String): Result<String>

  fun signOut()
}
