// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.ui.theme.AroundColors
import com.github.aroundteam2026.aroundapp.ui.theme.AroundCorners
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme

// Shared banner geometry from the location and offline banners in Figma.
private val TILE_SIZE = 40.dp
private val ICON_SIZE = 20.dp
private val TEXT_SPACING = 2.dp
private val ERROR_BORDER_WIDTH = 1.dp
private val BANNER_SHADOW =
    Shadow(
        radius = 12.dp,
        color = AroundColors.BannerShadow,
        offset = DpOffset(0.dp, 8.dp),
    )

/** A status banner with an icon, title, caption, and an optional trailing action. */
@Composable
internal fun StatusBanner(
    @DrawableRes icon: Int,
    title: String,
    caption: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    iconTint: Color? = null,
    action: (@Composable () -> Unit)? = null,
) {
  val colors = MaterialTheme.colorScheme
  val accent = if (isError) colors.error else colors.onSurface
  val shape = AroundCorners.LocationBanner
  val card =
      modifier.fillMaxWidth().dropShadow(shape, BANNER_SHADOW).background(colors.surface, shape)
  Row(
      (if (isError) card.border(ERROR_BORDER_WIDTH, colors.error, shape) else card).padding(
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
          tint = iconTint ?: accent,
      )
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TEXT_SPACING)) {
      Text(title, color = accent, style = AroundTextStyles.Subtitle)
      Text(caption, color = colors.onSurfaceVariant, style = AroundTextStyles.Caption)
    }
    action?.invoke()
  }
}
