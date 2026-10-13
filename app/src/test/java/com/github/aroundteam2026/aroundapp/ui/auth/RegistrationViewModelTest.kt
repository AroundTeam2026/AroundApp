// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.auth

import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.user.FakePendingRoleStore
import com.github.aroundteam2026.aroundapp.model.user.PendingRole
import com.github.aroundteam2026.aroundapp.model.user.ProfileSetup
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.ScriptedUserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Checks the ordering between durable registration choices and authentication. */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val fakeAuth = FakeAuthRepository()
  private val pending = FakePendingRoleStore()
  private val users = ScriptedUserRepository()
  private val choicesAtAuthentication = mutableListOf<PendingRole?>()
  private val auth =
      object : AuthRepository by fakeAuth {
        override suspend fun signUpWithEmail(email: String, password: String): Result<String> {
          choicesAtAuthentication += pending.pending
          return fakeAuth.signUpWithEmail(email, password)
        }

        override suspend fun signInWithEmail(email: String, password: String): Result<String> {
          choicesAtAuthentication += pending.pending
          return fakeAuth.signInWithEmail(email, password)
        }
      }
  private lateinit var viewModel: AuthViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    viewModel = AuthViewModel(auth, ProfileSetup(auth, users, pending))
    viewModel.onEmailChange(EMAIL)
    viewModel.onPasswordChange(PASSWORD)
    viewModel.onPasswordConfirmationChange(PASSWORD)
  }

  @After fun tearDown() = Dispatchers.resetMain()

  @Test
  fun eachRoleIsRecordedBeforeAuthenticationCanEmitTheNewUid() =
      runTest(dispatcher) {
        for (role in Role.entries) {
          fakeAuth.signOut()
          viewModel.onEmailChange("${role.name.lowercase()}@around.test")
          viewModel.onPasswordChange(PASSWORD)
          viewModel.onPasswordConfirmationChange(PASSWORD)

          viewModel.signUp(role)
          advanceUntilIdle()

          assertEquals(PendingRole(role, null), choicesAtAuthentication.last())
          assertNotNull(fakeAuth.currentUserId.value)
          assertNull(viewModel.uiState.value.error)
        }
        // Resolving the profile belongs to the router, not the form's authentication coroutine.
        assertTrue(users.created.isEmpty())
        assertTrue(users.rolesSet.isEmpty())
      }

  @Test
  fun failedChoiceKeepsTheFormAndRetrySavesItBeforeAuthThenClearsIt() =
      runTest(dispatcher) {
        pending.failSave = true
        viewModel.signUp(Role.VENUE)
        advanceUntilIdle()

        assertTrue(choicesAtAuthentication.isEmpty())
        assertNull(fakeAuth.currentUserId.value)
        assertEquals(
            AuthUiState(EMAIL, PASSWORD, PASSWORD, error = AuthFormError.ACCOUNT_SETUP),
            viewModel.uiState.value,
        )

        pending.failSave = false
        viewModel.signUp(Role.VENUE)
        advanceUntilIdle()

        assertEquals(listOf(PendingRole(Role.VENUE, null)), choicesAtAuthentication)
        assertNotNull(fakeAuth.currentUserId.value)
        assertEquals(AuthUiState(), viewModel.uiState.value)
      }

  @Test
  fun invalidRegistrationDoesNotReplaceAPendingChoice() =
      runTest(dispatcher) {
        val original = PendingRole(Role.VENUE, "existing-user")
        pending.pending = original
        viewModel.onPasswordConfirmationChange("different")

        viewModel.signUp(Role.EXPLORER)
        advanceUntilIdle()

        assertEquals(original, pending.pending)
        assertTrue(choicesAtAuthentication.isEmpty())
      }

  @Test
  fun signInDiscardsAnAbandonedChoiceBeforeAuthenticationCanEmitItsUid() =
      runTest(dispatcher) {
        val uid = fakeAuth.addAccount(EMAIL, PASSWORD)
        pending.pending = PendingRole(Role.VENUE, null)

        viewModel.signIn()
        advanceUntilIdle()

        assertEquals(listOf<PendingRole?>(null), choicesAtAuthentication)
        assertEquals(uid, fakeAuth.currentUserId.value)
        assertNull(pending.pending)
        assertTrue(users.rolesSet.isEmpty())
      }

  @Test
  fun failedDiscardBlocksSignInUntilTheChoiceCanBeCleared() =
      runTest(dispatcher) {
        fakeAuth.addAccount(EMAIL, PASSWORD)
        val abandoned = PendingRole(Role.VENUE, null)
        pending.pending = abandoned
        pending.failNextClear = true

        viewModel.signIn()
        advanceUntilIdle()

        assertTrue(choicesAtAuthentication.isEmpty())
        assertEquals(abandoned, pending.pending)
        assertEquals(AuthFormError.ACCOUNT_SETUP, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)

        viewModel.signIn()
        advanceUntilIdle()

        assertEquals(listOf<PendingRole?>(null), choicesAtAuthentication)
        assertNotNull(fakeAuth.currentUserId.value)
      }

  @Test
  fun signInKeepsAnAccountBoundChoiceForTheRouterToResolve() =
      runTest(dispatcher) {
        val uid = fakeAuth.addAccount(EMAIL, PASSWORD)
        val bound = PendingRole(Role.VENUE, uid)
        pending.pending = bound

        viewModel.signIn()
        advanceUntilIdle()

        assertEquals(listOf(bound), choicesAtAuthentication)
        assertEquals(bound, pending.pending)
        assertEquals(uid, fakeAuth.currentUserId.value)
      }

  @Test
  fun accountSetupBindsTheChoiceWithoutAuthenticatingAndSignalsOnce() =
      runTest(dispatcher) {
        val uid = fakeAuth.signUpWithEmail(EMAIL, PASSWORD).getOrThrow()

        viewModel.completeRegistration(Role.VENUE)
        assertTrue(viewModel.uiState.value.isLoading)
        viewModel.completeRegistration(Role.EXPLORER)
        advanceUntilIdle()

        assertEquals(PendingRole(Role.VENUE, uid), pending.pending)
        assertTrue(choicesAtAuthentication.isEmpty())
        assertTrue(users.created.isEmpty())
        assertTrue(users.rolesSet.isEmpty())
        assertTrue(viewModel.uiState.value.accountSetupComplete)
        assertFalse(viewModel.uiState.value.isLoading)
        viewModel.completeRegistration(Role.EXPLORER)
        advanceUntilIdle()
        assertEquals(PendingRole(Role.VENUE, uid), pending.pending)

        viewModel.onAccountSetupHandled()
        assertFalse(viewModel.uiState.value.accountSetupComplete)
      }

  @Test
  fun accountSetupWithoutASessionRejectsTheChoice() =
      runTest(dispatcher) {
        viewModel.completeRegistration(Role.VENUE)
        advanceUntilIdle()

        assertEquals(AuthFormError.ACCOUNT_SETUP, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.accountSetupComplete)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(pending.pending)
        assertTrue(choicesAtAuthentication.isEmpty())
      }

  private companion object {
    const val EMAIL = "ada@around.test"
    const val PASSWORD = "password123"
  }
}
