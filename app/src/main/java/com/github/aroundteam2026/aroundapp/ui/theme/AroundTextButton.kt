// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.theme

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Text button colours that stay readable in both themes: Material's default primary is barely
 * visible on the dark background, so the label uses the accent text colour unless told otherwise.
 *
 * @param contentColor The label colour, e.g. the error colour for a destructive action.
 */
@Composable
fun aroundTextButtonColors(contentColor: Color = AroundTheme.colors.accentText): ButtonColors =
    ButtonDefaults.textButtonColors(contentColor = contentColor)

/**
 * A [TextButton] coloured by [aroundTextButtonColors].
 *
 * @param contentColor The label colour; the accent text colour unless given.
 */
@Composable
fun AroundTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = AroundTheme.colors.accentText,
    content: @Composable RowScope.() -> Unit,
) {
  TextButton(
      onClick = onClick,
      modifier = modifier,
      enabled = enabled,
      colors = aroundTextButtonColors(contentColor),
      content = content,
  )
}
