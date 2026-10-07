// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.auth

import androidx.lifecycle.viewmodel.CreationExtras
import com.github.aroundteam2026.aroundapp.model.auth.AuthError
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private lateinit var repo: FakeAuthRepository
  private lateinit var vm: AuthViewModel

  @Before
  fun setUp() {
    // viewModelScope runs on Dispatchers.Main, which unit tests must replace.
    Dispatchers.setMain(dispatcher)
    repo = FakeAuthRepository()
    vm = AuthViewModel(repo)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun fill(email: String, password: String) {
    vm.onEmailChange(email)
    vm.onPasswordChange(password)
  }

  @Test
  fun signInWithValidCredentialsSignsTheUserIn() =
      runTest(dispatcher) {
        val uid = repo.addAccount("ada@around.test", PASSWORD)
        fill("ada@around.test", PASSWORD)
        vm.signIn()
        advanceUntilIdle()
        assertEquals(uid, repo.currentUserId.value)
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.error)
      }

  @Test
  fun signUpWithValidInputCreatesAnAccountAndSignsIn() =
      runTest(dispatcher) {
        fill("ada@around.test", PASSWORD)
        vm.signUp()
        advanceUntilIdle()
        assertTrue(repo.currentUserId.value != null)
        assertNull(vm.uiState.value.error)
      }

  @Test
  fun invalidEmailIsRejectedWithoutCallingTheRepository() =
      runTest(dispatcher) {
        // If the repository were called, it would consume this error.
        repo.nextError = AuthError.Network
        fill("not-an-email", PASSWORD)
        vm.signIn()
        advanceUntilIdle()
        assertEquals(AuthFormError.INVALID_EMAIL, vm.uiState.value.error)
        assertEquals(AuthError.Network, repo.nextError)
      }

  @Test
  fun signInWithEmptyPasswordIsRejectedWithoutCallingTheRepository() =
      runTest(dispatcher) {
        repo.nextError = AuthError.Network
        fill("ada@around.test", "")
        vm.signIn()
        advanceUntilIdle()
        assertEquals(AuthFormError.EMPTY_PASSWORD, vm.uiState.value.error)
        assertEquals(AuthError.Network, repo.nextError)
      }

  @Test
  fun signUpWithShortPasswordIsRejectedWithoutCallingTheRepository() =
      runTest(dispatcher) {
        repo.nextError = AuthError.Network
        fill("ada@around.test", "12345")
        vm.signUp()
        advanceUntilIdle()
        assertEquals(AuthFormError.PASSWORD_TOO_SHORT, vm.uiState.value.error)
        assertEquals(AuthError.Network, repo.nextError)
      }

  @Test
  fun wrongPasswordShowsWrongCredentialsAndKeepsTheForm() =
      runTest(dispatcher) {
        repo.addAccount("ada@around.test", PASSWORD)
        fill("ada@around.test", "wrong-password")
        vm.signIn()
        advanceUntilIdle()
        assertEquals(AuthFormError.WRONG_CREDENTIALS, vm.uiState.value.error)
        assertEquals("ada@around.test", vm.uiState.value.email)
        assertNull(repo.currentUserId.value)
      }

  @Test
  fun takenEmailShowsEmailTaken() =
      runTest(dispatcher) {
        repo.addAccount("ada@around.test", PASSWORD)
        fill("ada@around.test", PASSWORD)
        vm.signUp()
        advanceUntilIdle()
        assertEquals(AuthFormError.EMAIL_TAKEN, vm.uiState.value.error)
      }

  @Test
  fun networkFailureShowsNetworkError() =
      runTest(dispatcher) {
        repo.nextError = AuthError.Network
        fill("ada@around.test", PASSWORD)
        vm.signUp()
        advanceUntilIdle()
        assertEquals(AuthFormError.NETWORK, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
      }

  @Test
  fun isLoadingWhileTheRequestRuns() =
      runTest(dispatcher) {
        fill("ada@around.test", PASSWORD)
        vm.signUp()
        assertTrue(vm.uiState.value.isLoading)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
      }

  @Test
  fun secondSubmitWhileLoadingIsIgnored() =
      runTest(dispatcher) {
        // Only the first call fails; a second call would succeed and clear the error.
        repo.nextError = AuthError.Network
        fill("ada@around.test", PASSWORD)
        vm.signUp()
        vm.signUp()
        advanceUntilIdle()
        assertEquals(AuthFormError.NETWORK, vm.uiState.value.error)
        assertNull(repo.currentUserId.value)
      }

  @Test
  fun typingClearsThePreviousError() =
      runTest(dispatcher) {
        fill("not-an-email", PASSWORD)
        vm.signIn()
        vm.onEmailChange("ada@around.test")
        assertNull(vm.uiState.value.error)
      }

  @Test
  fun typingAPasswordClearsThePreviousError() =
      runTest(dispatcher) {
        fill("ada@around.test", "")
        vm.signIn()
        vm.onPasswordChange(PASSWORD)
        assertNull(vm.uiState.value.error)
      }

  @Test
  fun signUpWithInvalidEmailIsRejectedWithoutCallingTheRepository() =
      runTest(dispatcher) {
        repo.nextError = AuthError.Network
        fill("not-an-email", PASSWORD)
        vm.signUp()
        advanceUntilIdle()
        assertEquals(AuthFormError.INVALID_EMAIL, vm.uiState.value.error)
        assertEquals(AuthError.Network, repo.nextError)
      }

  @Test
  fun emailWithSurroundingSpacesIsAccepted() =
      runTest(dispatcher) {
        val uid = repo.addAccount("ada@around.test", PASSWORD)
        fill("  ada@around.test ", PASSWORD)
        vm.signIn()
        advanceUntilIdle()
        assertEquals(uid, repo.currentUserId.value)
      }

  @Test
  fun repositoryInvalidEmailShowsInvalidEmail() =
      runTest(dispatcher) {
        // The repository can be stricter than the local check, e.g. Firebase.
        repo.nextError = AuthError.InvalidEmail
        fill("ada@around.test", PASSWORD)
        vm.signIn()
        advanceUntilIdle()
        assertEquals(AuthFormError.INVALID_EMAIL, vm.uiState.value.error)
      }

  @Test
  fun repositoryWeakPasswordShowsPasswordTooShort() =
      runTest(dispatcher) {
        repo.nextError = AuthError.WeakPassword
        fill("ada@around.test", PASSWORD)
        vm.signUp()
        advanceUntilIdle()
        assertEquals(AuthFormError.PASSWORD_TOO_SHORT, vm.uiState.value.error)
      }

  @Test
  fun unexpectedRepositoryErrorShowsUnknown() =
      runTest(dispatcher) {
        repo.nextError = AuthError.Unknown(IllegalStateException("boom"))
        fill("ada@around.test", PASSWORD)
        vm.signIn()
        advanceUntilIdle()
        assertEquals(AuthFormError.UNKNOWN, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
      }

  @Test
  fun factoryCreatesAViewModelBackedByTheGivenRepository() =
      runTest(dispatcher) {
        val created =
            AuthViewModel.factory(repo).create(AuthViewModel::class.java, CreationExtras.Empty)
        assertNotNull(created)
        val uid = repo.addAccount("ada@around.test", PASSWORD)
        created.onEmailChange("ada@around.test")
        created.onPasswordChange(PASSWORD)
        created.signIn()
        advanceUntilIdle()
        assertEquals(uid, repo.currentUserId.value)
      }

  private companion object {
    const val PASSWORD = "password123"
  }
}
