// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.aroundteam2026.aroundapp.ui.map.NearbyQuestPagerDefaults
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tests that the default marker colours come from the theme, so markers follow dark mode. */
@RunWith(RobolectricTestRunner::class)
class MarkerColorsTest {
  @get:Rule val composeTestRule = createComposeRule()

  private fun colors(darkTheme: Boolean): MarkerColors {
    lateinit var result: MarkerColors
    composeTestRule.setContent { AroundAppTheme(darkTheme) { result = MarkerDefaults.colors() } }
    return result
  }

  @Test
  fun inLightModeTheMarkersAreThePurpleTealAndCreamOfTheDesign() {
    assertEquals(
        MarkerColors(
            accent = AroundColors.Purple,
            onAccent = AroundColors.Cream,
            secondary = AroundColors.Tertiary,
            onSecondary = AroundColors.Cream,
            pinRing = AroundColors.Tertiary,
            pinSurface = AroundColors.Cream,
            surface = AroundColors.Paper,
            onSurface = AroundColors.Ink,
            onSurfaceVariant = AroundColors.InkMuted,
            outline = AroundColors.Line,
            area = AroundColors.Purple.copy(alpha = MARKER_AREA_ALPHA),
        ),
        colors(darkTheme = false),
    )
  }

  @Test
  fun inDarkModeTheCardAndThePinTakeTheDarkSurfacesAndInk() {
    assertEquals(
        MarkerColors(
            accent = AroundColors.Purple,
            onAccent = AroundColors.Cream,
            secondary = AroundColors.Tertiary,
            onSecondary = AroundColors.Cream,
            pinRing = AroundColors.Tertiary,
            pinSurface = AroundColors.Ink,
            surface = AroundColors.DarkSurface,
            onSurface = AroundColors.Cream,
            onSurfaceVariant = AroundColors.DarkMuted,
            outline = AroundColors.DarkLine,
            area = AroundColors.Purple.copy(alpha = MARKER_AREA_ALPHA),
        ),
        colors(darkTheme = true),
    )
  }

  @Test
  fun theDefaultStyleHasTheThemesColoursAndIsEqualEachTimeItIsBuilt() {
    lateinit var styles: List<MarkerStyle>
    composeTestRule.setContent {
      AroundAppTheme(darkTheme = true) {
        styles = listOf(MarkerDefaults.style(), MarkerDefaults.style())
      }
    }

    assertEquals(styles[0], styles[1])
    assertEquals(AroundColors.DarkSurface, styles[0].colors.surface)
  }

  @Test
  fun theNearbyPagerTakesTheThemesColoursToo() {
    lateinit var style: MarkerStyle
    composeTestRule.setContent {
      AroundAppTheme(darkTheme = true) { style = NearbyQuestPagerDefaults.style() }
    }

    assertEquals(AroundColors.DarkSurface, style.colors.surface)
    assertEquals(AroundColors.Tertiary, style.colors.secondary)
  }

  @Test
  fun theAreaIsTheThemesPrimaryAtAFixedAlpha() {
    assertEquals(Color(0xFF4D0092).copy(alpha = 0.22f), colors(darkTheme = false).area)
  }
}
