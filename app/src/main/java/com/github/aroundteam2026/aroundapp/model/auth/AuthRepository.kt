package com.github.aroundteam2026.aroundapp.model.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * Signs users up, in and out, and exposes who is signed in.
 *
 * Every call returns the signed-in user's uid on success, or fails with an [AuthError]. A failed
 * call never changes [currentUserId]: whoever was signed in before stays signed in.
 */
interface AuthRepository {
  /** The uid of the signed-in user, or null when nobody is signed in. */
  val currentUserId: StateFlow<String?>

  /**
   * Creates an account with [email] and [password] and signs it in.
   *
   * Emails are case-insensitive and surrounding spaces are ignored, so `" Ada@Test.com "` and
   * `"ada@test.com"` are the same account.
   *
   * @return the new account's uid, or a failure with [AuthError.InvalidEmail] if [email] is
   *   malformed, [AuthError.WeakPassword] if [password] is too short, [AuthError.EmailAlreadyInUse]
   *   if an account already has this email, or [AuthError.Network] if Firebase can't be reached.
   */
  suspend fun signUpWithEmail(email: String, password: String): Result<String>

  /**
   * Signs in to the existing account with [email] and [password].
   *
   * Emails are matched case-insensitively and surrounding spaces are ignored. Passwords are
   * case-sensitive.
   *
   * @return the account's uid, or a failure with [AuthError.InvalidEmail] if [email] is malformed,
   *   [AuthError.InvalidCredentials] if the password is wrong or no account has this email (the two
   *   are deliberately not told apart), or [AuthError.Network] if Firebase can't be reached.
   */
  suspend fun signInWithEmail(email: String, password: String): Result<String>

  /**
   * Signs in with a Google ID token obtained through Credential Manager in the UI layer, creating
   * the account on first use.
   *
   * @return the account's uid, or a failure with [AuthError.InvalidCredentials] if the token is
   *   rejected, or [AuthError.Network] if Firebase can't be reached.
   */
  suspend fun signInWithGoogle(idToken: String): Result<String>

  /** Signs the current user out. Does nothing if nobody is signed in. */
  fun signOut()
}
