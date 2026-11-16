// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the label colour inside the shared text button, by default and when overridden. */
@RunWith(AndroidJUnit4::class)
class AroundTextButtonTest {
  @get:Rule val composeTestRule = createComposeRule()

  private data class Labels(val default: Color, val destructive: Color, val error: Color)

  private fun labels(darkTheme: Boolean): Labels {
    var default = Color.Unspecified
    var destructive = Color.Unspecified
    var error = Color.Unspecified
    composeTestRule.setContent {
      AroundAppTheme(darkTheme = darkTheme) {
        error = MaterialTheme.colorScheme.error
        Column {
          AroundTextButton(onClick = {}) { default = LocalContentColor.current }
          AroundTextButton(onClick = {}, contentColor = error) {
            destructive = LocalContentColor.current
          }
        }
      }
    }
    composeTestRule.waitForIdle()
    return Labels(default, destructive, error)
  }

  @Test fun lightLabelIsPurple() = assertLabels(labels(darkTheme = false), AroundColors.Purple)

  @Test fun darkLabelIsCream() = assertLabels(labels(darkTheme = true), AroundColors.Cream)

  private fun assertLabels(labels: Labels, accent: Color) {
    assertEquals(accent, labels.default)
    assertEquals(labels.error, labels.destructive)
  }
}
