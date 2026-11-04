// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.resources.C
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Tests how [BottomTabScaffold] hands the system bars' insets to the screens it shows. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class BottomTabScaffoldTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  /**
   * A tab screen that pads itself for the system bars, as a full-screen form does: one probe at its
   * top and one at its bottom.
   */
  @Before
  fun launch() {
    composeTestRule.setContent {
      BottomTabScaffold(
          tabs = VenueTab.entries,
          startTab = VenueTab.DASHBOARD,
          navController = rememberNavController(),
          navBarTag = C.Tag.VENUE_NAV_BAR,
          screen = {
            Box(Modifier.fillMaxSize()) {
              Probe(TOP_PROBE, Modifier.align(Alignment.TopStart))
              Probe(BOTTOM_PROBE, Modifier.align(Alignment.BottomStart))
            }
          },
      )
    }
    sendSystemBars()
  }

  @Composable
  private fun Probe(tag: String, modifier: Modifier) =
      Box(modifier.windowInsetsPadding(WindowInsets.safeDrawing).size(1.dp).testTag(tag))

  /** Gives the window a status bar and a navigation bar. */
  private fun sendSystemBars() {
    val insets =
        WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, STATUS_BAR, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, NAV_BAR))
            .build()
    composeTestRule.runOnUiThread {
      val content = composeTestRule.activity.findViewById<ViewGroup>(android.R.id.content)
      ViewCompat.dispatchApplyWindowInsets(content.getChildAt(0), insets)
    }
    composeTestRule.waitForIdle()
  }

  // The scaffold already makes room for the status bar; the screen must not add it again
  @Test
  fun aTabScreenGetsTheStatusBarOnlyOnce() {
    val top = composeTestRule.onNodeWithTag(TOP_PROBE).getUnclippedBoundsInRoot().top

    assertEquals(STATUS_BAR.dp, top)
  }

  // Nor does it add the navigation bar's room above the bottom bar
  @Test
  fun aTabScreenEndsOnTheBottomBar() {
    val probeBottom = composeTestRule.onNodeWithTag(BOTTOM_PROBE).getUnclippedBoundsInRoot().bottom
    val barTop = composeTestRule.onNodeWithTag(C.Tag.VENUE_NAV_BAR).getUnclippedBoundsInRoot().top

    assertEquals(barTop, probeBottom)
  }

  private companion object {
    const val TOP_PROBE = "top_probe"
    const val BOTTOM_PROBE = "bottom_probe"

    // In pixels, which are dp at mdpi
    const val STATUS_BAR = 25
    const val NAV_BAR = 15
  }
}
