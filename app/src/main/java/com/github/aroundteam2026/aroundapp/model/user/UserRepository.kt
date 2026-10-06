// Co-authored-by: OpenAI Codex

package com.github.aroundteam2026.aroundapp.model.user

import kotlinx.coroutines.flow.Flow

/**
 * Repository for accessing and managing user profiles.
 *
 * A user's role can only be assigned once and cannot be changed afterward.
 */
interface UserRepository {
  /** Emits the current profile and subsequent changes, or null when [uid] does not exist. */
  fun observeUser(uid: String): Flow<User?>

  /** Returns the profile for [uid], or null when it does not exist. */
  suspend fun getUser(uid: String): User?

  /**
   * Creates [user]. Fails with [IllegalStateException] if its uid already exists, without replacing
   * it.
   */
  suspend fun createUser(user: User): Result<Unit>

  /**
   * Assigns [role] to the user identified by [uid].
   *
   * Fails with [IllegalArgumentException] if the user does not exist, or [IllegalStateException] if
   * the user already has a role.
   */
  suspend fun setRole(uid: String, role: Role): Result<Unit>
}
