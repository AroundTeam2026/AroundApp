// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.auth

/** Why an authentication call failed. Screens map each case to a readable message. */
sealed class AuthError(cause: Throwable? = null) : Exception(cause) {
  /** Wrong password, or no account with that email. Deliberately not told apart. */
  data object InvalidCredentials : AuthError()

  /** The email is not well formed. */
  data object InvalidEmail : AuthError()

  /** An account already uses this email. */
  data object EmailAlreadyInUse : AuthError()

  /** The password is too weak for the provider. */
  data object WeakPassword : AuthError()

  /** The provider could not be reached. */
  data object Network : AuthError()

  /** Any other failure; [cause] keeps the original error. */
  class Unknown(cause: Throwable? = null) : AuthError(cause)
}
