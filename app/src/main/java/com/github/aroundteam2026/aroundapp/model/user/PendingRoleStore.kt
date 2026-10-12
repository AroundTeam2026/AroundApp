// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.user

/**
 * A role chosen at registration and not yet saved to a profile.
 *
 * @property uid the account it was chosen for, or null when it was chosen before sign-up, when no
 *   account existed yet.
 */
data class PendingRole(val role: Role, val uid: String?)

/**
 * Remembers the account type a user chose at registration, until their profile is saved.
 *
 * The record must survive process death: if the app is killed between the choice and the profile
 * save, the next start finishes the profile with this role rather than guessing one.
 */
interface PendingRoleStore {
  /** Returns the recorded choice, or null when none is recorded. */
  suspend fun load(): PendingRole?

  /**
   * Records [pending], replacing any earlier record, and returns once it is on disk.
   *
   * @throws java.io.IOException if it couldn't be written. Sign-up must not start then.
   */
  suspend fun save(pending: PendingRole)

  /**
   * Removes the record and returns once that is on disk. Does nothing if none is recorded.
   *
   * @throws java.io.IOException if it couldn't be written: the record would otherwise outlive the
   *   account it was meant for.
   */
  suspend fun clear()
}
