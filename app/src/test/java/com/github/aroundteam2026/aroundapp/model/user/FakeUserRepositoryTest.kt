// Co-authored-by: OpenAI Codex

package com.github.aroundteam2026.aroundapp.model.user

import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert
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
            createdAt = 1_000L,
        )

    val result = repository.createUser(user)

    Assert.assertEquals(Result.success(Unit), result)
    Assert.assertEquals(user, repository.getUser("user-1"))
  }

  @Test
  fun createUser_whenUserAlreadyExists_failsAndKeepsExistingUser() = runTest {
    val repository = FakeUserRepository()
    val originalUser =
        User(
            uid = "user-1",
            email = "original@example.com",
            displayName = "Original User",
            role = Role.EXPLORER,
            createdAt = 1_000L,
        )
    val replacementUser =
        User(
            uid = "user-1",
            email = "replacement@example.com",
            displayName = "Replacement User",
            role = null,
            createdAt = 2_000L,
        )

    repository.createUser(originalUser)
    val result = repository.createUser(replacementUser)

    Assert.assertTrue(result.isFailure)
    Assert.assertTrue(result.exceptionOrNull() is IllegalStateException)
    Assert.assertEquals(originalUser, repository.getUser("user-1"))
  }

  @Test
  fun getUser_whenUserDoesNotExist_returnsNull() = runTest {
    val repository = FakeUserRepository()

    val user = repository.getUser("unknown-user")

    Assert.assertNull(user)
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
            createdAt = 1_000L,
        )
    repository.createUser(user)

    val result = repository.setRole("user-1", Role.EXPLORER)

    Assert.assertEquals(Result.success(Unit), result)
    Assert.assertEquals(Role.EXPLORER, repository.getUser("user-1")?.role)
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
            createdAt = 1_000L,
        )
    repository.createUser(user)

    val result = repository.setRole("user-1", Role.VENUE)

    Assert.assertTrue(result.isFailure)
    Assert.assertTrue(result.exceptionOrNull() is IllegalStateException)
    Assert.assertEquals(Role.EXPLORER, repository.getUser("user-1")?.role)
  }

  @Test
  fun setRole_whenUserDoesNotExist_fails() = runTest {
    val repository = FakeUserRepository()

    val result = repository.setRole("unknown-user", Role.EXPLORER)

    Assert.assertTrue(result.isFailure)
    Assert.assertTrue(result.exceptionOrNull() is IllegalArgumentException)
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
            createdAt = 1_000L,
        )

    repository.createUser(user)

    Assert.assertEquals(user, repository.observeUser("user-1").first())
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
            createdAt = 1_000L,
        )

    repository.createUser(user)

    val emissions = async { repository.observeUser("user-1").take(2).toList() }

    runCurrent()

    repository.setRole("user-1", Role.EXPLORER)

    val observedUsers = emissions.await()

    Assert.assertEquals(user, observedUsers[0])
    Assert.assertEquals(user.copy(role = Role.EXPLORER), observedUsers[1])
  }

  @Test
  fun observeUser_whenAnotherUserIsCreated_doesNotReemit() = runTest {
    val repository = FakeUserRepository()
    val userA =
        User(
            uid = "user-a",
            email = "a@example.com",
            displayName = "User A",
            role = null,
            createdAt = 1_000L,
        )
    val userB =
        User(
            uid = "user-b",
            email = "b@example.com",
            displayName = "User B",
            role = null,
            createdAt = 2_000L,
        )

    repository.createUser(userA)

    val emissions = mutableListOf<User?>()
    val job = launch { repository.observeUser("user-a").collect { emissions.add(it) } }

    runCurrent()

    repository.createUser(userB)
    runCurrent()

    Assert.assertEquals(listOf(userA), emissions)

    job.cancel()
  }

  @Test
  fun observeUser_whenUserDoesNotExist_emitsNull() = runTest {
    val repository = FakeUserRepository()

    val observedUser = repository.observeUser("unknown-user").first()

    Assert.assertNull(observedUser)
  }

  @Test(timeout = 30_000)
  fun concurrentCreateUser_onlyOneCallSucceeds() = runBlocking {
    repeat(1_000) {
      val repository = FakeUserRepository()
      val user = User("user-1", "user@example.com", "Test", null, 1_000L)
      val barrier = CyclicBarrier(2)
      val calls =
          List(2) {
            async(Dispatchers.IO) {
              barrier.await(5, TimeUnit.SECONDS)
              repository.createUser(user)
            }
          }
      val results = calls.map { it.await() }
      Assert.assertEquals(1, results.count { it.isSuccess })
      Assert.assertTrue(results.single { it.isFailure }.exceptionOrNull() is IllegalStateException)
      Assert.assertEquals(user, repository.getUser(user.uid))
    }
  }

  @Test(timeout = 30_000)
  fun concurrentSetRole_onlyOneRoleIsAssigned() = runBlocking {
    repeat(1_000) {
      val repository = FakeUserRepository()
      val user = User("user-1", "user@example.com", "Test", null, 1_000L)
      repository.createUser(user)
      val barrier = CyclicBarrier(2)
      val roles = listOf(Role.EXPLORER, Role.VENUE)
      val calls = roles.map { role ->
        async(Dispatchers.IO) {
          barrier.await(5, TimeUnit.SECONDS)
          repository.setRole(user.uid, role)
        }
      }
      val results = calls.map { it.await() }
      Assert.assertEquals(1, results.count { it.isSuccess })
      Assert.assertTrue(results.single { it.isFailure }.exceptionOrNull() is IllegalStateException)
      Assert.assertEquals(
          user.copy(role = roles[results.indexOfFirst { it.isSuccess }]),
          repository.getUser(user.uid),
      )
    }
  }

  @Test(timeout = 30_000)
  fun setRoleRacingCreateUser_resultMatchesStoredRole() = runBlocking {
    repeat(1_000) {
      val repository = FakeUserRepository()
      val user = User("user-1", "user@example.com", "Test", null, 1_000L)
      val barrier = CyclicBarrier(2)
      val creation =
          async(Dispatchers.IO) {
            barrier.await(5, TimeUnit.SECONDS)
            repository.createUser(user)
          }
      val assignment =
          async(Dispatchers.IO) {
            barrier.await(5, TimeUnit.SECONDS)
            repository.setRole(user.uid, Role.EXPLORER)
          }
      Assert.assertTrue(creation.await().isSuccess)
      val result = assignment.await()
      if (result.isSuccess) {
        Assert.assertEquals(user.copy(role = Role.EXPLORER), repository.getUser(user.uid))
      } else {
        Assert.assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        Assert.assertEquals(user, repository.getUser(user.uid))
      }
    }
  }
}
