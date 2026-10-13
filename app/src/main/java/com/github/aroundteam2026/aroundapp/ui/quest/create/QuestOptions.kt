// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme

/** A card that shows whether it is [selected], in the light purple of a chosen option. */
@Composable
internal fun SelectableOption(
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    shape: Shape,
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
      selected = selected,
      onClick = onClick,
      shape = shape,
      color =
          if (selected) MaterialTheme.colorScheme.primaryContainer
          else MaterialTheme.colorScheme.surface,
      border =
          if (selected) BorderStroke(SELECTED_BORDER, MaterialTheme.colorScheme.primary)
          else BorderStroke(BORDER, MaterialTheme.colorScheme.outline),
      modifier = modifier.testTag(tag),
  ) {
    Column(
        Modifier.padding(padding),
        verticalArrangement = Arrangement.spacedBy(OPTION_TEXT_GAP),
        horizontalAlignment = horizontalAlignment,
    ) {
      content()
    }
  }
}

/** The title and hint of a [SelectableOption], in the accent colour when it is selected. */
@Composable
internal fun OptionText(
    title: String,
    hint: String,
    selected: Boolean,
    hintStyle: TextStyle = AroundTextStyles.Small,
) {
  val accent = AroundTheme.colors.accentText
  Text(
      title,
      style = AroundTextStyles.Subtitle,
      color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
  )
  Text(
      hint,
      style = hintStyle,
      color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/** The gap between an option's title and hint. */
internal val OPTION_TEXT_GAP: Dp = 2.dp

private val BORDER: Dp = 1.dp
private val SELECTED_BORDER: Dp = 2.dp
