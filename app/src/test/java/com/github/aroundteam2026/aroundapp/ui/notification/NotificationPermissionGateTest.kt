// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.notification

import android.Manifest.permission.POST_NOTIFICATIONS
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.aroundteam2026.aroundapp.model.notification.FakeNotificationPromptStore
import com.github.aroundteam2026.aroundapp.model.notification.NotificationPromptStore
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.completion.FakeActivityResults
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class NotificationPermissionGateTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val store = FakeNotificationPromptStore()

  /** How many times home started; the map asks for location when it does. */
  private var homeStarts = 0

  /** The gate over a "Home" text, as after sign-in on Android 13 with notifications not allowed. */
  private fun show(
      role: Role,
      results: FakeActivityResults = FakeActivityResults(),
      store: NotificationPromptStore = this.store,
  ) {
    val model = NotificationPermissionViewModel(store, Build.VERSION_CODES.TIRAMISU) { false }
    compose.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides results) {
        AroundAppTheme {
          NotificationPermissionGate(role, store, model) {
            DisposableEffect(Unit) {
              homeStarts++
              onDispose {}
            }
            Text("Home")
          }
        }
      }
    }
    compose.waitForIdle()
  }

  @Test
  fun theExplanationShowsAndLeadsToAndroidsDialog() {
    val results = FakeActivityResults(permission = false)
    show(Role.EXPLORER, results)

    compose.onNodeWithText("Know when the venue answers").assertIsDisplayed()
    compose.onNodeWithText("Your reservation is approved or declined").assertIsDisplayed()
    compose.onNodeWithText("Your proof is approved or rejected").assertIsDisplayed()
    assertEquals(0, homeStarts)

    compose.onNodeWithTag(C.Tag.NOTIFICATION_PROMPT_ALLOW).performClick()
    compose.waitForIdle()

    assertEquals(listOf(POST_NOTIFICATIONS), results.inputsOf<RequestPermission>())
    // Android's dialog answered (refused here): home, and the answer is kept
    compose.onNodeWithTag(C.Tag.NOTIFICATION_PROMPT).assertDoesNotExist()
    compose.onNodeWithText("Home").assertIsDisplayed()
    assertEquals(1, homeStarts)
    assertTrue(store.answered)
  }

  @Test
  fun homeDoesntStartWhileAndroidsDialogIsOpen() {
    // Never answers, as while the dialog is on screen
    show(Role.EXPLORER, FakeActivityResults())

    compose.onNodeWithTag(C.Tag.NOTIFICATION_PROMPT_ALLOW).performClick()
    compose.waitForIdle()

    assertEquals(0, homeStarts)
    compose.onNodeWithTag(C.Tag.NOTIFICATION_PROMPT).assertIsDisplayed()
  }

  @Test
  fun homeWaitsUntilTheEarlierAnswerIsRead() {
    val reading = CompletableDeferred<Boolean>()
    val slow =
        object : NotificationPromptStore {
          override suspend fun wasAnswered() = reading.await()

          override suspend fun markAnswered() = Unit
        }
    show(Role.EXPLORER, store = slow)

    assertEquals(0, homeStarts)
    compose.onNodeWithTag(C.Tag.NOTIFICATION_PROMPT).assertDoesNotExist()

    reading.complete(true)
    compose.waitForIdle()

    compose.onNodeWithText("Home").assertIsDisplayed()
    assertEquals(1, homeStarts)
  }

  @Test
  fun notNowGoesHomeWithoutAndroidsDialog() {
    val results = FakeActivityResults(permission = true)
    show(Role.EXPLORER, results)

    compose.onNodeWithTag(C.Tag.NOTIFICATION_PROMPT_NOT_NOW).performClick()
    compose.waitForIdle()

    assertTrue(results.launches.isEmpty())
    compose.onNodeWithText("Home").assertIsDisplayed()
    assertTrue(store.answered)
  }

  @Test
  fun backIsNotNow() {
    show(Role.EXPLORER)

    compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()

    compose.onNodeWithText("Home").assertIsDisplayed()
    assertTrue(store.answered)
  }

  @Test
  fun theVenueHearsAboutWhatPartiesDo() {
    show(Role.VENUE)

    compose.onNodeWithText("Know when parties book").assertIsDisplayed()
    compose.onNodeWithText("A party asks to book one of your quests").assertIsDisplayed()
    compose.onNodeWithText("A party sends a proof to review").assertIsDisplayed()
    compose.onNodeWithText("A party cancels its reservation").assertIsDisplayed()
  }

  @Test
  fun anEarlierAnswerGoesStraightHome() {
    store.answered = true
    show(Role.EXPLORER)

    compose.onNodeWithTag(C.Tag.NOTIFICATION_PROMPT).assertDoesNotExist()
    compose.onNodeWithText("Home").assertIsDisplayed()
  }

  @Test
  fun thePreviewsShowTheExplorerAndTheVenue() {
    compose.setContent {
      NotificationPermissionExplorerPreview()
      NotificationPermissionVenuePreview()
    }

    compose.onNodeWithText("Know when the venue answers").assertExists()
    compose.onNodeWithText("Know when parties book").assertExists()
  }
}
