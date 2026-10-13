// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.user

/**
 * Records the account type chosen at registration, so the registration form can depend on an
 * interface. [ProfileSetup] implements it and later turns the choice into the user's profile.
 */
interface RegistrationChoice {
  /**
   * Records [role] as the account type to save. If it fails, registration must not go on: without
   * the record, an interrupted save couldn't tell which type was chosen.
   */
  suspend fun choose(role: Role): Result<Unit>

  /** Forgets a choice not bound to an account, before an ordinary sign-in. */
  suspend fun discardChoice(): Result<Unit>
}
