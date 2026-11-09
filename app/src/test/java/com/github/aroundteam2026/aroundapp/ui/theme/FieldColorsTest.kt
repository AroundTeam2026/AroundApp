// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks that the shared fields follow the selected palette without changing disabled defaults. */
@RunWith(AndroidJUnit4::class)
class FieldColorsTest {
  @get:Rule val composeTestRule = createComposeRule()

  private data class Palette(
      val scheme: ColorScheme,
      val field: TextFieldColors,
      val defaultDisabledContainer: Color,
      val defaultErrorContainer: Color,
      val defaultCursor: Color,
      val venueField: TextFieldColors,
  )

  private fun palette(darkTheme: Boolean): Palette {
    lateinit var result: Palette
    composeTestRule.setContent {
      AroundAppTheme(darkTheme = darkTheme) {
        result =
            Palette(
                scheme = MaterialTheme.colorScheme,
                field = aroundFieldColors(),
                defaultDisabledContainer =
                    OutlinedTextFieldDefaults.colors().disabledContainerColor,
                defaultErrorContainer = OutlinedTextFieldDefaults.colors().errorContainerColor,
                defaultCursor = OutlinedTextFieldDefaults.colors().cursorColor,
                // As VenueInitializationScreen passes them
                venueField =
                    aroundFieldColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                        errorContainerColor = Color.Unspecified,
                        focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.onSurface,
                    ),
            )
      }
    }
    return result
  }

  @Test fun lightFieldsUseTheLightPalette() = assertPalette(palette(darkTheme = false))

  @Test fun darkFieldsUseTheDarkPalette() = assertPalette(palette(darkTheme = true))

  private fun assertPalette(palette: Palette) {
    val (scheme, field, defaultDisabled, defaultError, defaultCursor, venueField) = palette
    assertEquals(scheme.surface, field.focusedContainerColor)
    assertEquals(scheme.surface, field.unfocusedContainerColor)
    assertEquals(scheme.surface, field.errorContainerColor)
    assertEquals(scheme.primary, field.focusedIndicatorColor)
    assertEquals(scheme.outline, field.unfocusedIndicatorColor)
    assertEquals(defaultDisabled, field.disabledContainerColor)
    assertEquals(defaultCursor, field.cursorColor)
    assertEquals(field.focusedContainerColor, venueField.focusedContainerColor)
    assertEquals(field.unfocusedContainerColor, venueField.unfocusedContainerColor)
    assertEquals(field.unfocusedIndicatorColor, venueField.unfocusedIndicatorColor)
    assertEquals(scheme.surface, venueField.disabledContainerColor)
    assertEquals(defaultError, venueField.errorContainerColor)
    // Venue setup keeps its focused border and cursor in its text colour
    assertEquals(scheme.onSurface, venueField.focusedIndicatorColor)
    assertEquals(scheme.onSurface, venueField.cursorColor)
  }
}
