// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tests the app-wide banner that says the device is offline. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w413dp-h917dp-mdpi")
class OfflineBannerTest {
  @get:Rule val compose = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun theBannerSaysTheDeviceIsOfflineAndThatTheAppShowsWhatIsSaved() {
    compose.setContent { AroundAppTheme { OfflineBanner() } }

    compose.onNodeWithTag(C.Tag.OFFLINE_BANNER).assertIsDisplayed()
    compose.onNodeWithText(context.getString(R.string.offline_hint_title)).assertIsDisplayed()
    compose.onNodeWithText(context.getString(R.string.offline_banner_caption)).assertIsDisplayed()
  }

  @Test
  fun theBannerIsNotTakenForAScreensHint() {
    compose.setContent { AroundAppTheme { OfflineBanner() } }

    // A screen's own hint can show with the banner, so tests must tell the two apart
    compose.onAllNodesWithTag(C.Tag.OFFLINE_HINT).assertCountEquals(0)
  }

  @Test
  fun thePreviewShowsTheBanner() {
    compose.setContent { OfflineBannerPreview() }

    compose.onNodeWithTag(C.Tag.OFFLINE_BANNER).assertIsDisplayed()
  }
}
