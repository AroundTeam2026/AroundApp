package com.github.aroundteam2026.aroundapp.data.user

import com.github.aroundteam2026.aroundapp.model.Role
import com.github.aroundteam2026.aroundapp.model.User
import com.google.firebase.Timestamp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeUserRepositoryTest {

  @Test
  fun createUser_thenGetUser_returnsCreatedUser() = runTest {
    val repository = FakeUserRepository()
    val user =
        User(
            uid = "user-1",
            email = "user@example.com",
            displayName = "Test User",
            role = null,
            createdAt = Timestamp(1000, 0),
        )

    val result = repository.createUser(user)

    assertEquals(Result.success(Unit), result)
    assertEquals(user, repository.getUser("user-1"))
  }

  @Test
  fun getUser_whenUserDoesNotExist_returnsNull() = runTest {
    val repository = FakeUserRepository()

    val user = repository.getUser("unknown-user")

    assertNull(user)
  }

  @Test
  fun setRole_whenRoleIsNull_setsRole() = runTest {
    val repository = FakeUserRepository()
    val user =
        User(
            uid = "user-1",
            email = "user@example.com",
            displayName = "Test User",
            role = null,
            createdAt = Timestamp(1000, 0),
        )
    repository.createUser(user)

    val result = repository.setRole("user-1", Role.EXPLORER)

    assertEquals(Result.success(Unit), result)
    assertEquals(Role.EXPLORER, repository.getUser("user-1")?.role)
  }

  @Test
  fun setRole_whenRoleAlreadySet_failsAndKeepsExistingRole() = runTest {
    val repository = FakeUserRepository()
    val user =
        User(
            uid = "user-1",
            email = "user@example.com",
            displayName = "Test User",
            role = Role.EXPLORER,
            createdAt = Timestamp(1000, 0),
        )
    repository.createUser(user)

    val result = repository.setRole("user-1", Role.VENUE)

    assert(result.isFailure)
    assertEquals(Role.EXPLORER, repository.getUser("user-1")?.role)
  }

  @Test
  fun setRole_whenUserDoesNotExist_fails() = runTest {
    val repository = FakeUserRepository()

    val result = repository.setRole("unknown-user", Role.EXPLORER)

    assert(result.isFailure)
  }

  @Test
  fun observeUser_afterCreateUser_emitsCreatedUser() = runTest {
    val repository = FakeUserRepository()
    val user =
        User(
            uid = "user-1",
            email = "user@example.com",
            displayName = "Test User",
            role = null,
            createdAt = Timestamp(1000, 0),
        )

    repository.createUser(user)

    assertEquals(user, repository.observeUser("user-1").first())
  }

  @Test
  fun observeUser_whenUserChanges_emitsUpdatedUser() = runTest {
    val repository = FakeUserRepository()
    val user =
        User(
            uid = "user-1",
            email = "user@example.com",
            displayName = "Test User",
            role = null,
            createdAt = Timestamp(1000, 0),
        )

    repository.createUser(user)

    val emissions = async { repository.observeUser("user-1").take(2).toList() }

    runCurrent()

    repository.setRole("user-1", Role.EXPLORER)

    val observedUsers = emissions.await()

    assertEquals(user, observedUsers[0])
    assertEquals(user.copy(role = Role.EXPLORER), observedUsers[1])
  }
}
