package com.github.aroundteam2026.aroundapp.data.user

import com.github.aroundteam2026.aroundapp.model.Role
import com.github.aroundteam2026.aroundapp.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
  fun observeUser(uid: String): Flow<User?>

  suspend fun getUser(uid: String): User?

  suspend fun createUser(user: User): Result<Unit>

  suspend fun setRole(uid: String, role: Role): Result<Unit>
}
