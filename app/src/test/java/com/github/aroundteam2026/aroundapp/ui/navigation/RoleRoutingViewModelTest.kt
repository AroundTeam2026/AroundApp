// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.lifecycle.viewmodel.CreationExtras
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.user.FakePendingRoleStore
import com.github.aroundteam2026.aroundapp.model.user.FakeUserRepository
import com.github.aroundteam2026.aroundapp.model.user.PendingRole
import com.github.aroundteam2026.aroundapp.model.user.ProfileSetup
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.User
import com.github.aroundteam2026.aroundapp.model.user.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoleRoutingViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val users = FakeUserRepository()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun router(
      auth: FakeAuthRepository,
      users: UserRepository,
      pending: FakePendingRoleStore = FakePendingRoleStore(),
  ) = RoleRoutingViewModel(auth, users, ProfileSetup(auth, users, pending))

  private fun profile(uid: String, role: Role?) = User(uid, "Ada", role, 1L)

  @Test
  fun checksSessionBeforeShowingSignedOut() =
      runTest(dispatcher) {
        val vm = router(FakeAuthRepository(), users)
        assertEquals(RoleSessionState.Checking, vm.uiState.value)
        advanceUntilIdle()
        assertEquals(RoleSessionState.SignedOut, vm.uiState.value)
      }

  @Test
  fun restoresEachSavedRoleWithoutSigningInAgain() =
      runTest(dispatcher) {
        for (role in Role.entries) {
          val uid = role.name
          users.createUser(profile(uid, role)).getOrThrow()
          val vm = router(FakeAuthRepository(uid), users)
          assertEquals(RoleSessionState.Checking, vm.uiState.value)
          advanceUntilIdle()
          assertEquals(RoleSessionState.SignedIn(uid, role), vm.uiState.value)
        }
      }

  @Test
  fun missingProfileAndUnsetRoleRequireSelection() =
      runTest(dispatcher) {
        val vm = router(FakeAuthRepository("user"), users)
        advanceUntilIdle()
        assertEquals(RoleSessionState.SignedIn("user", null), vm.uiState.value)
        users.createUser(profile("user", null)).getOrThrow()
        advanceUntilIdle()
        assertEquals(RoleSessionState.SignedIn("user", null), vm.uiState.value)
        users.setRole("user", Role.VENUE).getOrThrow()
        advanceUntilIdle()
        assertEquals(RoleSessionState.SignedIn("user", Role.VENUE), vm.uiState.value)
      }

  @Test
  fun waitsForProfileBeforeChoosingADestination() =
      runTest(dispatcher) {
        val profiles = MutableSharedFlow<User?>()
        val delayed =
            object : UserRepository by users {
              override fun observeUser(uid: String) = profiles
            }
        val vm = router(FakeAuthRepository("user"), delayed)
        advanceUntilIdle()
        assertEquals(RoleSessionState.Checking, vm.uiState.value)
        profiles.emit(profile("user", Role.EXPLORER))
        advanceUntilIdle()
        assertEquals(RoleSessionState.SignedIn("user", Role.EXPLORER), vm.uiState.value)
      }

  @Test
  fun observesAccountCreationAndSignOut() =
      runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val vm = router(repo, users)
        advanceUntilIdle()
        val uid = repo.signUpWithEmail("ada@around.test", "123456").getOrThrow()
        advanceUntilIdle()
        assertEquals(RoleSessionState.SignedIn(uid, null), vm.uiState.value)
        repo.signOut()
        advanceUntilIdle()
        assertEquals(RoleSessionState.SignedOut, vm.uiState.value)
      }

  @Test
  fun accountChangeCancelsOldProfileAndIgnoresItsLaterUpdates() =
      runTest(dispatcher) {
        val repo = FakeAuthRepository("old")
        val oldProfiles = MutableSharedFlow<User?>()
        val newProfiles = MutableSharedFlow<User?>()
        val observed =
            object : UserRepository by users {
              override fun observeUser(uid: String) = if (uid == "old") oldProfiles else newProfiles
            }
        val vm = router(repo, observed)
        advanceUntilIdle()
        oldProfiles.emit(profile("old", Role.VENUE))
        advanceUntilIdle()
        val uid = repo.signUpWithEmail("new@around.test", "123456").getOrThrow()
        advanceUntilIdle()
        assertEquals(0, oldProfiles.subscriptionCount.value)
        assertEquals(RoleSessionState.Checking, vm.uiState.value)
        oldProfiles.emit(profile("old", Role.EXPLORER))
        advanceUntilIdle()
        assertEquals(RoleSessionState.Checking, vm.uiState.value)
        newProfiles.emit(profile(uid, Role.EXPLORER))
        advanceUntilIdle()
        assertEquals(RoleSessionState.SignedIn(uid, Role.EXPLORER), vm.uiState.value)
      }

  @Test
  fun signOutWhileProfileIsLoadingImmediatelyReturnsToAuth() =
      runTest(dispatcher) {
        val repo = FakeAuthRepository("user")
        val profiles = MutableSharedFlow<User?>()
        val delayed =
            object : UserRepository by users {
              override fun observeUser(uid: String) = profiles
            }
        val vm = router(repo, delayed)
        advanceUntilIdle()
        repo.signOut()
        advanceUntilIdle()
        assertEquals(RoleSessionState.SignedOut, vm.uiState.value)
        assertEquals(0, profiles.subscriptionCount.value)
      }

  @Test
  fun profileFailureShowsErrorAndRetryResolvesSavedRole() =
      runTest(dispatcher) {
        var fails = true
        val failing =
            object : UserRepository by users {
              override fun observeUser(uid: String) = flow {
                if (fails) error("network unavailable")
                emit(profile(uid, Role.VENUE))
              }
            }
        val vm = router(FakeAuthRepository("user"), failing)
        advanceUntilIdle()
        assertEquals(RoleSessionState.ProfileUnavailable("user"), vm.uiState.value)
        fails = false
        vm.retryProfile()
        advanceUntilIdle()
        assertEquals(RoleSessionState.SignedIn("user", Role.VENUE), vm.uiState.value)
      }

  @Test
  fun failedSignInDoesNotUnlockMainFlow() =
      runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val vm = router(repo, users)
        advanceUntilIdle()
        repo.signInWithEmail("ada@around.test", "wrong")
        advanceUntilIdle()
        assertEquals(RoleSessionState.SignedOut, vm.uiState.value)
      }

  @Test
  fun factoryObservesTheSuppliedRepositories() =
      runTest(dispatcher) {
        users.createUser(profile("restored", Role.EXPLORER)).getOrThrow()
        val auth = FakeAuthRepository("restored")
        val vm =
            RoleRoutingViewModel.factory(
                    auth,
                    users,
                    ProfileSetup(auth, users, FakePendingRoleStore()),
                )
                .create(RoleRoutingViewModel::class.java, CreationExtras.Empty)
        advanceUntilIdle()
        assertEquals(RoleSessionState.SignedIn("restored", Role.EXPLORER), vm.uiState.value)
      }

  @Test
  fun restoredPendingChoiceFinishesProfileBeforeRouting() =
      runTest(dispatcher) {
        val pending = FakePendingRoleStore(PendingRole(Role.VENUE, null))
        val vm = router(FakeAuthRepository("restored"), users, pending)
        advanceUntilIdle()
        assertEquals(RoleSessionState.SignedIn("restored", Role.VENUE), vm.uiState.value)
        assertEquals(Role.VENUE, users.getUser("restored")?.role)
        assertEquals(null, pending.pending)
      }

  @Test
  fun failedProfileSaveKeepsChoiceAndRetryFinishesIt() =
      runTest(dispatcher) {
        val pending = FakePendingRoleStore(PendingRole(Role.VENUE, "user"))
        var fails = true
        val failing =
            object : UserRepository by users {
              override suspend fun createUser(user: User): Result<Unit> =
                  if (fails) Result.failure(java.io.IOException("offline"))
                  else users.createUser(user)
            }
        val vm = router(FakeAuthRepository("user"), failing, pending)
        advanceUntilIdle()
        assertEquals(RoleSessionState.ProfileUnavailable("user"), vm.uiState.value)
        assertEquals(Role.VENUE, pending.pending?.role)
        fails = false
        vm.retryProfile()
        advanceUntilIdle()
        assertEquals(RoleSessionState.SignedIn("user", Role.VENUE), vm.uiState.value)
        assertEquals(null, pending.pending)
      }

  @Test
  fun anotherAccountsPendingChoiceDoesNotAssignARole() =
      runTest(dispatcher) {
        val pending = FakePendingRoleStore(PendingRole(Role.VENUE, "other"))
        val vm = router(FakeAuthRepository("user"), users, pending)
        advanceUntilIdle()
        assertEquals(RoleSessionState.SignedIn("user", null), vm.uiState.value)
        assertEquals("other", pending.pending?.uid)
        assertEquals(null, users.getUser("user"))
      }
}
