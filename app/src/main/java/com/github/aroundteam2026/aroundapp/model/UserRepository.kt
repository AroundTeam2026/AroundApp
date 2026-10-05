package com.github.aroundteam2026.aroundapp.model

import kotlinx.coroutines.flow.Flow

/**
 * Repository for accessing and managing user profiles.
 *
 * A user's role can only be assigned once and cannot be changed afterward.
 */
interface UserRepository {
  fun observeUser(uid: String): Flow<User?>

  suspend fun getUser(uid: String): User?

  suspend fun createUser(user: User): Result<Unit>

  /**
   * Assigns [role] to the user identified by [uid].
   *
   * Fails with [IllegalArgumentException] if the user does not exist, or [IllegalStateException] if
   * the user already has a role.
   */
  suspend fun setRole(uid: String, role: Role): Result<Unit>
}
