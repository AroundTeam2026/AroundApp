// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.auth

/** Why an authentication call failed. Screens map each case to a readable message. */
sealed class AuthError(cause: Throwable? = null) : Exception(cause) {
  /** Wrong password, or no account with that email. Deliberately not told apart. */
  data object InvalidCredentials : AuthError()

  data object InvalidEmail : AuthError()

  data object EmailAlreadyInUse : AuthError()

  data object WeakPassword : AuthError()

  data object Network : AuthError()

  class Unknown(cause: Throwable? = null) : AuthError(cause)
}
