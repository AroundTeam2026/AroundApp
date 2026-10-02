package com.github.aroundteam2026.aroundapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AroundAppThemeTest {
  @get:Rule val composeTestRule = createComposeRule()

  private fun primaryColor(darkTheme: Boolean): Color {
    var primary = Color.Unspecified
    composeTestRule.setContent {
      AroundAppTheme(darkTheme = darkTheme, dynamicColor = false) {
        primary = MaterialTheme.colorScheme.primary
      }
    }
    return primary
  }

  @Test fun staticDarkPaletteWhenDynamicColorOff() = assertEquals(Purple80, primaryColor(true))

  @Test fun staticLightPaletteWhenDynamicColorOff() = assertEquals(Purple40, primaryColor(false))
}
