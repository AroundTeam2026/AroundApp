// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.ui.StatusBanner
import com.github.aroundteam2026.aroundapp.ui.theme.AroundCorners
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles

private val BUTTON_HEIGHT = 44.dp

/**
 * A card at the top of the map that says why the map shows less than usual, with a button that
 * fixes it, as the location banner in Figma: an [icon] tile, a [title] over a [caption], and an
 * [action] button.
 *
 * @param onAction Called when the explorer taps the [action] button, tagged [actionTag].
 * @param actionEnabled Whether the [action] button can be tapped.
 * @param isError Whether something failed: the card is then outlined, and its title and icon are
 *   drawn, in the theme's error colour.
 */
@Composable
internal fun MapBanner(
    @DrawableRes icon: Int,
    title: String,
    caption: String,
    action: String,
    onAction: () -> Unit,
    actionTag: String,
    modifier: Modifier = Modifier,
    actionEnabled: Boolean = true,
    isError: Boolean = false,
) {
  val colors = MaterialTheme.colorScheme
  StatusBanner(icon, title, caption, modifier, isError = isError) {
    Button(
        onAction,
        Modifier.testTag(actionTag).height(BUTTON_HEIGHT),
        enabled = actionEnabled,
        shape = AroundCorners.InsetField,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = colors.tertiary,
                contentColor = colors.onTertiary,
            ),
        contentPadding = PaddingValues(horizontal = AroundSpacing.InsetPadding),
    ) {
      Text(action, style = AroundTextStyles.FieldLabel)
    }
  }
}
