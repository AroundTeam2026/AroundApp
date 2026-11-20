// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.notification

import android.os.Build
import com.github.aroundteam2026.aroundapp.model.notification.FakeNotificationPromptStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPermissionViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val store = FakeNotificationPromptStore()

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  /** A ViewModel as after sign-in, once it has read the earlier answer. */
  private fun TestScope.signIn(
      sdkInt: Int = Build.VERSION_CODES.TIRAMISU,
      granted: Boolean = false,
  ): NotificationPermissionViewModel {
    val model = NotificationPermissionViewModel(store, sdkInt) { granted }
    advanceUntilIdle()
    return model
  }

  @Test
  fun itAsksOnceAfterSignIn() =
      runTest(dispatcher) {
        val first = signIn()
        assertEquals(NotificationPrompt.EXPLAINING, first.prompt.value)

        first.notNow()
        advanceUntilIdle()
        assertEquals(NotificationPrompt.DONE, first.prompt.value)
        assertTrue(store.answered)
        assertEquals(NotificationPrompt.DONE, signIn().prompt.value)
      }

  @Test
  fun itDoesntAskAgainAfterARefusalInAndroidsDialog() =
      runTest(dispatcher) {
        val model = signIn()

        model.allow()
        assertEquals(NotificationPrompt.ASKING, model.prompt.value)
        model.onDialogClosed()
        advanceUntilIdle()

        assertEquals(NotificationPrompt.DONE, model.prompt.value)
        // Android's dialog was refused: still not granted, but answered
        assertEquals(NotificationPrompt.DONE, signIn(granted = false).prompt.value)
      }

  @Test
  fun itDoesntAskBelowAndroid13() =
      runTest(dispatcher) {
        assertEquals(
            NotificationPrompt.DONE,
            signIn(sdkInt = Build.VERSION_CODES.S_V2).prompt.value,
        )
        assertFalse(store.answered)
      }

  @Test
  fun itDoesntAskWhenNotificationsAreAlreadyAllowed() =
      runTest(dispatcher) {
        assertEquals(NotificationPrompt.DONE, signIn(granted = true).prompt.value)
        assertFalse(store.answered)
      }

  @Test
  fun nothingShowsWhileTheEarlierAnswerIsRead() =
      runTest(dispatcher) {
        val model = NotificationPermissionViewModel(store, Build.VERSION_CODES.TIRAMISU) { false }

        assertEquals(NotificationPrompt.CHECKING, model.prompt.value)
        // Answers before the explanation shows are ignored
        model.allow()
        model.notNow()
        assertEquals(NotificationPrompt.CHECKING, model.prompt.value)
      }

  @Test
  fun anAnswerThatCantBeSavedStillClosesTheExplanation() =
      runTest(dispatcher) {
        store.failWrites = true
        val model = signIn()

        model.notNow()
        advanceUntilIdle()

        assertEquals(NotificationPrompt.DONE, model.prompt.value)
        assertFalse(store.answered)
      }
}
