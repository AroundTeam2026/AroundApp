// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests the design's colour tokens and how the theme hands them out. */
@RunWith(AndroidJUnit4::class)
class AroundColorsTest {
  @get:Rule val composeTestRule = createComposeRule()

  private fun colorsIn(darkTheme: Boolean, dynamicColor: Boolean = false): AroundColorScheme {
    var colors: AroundColorScheme? = null
    composeTestRule.setContent {
      AroundAppTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        colors = AroundTheme.colors
      }
    }
    return colors!!
  }

  @Test fun theLightThemeHandsOutTheLightTokens() = assertEquals(LightAroundColors, colorsIn(false))

  @Test fun theDarkThemeHandsOutTheDarkTokens() = assertEquals(DarkAroundColors, colorsIn(true))

  // Material's dynamic colours follow the wallpaper; the brand's must not
  @Test
  fun dynamicColourLeavesTheBrandTokensAlone() {
    assertEquals(LightAroundColors, colorsIn(darkTheme = false, dynamicColor = true))
  }

  @Test
  fun outsideTheThemeTheTokensAreTheLightOnes() {
    var colors: AroundColorScheme? = null
    composeTestRule.setContent { colors = AroundTheme.colors }

    assertEquals(LightAroundColors, colors)
  }

  @Test
  fun theDarkBackgroundsAreDark() {
    with(DarkAroundColors) {
      listOf(paper, surface, bg).forEach {
        assertTrue("$it is too light for dark mode", it.luminance() < 0.05f)
      }
    }
  }

  // The pairs the design puts text on, which WCAG AA wants at 4.5:1 or more
  @Test
  fun everyTextColourIsReadableOnItsBackgroundInBothSchemes() {
    listOf("light" to LightAroundColors, "dark" to DarkAroundColors).forEach { (name, scheme) ->
      with(scheme) {
        listOf(
                "ink on paper" to (ink to paper),
                "ink on surface" to (ink to surface),
                "ink on bg" to (ink to bg),
                "muted on paper" to (muted to paper),
                "muted on surface" to (muted to surface),
                "muted on bg" to (muted to bg),
                "primary on paper" to (primary to paper),
                "primary on surface" to (primary to surface),
                "primary on bg" to (primary to bg),
                "primary on mint" to (primary to mint),
                "on-primary on primary" to (onPrimary to primary),
                "error on surface" to (error to surface),
                "ink on cream" to (ink to cream),
                "ink on mint" to (ink to mint),
                "ink on baby blue" to (ink to babyBlue),
            )
            .forEach { (pair, colors) ->
              val ratio = contrast(colors.first, colors.second)
              assertTrue("$name $pair is only $ratio:1", ratio >= 4.5f)
            }
      }
    }
  }

  private fun contrast(a: Color, b: Color): Float {
    val (lighter, darker) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (lighter + 0.05f) / (darker + 0.05f)
  }
}
