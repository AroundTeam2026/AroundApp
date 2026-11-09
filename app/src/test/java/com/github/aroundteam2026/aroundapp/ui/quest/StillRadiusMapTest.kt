// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class StillRadiusMapTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun theStillMapIsDescribedAndDrawsItsOverlay() {
    compose.setContent {
      AroundAppTheme {
        StillRadiusMap(Location(46.5220, 6.6330), 200, "Within 200 m") { Text("Overlay") }
      }
    }

    compose.onNodeWithContentDescription("Within 200 m").assertExists()
    compose.onNodeWithText("Overlay", useUnmergedTree = true).assertExists()
  }
}
