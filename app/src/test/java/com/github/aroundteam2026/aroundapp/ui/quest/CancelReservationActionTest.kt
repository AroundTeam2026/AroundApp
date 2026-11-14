// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.quest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.OfflineHint
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w413dp-h917dp-mdpi")
class CancelReservationActionTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun cancelIsDisabledOfflineAndEnabledAgainOnReconnect() {
    var offline by mutableStateOf(true)
    compose.setContent {
      AroundAppTheme {
        CancelReservationAction(CancelStatus.NONE, "Café Lumière", onCancel = {}, offline = offline)
      }
    }

    compose.onNodeWithTag(C.Tag.CANCEL_RESERVATION).assertIsNotEnabled()
    compose.onNodeWithText("You're offline").assertIsDisplayed()
    compose.onNodeWithText("Cancelling needs a connection.").assertIsDisplayed()

    offline = false

    compose.onNodeWithTag(C.Tag.CANCEL_RESERVATION).assertIsEnabled()
    compose.onNodeWithTag(C.Tag.OFFLINE_HINT).assertDoesNotExist()
  }

  @Test
  fun offlineLeaveRetryShowsOnlyTheConnectionHintUntilReconnected() {
    var offline by mutableStateOf(false)
    compose.setContent {
      AroundAppTheme {
        CancelReservationAction(
            CancelStatus.FAILED,
            "Café Lumière",
            onCancel = {},
            offline = offline,
            leave = true,
        )
      }
    }

    compose.onNodeWithTag(C.Tag.CANCEL_RESERVATION_FAILED).assertIsDisplayed()
    offline = true
    compose.onNodeWithText("Try again").assertIsNotEnabled()
    compose.onNodeWithText("Leaving the party needs a connection.").assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.CANCEL_RESERVATION_FAILED).assertDoesNotExist()

    offline = false
    compose.onNodeWithText("Try again").assertIsEnabled()
    compose.onNodeWithTag(C.Tag.CANCEL_RESERVATION_FAILED).assertIsDisplayed()
  }

  @Test
  fun losingConnectionDuringCancellationShowsOnlyTheInProgressState() {
    var status by mutableStateOf(CancelStatus.CANCELLING)
    var offline by mutableStateOf(false)
    compose.setContent {
      AroundAppTheme {
        CancelReservationAction(status, "Café Lumière", onCancel = {}, offline = offline)
      }
    }

    compose.onNodeWithText("Cancelling…").assertIsDisplayed()
    offline = true
    compose.onNodeWithText("Cancelling…").assertIsNotEnabled()
    compose.onNodeWithTag(C.Tag.OFFLINE_HINT).assertDoesNotExist()

    status = CancelStatus.FAILED
    compose.onNodeWithTag(C.Tag.OFFLINE_HINT).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.CANCEL_RESERVATION_FAILED).assertDoesNotExist()
  }

  @Test
  fun losingConnectionDismissesConfirmationUntilTheUserOpensItAgain() {
    var offline by mutableStateOf(false)
    var cancellations = 0
    compose.setContent {
      AroundAppTheme {
        CancelReservationAction(
            CancelStatus.NONE,
            "Café Lumière",
            onCancel = { cancellations++ },
            offline = offline,
        )
      }
    }

    compose.onNodeWithTag(C.Tag.CANCEL_RESERVATION).performClick()
    compose.onNodeWithTag(C.Tag.CANCEL_RESERVATION_DIALOG).assertIsDisplayed()

    offline = true
    compose.onNodeWithTag(C.Tag.CANCEL_RESERVATION_DIALOG).assertDoesNotExist()

    offline = false
    compose.onNodeWithTag(C.Tag.CANCEL_RESERVATION_DIALOG).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.CANCEL_RESERVATION).performClick()
    compose.onNodeWithTag(C.Tag.CANCEL_RESERVATION_CONFIRM).performClick()
    assertEquals(1, cancellations)
  }

  @Test
  fun confirmationSavedOnlineIsHiddenWhenTheScreenIsRestoredOffline() {
    val restoration = StateRestorationTester(compose)
    // The restored composition gets the new connectivity value before any recomposition.
    var offline = false
    restoration.setContent {
      AroundAppTheme {
        CancelReservationAction(CancelStatus.NONE, "Café Lumière", onCancel = {}, offline = offline)
      }
    }

    compose.onNodeWithTag(C.Tag.CANCEL_RESERVATION).performClick()
    compose.onNodeWithTag(C.Tag.CANCEL_RESERVATION_DIALOG).assertIsDisplayed()

    offline = true
    restoration.emulateSavedInstanceStateRestore()

    compose.onNodeWithTag(C.Tag.OFFLINE_HINT).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.CANCEL_RESERVATION_DIALOG).assertDoesNotExist()
  }

  @Test
  fun sharedHintHasActionNeutralCopy() {
    compose.setContent { AroundAppTheme { OfflineHint() } }

    compose.onNodeWithText("You're offline").assertIsDisplayed()
    compose.onNodeWithText("This action needs a connection.").assertIsDisplayed()
  }
}
