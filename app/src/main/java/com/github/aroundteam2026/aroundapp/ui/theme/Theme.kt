// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography as MaterialTypography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * Provides shared typography, shapes and the light or dark brand palette.
 *
 * Opting into dynamic colors on Android 12+ changes Material colors only. [AroundTheme.colors]
 * continues to provide the brand palette for the selected light or dark mode.
 */
@Composable
fun AroundAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Opt in to wallpaper colors on Android 12+; defaults follow the Figma palette.
    dynamicColor: Boolean = false,
    typography: MaterialTypography = Typography,
    content: @Composable () -> Unit,
) {
  val colorScheme =
      when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
          val context = LocalContext.current
          if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
      }

  CompositionLocalProvider(
      LocalAroundThemeColors provides if (darkTheme) DarkThemeColors else LightThemeColors
  ) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = AroundShapes,
        content = content,
    )
  }
}
