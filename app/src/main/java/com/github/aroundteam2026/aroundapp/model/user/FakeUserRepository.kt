// Co-authored-by: OpenAI Codex

package com.github.aroundteam2026.aroundapp.model.user

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map

/**
 * In-memory implementation of [UserRepository] for local development and testing without Firestore.
 */
class FakeUserRepository : UserRepository {

  private val users = MutableStateFlow<Map<String, User>>(emptyMap())

  override fun observeUser(uid: String): Flow<User?> {
    return users.map { it[uid] }.distinctUntilChanged()
  }

  override suspend fun getUser(uid: String): User? {
    return users.value[uid]
  }

  override suspend fun createUser(user: User): Result<Unit> {
    val previous = users.getAndUpdate { current ->
      if (user.uid in current) current else current + (user.uid to user)
    }
    return if (user.uid in previous) {
      Result.failure(IllegalStateException("User already exists"))
    } else {
      Result.success(Unit)
    }
  }

  override suspend fun setRole(uid: String, role: Role): Result<Unit> {
    val previous = users.getAndUpdate { current ->
      val user = current[uid]
      if (user != null && user.role == null) current + (uid to user.copy(role = role)) else current
    }
    val user = previous[uid]
    return when {
      user == null -> Result.failure(IllegalArgumentException("User not found"))
      user.role != null -> Result.failure(IllegalStateException("User role is already set"))
      else -> Result.success(Unit)
    }
  }
}
