// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.ui.theme.AroundCorners
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme

// The banner's own geometry, from Figma (frame 99:588); its gaps come from AroundSpacing
private val TILE_SIZE = 40.dp
private val ICON_SIZE = 20.dp
private val TEXT_SPACING = 2.dp
private val BUTTON_HEIGHT = 44.dp
private val BANNER_ELEVATION = 8.dp
/** The outline of an error banner, from Figma (frame 4484:9165). */
private val ERROR_BORDER_WIDTH = 1.dp
/** Figma's shadow: warm brown at 16 %. */
private val BANNER_SHADOW = Color(0x296B5947)

/**
 * A card at the top of the map that says why the map shows less than usual, with a button that
 * fixes it, as the location banner in Figma: an [icon] tile, a [title] over a [caption], and an
 * [action] button.
 *
 * @param onAction Called when the explorer taps the [action] button, tagged [actionTag].
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
    isError: Boolean = false,
) {
  val colors = MaterialTheme.colorScheme
  val accent = if (isError) colors.error else colors.onSurface
  val card =
      modifier
          .fillMaxWidth()
          .shadow(
              BANNER_ELEVATION,
              AroundCorners.LocationBanner,
              ambientColor = BANNER_SHADOW,
              spotColor = BANNER_SHADOW,
          )
          .background(colors.surface, AroundCorners.LocationBanner)
  Row(
      (if (isError) card.border(ERROR_BORDER_WIDTH, colors.error, AroundCorners.LocationBanner)
          else card)
          .padding(
              start = AroundSpacing.InsetPadding,
              top = AroundSpacing.Medium,
              end = AroundSpacing.Medium,
              bottom = AroundSpacing.Medium,
          ),
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
        Modifier.size(TILE_SIZE).background(AroundTheme.colors.lavender, AroundCorners.InsetField),
        Alignment.Center,
    ) {
      // Decorative: the title says it
      Icon(
          painterResource(icon),
          contentDescription = null,
          Modifier.size(ICON_SIZE),
          tint = accent,
      )
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TEXT_SPACING)) {
      Text(title, color = accent, style = AroundTextStyles.Subtitle)
      Text(caption, color = colors.onSurfaceVariant, style = AroundTextStyles.Caption)
    }
    Button(
        onAction,
        Modifier.testTag(actionTag).height(BUTTON_HEIGHT),
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
