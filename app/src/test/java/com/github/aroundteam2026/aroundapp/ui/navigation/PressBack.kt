// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule

/**
 * Presses the system Back button, once the screen has settled: otherwise Back can arrive before the
 * last navigation finished, and leaves the app instead.
 */
fun AndroidComposeTestRule<*, out ComponentActivity>.pressBack() {
  waitForIdle()
  runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
  waitForIdle()
}
