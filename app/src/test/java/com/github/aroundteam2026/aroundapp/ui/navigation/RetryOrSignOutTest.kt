// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tests the screen shown when what a signed-in user needs couldn't be read. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w413dp-h917dp-mdpi")
class RetryOrSignOutTest {
  @get:Rule val compose = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  private var retries = 0
  private var signOuts = 0

  private fun show() = compose.setContent {
    AroundAppTheme {
      RetryOrSignOut(
          message = MESSAGE,
          onRetry = { retries++ },
          onSignOut = { signOuts++ },
          modifier = Modifier.testTag(SCREEN),
      )
    }
  }

  @Test
  fun showsTheMessageOnTheTaggedScreen() {
    show()

    compose.onNodeWithTag(SCREEN).assertIsDisplayed()
    compose.onNodeWithText(MESSAGE).assertIsDisplayed()
  }

  @Test
  fun theRetryButtonOnlyRetries() {
    show()

    compose
        .onNodeWithTag(C.Tag.RETRY_OR_SIGN_OUT_RETRY)
        .assertTextEquals(context.getString(R.string.retry_or_sign_out_retry))
        .performClick()

    assertEquals(1, retries)
    assertEquals(0, signOuts)
  }

  @Test
  fun theSignOutButtonOnlySignsOut() {
    show()

    compose
        .onNodeWithTag(C.Tag.RETRY_OR_SIGN_OUT_SIGN_OUT)
        .assertTextEquals(context.getString(R.string.auth_sign_out))
        .performClick()

    assertEquals(0, retries)
    assertEquals(1, signOuts)
  }

  private companion object {
    const val MESSAGE = "Couldn't load this"
    const val SCREEN = "screen"
  }
}
