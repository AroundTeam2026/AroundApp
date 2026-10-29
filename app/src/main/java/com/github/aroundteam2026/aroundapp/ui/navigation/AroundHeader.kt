// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme

/**
 * The bar with the Around wordmark at the top of the explorer's tab screens, as in the Figma
 * explorer home (F3): the panel colour, rounded at the bottom, over a soft shadow.
 */
@Composable
fun AroundHeader(modifier: Modifier = Modifier) {
  Box(
      modifier
          .fillMaxWidth()
          .shadow(HEADER_ELEVATION, HEADER_SHAPE, ambientColor = SHADOW, spotColor = SHADOW)
          .background(AroundTheme.colors.panel, HEADER_SHAPE)
          .padding(horizontal = HORIZONTAL_PADDING, vertical = AroundSpacing.Medium)
          .height(ROW_HEIGHT)
          .testTag(C.Tag.AROUND_HEADER),
      contentAlignment = Alignment.Center,
  ) {
    Image(
        painterResource(R.drawable.around_wordmark),
        contentDescription = stringResource(R.string.app_name),
        colorFilter = ColorFilter.tint(AroundTheme.colors.accentText),
        modifier = Modifier.size(WORDMARK_WIDTH, WORDMARK_HEIGHT),
    )
  }
}

private val HEADER_SHAPE = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
private val HEADER_ELEVATION = 8.dp
private val HORIZONTAL_PADDING = 20.dp
private val ROW_HEIGHT = 44.dp
private val WORDMARK_WIDTH = 142.dp
private val WORDMARK_HEIGHT = 34.dp
// Figma's plum shadow at 12 %, the same in light and dark mode, so it isn't a theme colour
private val SHADOW = Color(0x1F1F142E)
