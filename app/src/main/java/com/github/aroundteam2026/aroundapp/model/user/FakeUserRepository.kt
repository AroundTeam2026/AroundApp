// Co-authored-by: OpenAI Codex

package com.github.aroundteam2026.aroundapp.model.user

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

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
    var created = false

    users.update { current ->
      if (user.uid in current) {
        current
      } else {
        created = true
        current + (user.uid to user)
      }
    }

    return if (created) {
      Result.success(Unit)
    } else {
      Result.failure(IllegalStateException("User already exists"))
    }
  }

  override suspend fun setRole(uid: String, role: Role): Result<Unit> {
    var result: Result<Unit> = Result.success(Unit)

    users.update { current ->
      val user = current[uid]

      when {
        user == null -> {
          result = Result.failure(IllegalArgumentException("User not found"))
          current
        }

        user.role != null -> {
          result = Result.failure(IllegalStateException("User role is already set"))
          current
        }

        else -> current + (uid to user.copy(role = role))
      }
    }

    return result
  }
}
