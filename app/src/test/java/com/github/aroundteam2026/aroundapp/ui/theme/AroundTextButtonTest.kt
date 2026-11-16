// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.theme

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks that the shared text button is readable in both themes and takes another colour. */
@RunWith(AndroidJUnit4::class)
class AroundTextButtonTest {
  @get:Rule val composeTestRule = createComposeRule()

  private fun colors(darkTheme: Boolean): Triple<ButtonColors, ButtonColors, Color> {
    lateinit var result: Triple<ButtonColors, ButtonColors, Color>
    composeTestRule.setContent {
      AroundAppTheme(darkTheme = darkTheme) {
        val error = MaterialTheme.colorScheme.error
        result = Triple(aroundTextButtonColors(), aroundTextButtonColors(error), error)
      }
    }
    return result
  }

  @Test fun lightTextButtonIsPurple() = assertColors(colors(darkTheme = false), AroundColors.Purple)

  @Test fun darkTextButtonIsCream() = assertColors(colors(darkTheme = true), AroundColors.Cream)

  private fun assertColors(colors: Triple<ButtonColors, ButtonColors, Color>, accent: Color) {
    val (default, destructive, error) = colors
    assertEquals(accent, default.contentColor)
    assertEquals(error, destructive.contentColor)
  }
}
