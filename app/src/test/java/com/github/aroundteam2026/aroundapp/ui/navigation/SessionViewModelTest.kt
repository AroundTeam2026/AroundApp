// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun restoresExistingSession() =
      runTest(dispatcher) {
        val vm = SessionViewModel(FakeAuthRepository("existing"))
        assertEquals(SessionState.Checking, vm.uiState.value)
        advanceUntilIdle()
        assertEquals(SessionState.SignedIn("existing"), vm.uiState.value)
      }

  @Test
  fun observesSignOut() =
      runTest(dispatcher) {
        val repo = FakeAuthRepository("existing")
        val vm = SessionViewModel(repo)
        advanceUntilIdle()
        repo.signOut()
        advanceUntilIdle()
        assertEquals(SessionState.SignedOut, vm.uiState.value)
      }
}
