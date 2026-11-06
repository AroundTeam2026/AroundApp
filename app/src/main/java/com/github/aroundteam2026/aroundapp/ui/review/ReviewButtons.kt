// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme

/**
 * The outlined half of a pair of buttons, as Figma's `Button/Secondary`: a purple ring and label,
 * faded while disabled.
 */
@Composable
internal fun RowScope.SecondaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    tag: String,
) {
  val primary = MaterialTheme.colorScheme.primary
  val accent = AroundTheme.colors.accentText
  OutlinedButton(
      onClick = onClick,
      enabled = enabled,
      modifier = Modifier.weight(1f).heightIn(min = BUTTON_MIN_HEIGHT).testTag(tag),
      shape = MaterialTheme.shapes.medium,
      border = BorderStroke(BUTTON_BORDER_WIDTH, if (enabled) primary else primary.disabled()),
      colors =
          ButtonDefaults.outlinedButtonColors(
              contentColor = accent,
              disabledContentColor = accent.disabled(),
          ),
  ) {
    Text(text, style = AroundTextStyles.Button)
  }
}

/**
 * The filled half of a pair of buttons, as Figma's `Button/Primary`, faded while disabled. While
 * [busy], a spinner comes before [text].
 */
@Composable
internal fun RowScope.PrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    tag: String,
    busy: Boolean = false,
) {
  Button(
      onClick = onClick,
      enabled = enabled,
      modifier = Modifier.weight(1f).heightIn(min = BUTTON_MIN_HEIGHT).testTag(tag),
      shape = MaterialTheme.shapes.medium,
      colors =
          ButtonDefaults.buttonColors(
              disabledContainerColor = MaterialTheme.colorScheme.primary.disabled(),
              disabledContentColor = MaterialTheme.colorScheme.onPrimary.disabled(),
          ),
  ) {
    if (busy) {
      CircularProgressIndicator(
          Modifier.size(PROGRESS_SIZE),
          color = LocalContentColor.current,
          strokeWidth = PROGRESS_STROKE,
      )
      Spacer(Modifier.width(AroundSpacing.Small))
    }
    Text(text, style = AroundTextStyles.Button)
  }
}

private fun Color.disabled() = copy(alpha = DISABLED_ALPHA)

private const val DISABLED_ALPHA = 0.4f
private val BUTTON_MIN_HEIGHT = 54.dp
private val BUTTON_BORDER_WIDTH = 1.5.dp
private val PROGRESS_SIZE = 20.dp
private val PROGRESS_STROKE = 2.dp
