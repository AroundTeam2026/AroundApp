// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class AroundAppThemeTest {
  @get:Rule val composeTestRule = createComposeRule()

  private fun palette(darkTheme: Boolean, dynamicColor: Boolean = false): ColorScheme {
    lateinit var result: ColorScheme
    composeTestRule.setContent {
      AroundAppTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        result = MaterialTheme.colorScheme
      }
    }
    return result
  }

  @Test
  fun lightPaletteMatchesFigma() {
    val colors = palette(false)
    assertEquals(Color(0xFF4D0092), colors.primary)
    assertEquals(Color(0xFFFDFFE8), colors.onPrimary)
    assertEquals(Color(0xFFFDFFE8), colors.onSecondary)
    assertEquals(Color(0xFFFDFFE8), colors.onTertiary)
    assertEquals(Color(0xFFFDFFE8), colors.background)
    assertEquals(Color(0xFFFFFFF6), colors.surface)
    assertEquals(Color(0xFF2D2D2D), colors.onSurface)
    assertEquals(Color(0xFF6E6C66), colors.onSurfaceVariant)
    assertEquals(Color(0xFFE3E5CF), colors.outline)
    assertEquals(Color(0xFF5EA89D), colors.tertiary)
    assertEquals(Color(0xFF62BAAD), colors.secondary)
    assertEquals(Color(0xFFA3312A), colors.error)
    assertEquals(colors.surface, colors.surfaceContainer)
  }

  @Test
  fun darkPaletteMatchesFigma() {
    val colors = palette(true)
    assertEquals(Color(0xFF4D0092), colors.primary)
    assertEquals(Color(0xFFFDFFE8), colors.onPrimary)
    assertEquals(Color(0xFFFDFFE8), colors.onSecondary)
    assertEquals(Color(0xFFFDFFE8), colors.onTertiary)
    assertEquals(Color(0xFF2D2D2D), colors.background)
    assertEquals(Color(0xFF383838), colors.surface)
    assertEquals(Color(0xFFFDFFE8), colors.onSurface)
    assertEquals(Color(0xFFB5B6A6), colors.onSurfaceVariant)
    assertEquals(Color(0xFF474547), colors.outline)
    assertEquals(Color(0xFF5EA89D), colors.tertiary)
    assertEquals(Color(0xFF62BAAD), colors.secondary)
    assertEquals(Color(0xFFE5736B), colors.error)
    assertEquals(colors.surface, colors.surfaceContainer)
  }

  @Test
  fun lightExtendedColorsMatchFigma() {
    val colors = extendedPalette(false)
    assertEquals(Color(0xFF4D0092), colors.accentText)
    assertEquals(Color(0xFFF6F8DE), colors.paper)
    assertEquals(Color(0xFFF3E8FF), colors.cream)
    assertEquals(Color(0xFF3E8C80), colors.sage)
    assertEquals(Color(0xFFDDF1EE), colors.babyBlue)
    assertEquals(Color(0xFF8A7560), colors.mapLabel)
  }

  @Test
  fun darkExtendedColorsMatchFigma() {
    val colors = extendedPalette(true)
    assertEquals(Color(0xFFFDFFE8), colors.accentText)
    assertEquals(Color(0xFF333333), colors.paper)
    assertEquals(Color(0xFF3F2E52), colors.cream)
    assertEquals(Color(0xFF62BAAD), colors.sage)
    assertEquals(Color(0xFF2F4744), colors.babyBlue)
    assertEquals(Color(0xFFC9B8A4), colors.mapLabel)
  }

  private fun extendedPalette(darkTheme: Boolean): AroundThemeColors {
    lateinit var colors: AroundThemeColors
    composeTestRule.setContent {
      AroundAppTheme(darkTheme = darkTheme) { colors = AroundTheme.colors }
    }
    return colors
  }

  @Test
  fun defaultUsesBrandColorsAndProvidesTypographyAndShapes() {
    composeTestRule.setContent {
      AroundAppTheme(darkTheme = false) {
        assertEquals(LightColorScheme, MaterialTheme.colorScheme)
        assertEquals(Typography, MaterialTheme.typography)
        assertEquals(AroundShapes, MaterialTheme.shapes)
        // Smoke test: loading the title and every supported body weight must not crash.
        // Font families, weights and metrics are checked separately below.
        Text("Around", style = MaterialTheme.typography.headlineLarge)
        Text("Explore", style = MaterialTheme.typography.bodyLarge)
        Text("Cafe", style = MaterialTheme.typography.labelMedium)
        Text("Continue", style = MaterialTheme.typography.labelLarge)
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun fontFamiliesContainDesignWeights() {
    assertEquals(
        setOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold),
        (AroundFonts.Body as FontListFontFamily).map { it.weight }.toSet(),
    )
    assertEquals(
        setOf(FontWeight.Medium, FontWeight.Bold),
        (AroundFonts.Title as FontListFontFamily).map { it.weight }.toSet(),
    )
  }

  @Test
  fun sharedTextStylesPreserveFigmaAutoLineHeights() {
    val autoStyles =
        listOf(
            AroundTextStyles.PageTitle,
            AroundTextStyles.OverlayTitle,
            AroundTextStyles.AvatarLarge,
            AroundTextStyles.AvatarSmall,
            AroundTextStyles.FieldValue,
            AroundTextStyles.Body,
            AroundTextStyles.InlineBody,
            AroundTextStyles.Caption,
            AroundTextStyles.Small,
            AroundTextStyles.Button,
            AroundTextStyles.CardTitle,
            AroundTextStyles.Subtitle,
            AroundTextStyles.FieldLabel,
            AroundTextStyles.Helper,
            AroundTextStyles.Badge,
            AroundTextStyles.Chip,
            AroundTextStyles.FilterChip,
            AroundTextStyles.MapLabel,
            AroundTextStyles.MapWaterLabel,
        )
    autoStyles.forEachIndexed { index, style ->
      assertEquals(TextUnit.Unspecified, style.lineHeight)
      assertEquals(
          if (index < 4 || index >= 17) AroundFonts.Title else AroundFonts.Body,
          style.fontFamily,
      )
      val weight =
          when {
            index < 4 -> FontWeight.Bold
            index < 9 -> FontWeight.Normal
            index < 15 -> FontWeight.SemiBold
            else -> FontWeight.Medium
          }
      assertEquals(weight, style.fontWeight)
    }
    assertEquals(
        listOf(30, 22, 20, 13, 16, 15, 14, 13, 12, 16, 17, 15, 14, 13, 12, 14, 13, 11, 13).map {
          it.sp
        },
        autoStyles.map { it.fontSize },
    )
    assertEquals(0.sp, AroundTextStyles.OverlayTitle.letterSpacing)
    assertEquals(0.sp, AroundTextStyles.AvatarLarge.letterSpacing)
    assertEquals(0.sp, AroundTextStyles.AvatarSmall.letterSpacing)
    assertEquals(0.44.sp, AroundTextStyles.MapLabel.letterSpacing)
    assertEquals(3.12.sp, AroundTextStyles.MapWaterLabel.letterSpacing)
  }

  @Test
  fun sharedTextStylesPreserveFigmaFixedLineHeights() {
    val fixedStyles =
        listOf(
            AroundTextStyles.Hero,
            AroundTextStyles.SetupTitle,
            AroundTextStyles.EmptyPageTitle,
            AroundTextStyles.SheetTitle,
            AroundTextStyles.BodyLarge,
            AroundTextStyles.TextArea,
            AroundTextStyles.Description,
            AroundTextStyles.RewardDetails,
            AroundTextStyles.BodyMedium,
        )
    assertEquals(
        listOf(38, 32, 30, 24, 16, 16, 15, 15, 14).map { it.sp },
        fixedStyles.map { it.fontSize },
    )
    assertEquals(
        listOf(41, 35, 33, 26, 24, 23, 22, 21, 20).map { it.sp },
        fixedStyles.map { it.lineHeight },
    )
    fixedStyles.forEachIndexed { index, style ->
      assertEquals(if (index < 4) AroundFonts.Title else AroundFonts.Body, style.fontFamily)
      assertEquals(if (index < 4) FontWeight.Bold else FontWeight.Normal, style.fontWeight)
    }
    assertEquals(
        listOf((-1.14).sp, (-0.96).sp, (-0.9).sp, (-0.72).sp),
        fixedStyles.take(4).map { it.letterSpacing },
    )
  }

  @Test
  fun remainingSharedVariablesAndGeometryMatchFigma() {
    assertEquals(Color(0xFF36006A), AroundColors.PrimaryDark)
    assertEquals(RoundedCornerShape(10.dp), AroundCorners.FilterOption)
    assertEquals(RoundedCornerShape(18.dp), AroundCorners.LocationBanner)
    assertEquals(RoundedCornerShape(32.dp), AroundCorners.Overlay)
    assertEquals(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), AroundCorners.BottomSheet)
    assertEquals(
        listOf(6.dp, 10.dp, 14.dp, 20.dp, 22.dp, 28.dp, 36.dp, 48.dp, 56.dp),
        listOf(
            AroundSpacing.SegmentGap,
            AroundSpacing.CompactGap,
            AroundSpacing.InsetPadding,
            AroundSpacing.SectionGap,
            AroundSpacing.NavigationPadding,
            AroundSpacing.SetupGap,
            AroundSpacing.HeadingGap,
            AroundSpacing.ScreenTop,
            AroundSpacing.HeroTop,
        ),
    )
    assertEquals(48.dp, AroundSpacing.HeroGap)
    assertEquals(18.dp, AroundSpacing.SheetHandleGap)
  }

  @Test
  fun sharedCornersAndInsetsMatchFigma() {
    assertEquals(RoundedCornerShape(14.dp), AroundShapes.small)
    assertEquals(RoundedCornerShape(16.dp), AroundShapes.medium)
    assertEquals(RoundedCornerShape(20.dp), AroundShapes.large)
    assertEquals(RoundedCornerShape(28.dp), AroundShapes.extraLarge)
    assertEquals(RoundedCornerShape(12.dp), AroundCorners.InsetField)
    assertEquals(RoundedCornerShape(9.dp), AroundCorners.SegmentedOption)
    assertEquals(RoundedCornerShape(2.dp), AroundShapes.extraSmall)
    assertEquals(RoundedCornerShape(50), AroundPillShape)
    assertEquals(
        listOf(4.dp, 8.dp, 12.dp, 16.dp, 24.dp),
        listOf(
            AroundSpacing.ExtraSmall,
            AroundSpacing.Small,
            AroundSpacing.Medium,
            AroundSpacing.Large,
            AroundSpacing.ExtraLarge,
        ),
    )
    assertEquals(18.dp, AroundSpacing.CardPadding)
    assertEquals(24.dp, AroundSpacing.ScreenHorizontal)
    assertEquals(32.dp, AroundSpacing.ScreenBottom)
  }

  @Test
  @Config(sdk = [30])
  fun dynamicColorFallsBackToBrandBeforeAndroid12() {
    assertEquals(LightColorScheme, palette(false, dynamicColor = true))
  }

  @Test fun dynamicLightColorsRemainOptIn() = verifyDynamicColors(false)

  @Test fun dynamicDarkColorsRemainOptIn() = verifyDynamicColors(true)

  private fun verifyDynamicColors(darkTheme: Boolean) {
    composeTestRule.setContent {
      val context = LocalContext.current
      val expected =
          if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      AroundAppTheme(darkTheme = darkTheme, dynamicColor = true) {
        val actual = MaterialTheme.colorScheme
        assertEquals(expected.primary, actual.primary)
        assertEquals(expected.onPrimary, actual.onPrimary)
        assertEquals(expected.background, actual.background)
        assertEquals(expected.surface, actual.surface)
        assertEquals(expected.onSurface, actual.onSurface)
      }
    }
  }
}
