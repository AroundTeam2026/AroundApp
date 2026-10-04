package com.github.aroundteam2026.aroundapp.data.user

import com.github.aroundteam2026.aroundapp.model.Role
import com.github.aroundteam2026.aroundapp.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeUserRepository : UserRepository {

  private val users = MutableStateFlow<Map<String, User>>(emptyMap())

  override fun observeUser(uid: String): Flow<User?> {
    return users.map { it[uid] }
  }

  override suspend fun getUser(uid: String): User? {
    return users.value[uid]
  }

  override suspend fun createUser(user: User): Result<Unit> {
    users.value = users.value + (user.uid to user)
    return Result.success(Unit)
  }

  override suspend fun setRole(uid: String, role: Role): Result<Unit> {
    val user = users.value[uid] ?: return Result.failure(IllegalArgumentException("User not found"))

    if (user.role != null) {
      return Result.failure(IllegalStateException("User role is already set"))
    }

    users.value = users.value + (uid to user.copy(role = role))
    return Result.success(Unit)
  }
}
