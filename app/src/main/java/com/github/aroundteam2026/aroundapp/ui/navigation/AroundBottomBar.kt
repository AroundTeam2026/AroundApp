// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundFonts
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme

// The Figma "Bottom bar/Explorer" component's measurements
private val BAR_HEIGHT = 84.dp
private val BAR_TOP_PADDING = 10.dp
private val BAR_BOTTOM_PADDING = 22.dp
private val BAR_SIDE_PADDING = 28.dp
private val HAIRLINE = 1.dp
private val PILL_WIDTH = 56.dp
private val PILL_HEIGHT = 30.dp
private val ICON_SIZE = 22.dp
private val LABEL_GAP = 4.dp

private val LabelStyle =
    TextStyle(fontFamily = AroundFonts.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)

/**
 * The bar of [Tab]s at the bottom of the app, as in the design: each tab's icon in a pill above its
 * label, the [selectedTab]'s pill filled with mint and drawn in the primary green, the others in
 * muted grey. Tapping a tab, even the selected one, calls [onTabClick] with it.
 *
 * The bar is 84dp tall, the design's 22dp of bottom padding included. A system bar taller than that
 * padding replaces it, so the tabs stay above it.
 *
 * @param selectedTab The tab shown, or null when none is.
 * @param windowInsets The system bars to keep clear of; only their bottom and sides count.
 */
@Composable
fun AroundBottomBar(
    selectedTab: Tab?,
    onTabClick: (Tab) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.navigationBars,
) {
  val colors = AroundTheme.colors
  Box(
      modifier
          .testTag(C.Tag.NAV_BAR)
          .selectableGroup()
          .fillMaxWidth()
          .background(colors.surface)
          .drawBehind { drawRect(colors.line, size = Size(size.width, HAIRLINE.toPx())) }
          .windowInsetsPadding(
              windowInsets
                  .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                  .union(WindowInsets(bottom = BAR_BOTTOM_PADDING))
          )
  ) {
    Row(
        Modifier.fillMaxWidth()
            .height(BAR_HEIGHT - BAR_BOTTOM_PADDING)
            .padding(start = BAR_SIDE_PADDING, end = BAR_SIDE_PADDING, top = BAR_TOP_PADDING),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Tab.entries.forEach { tab ->
        TabItem(tab, selected = tab == selectedTab, onClick = { onTabClick(tab) })
      }
    }
  }
}

/** One tab: its icon in a pill, mint when [selected], above its label. */
@Composable
private fun TabItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
  val colors = AroundTheme.colors
  val content = if (selected) colors.primary else colors.muted
  Column(
      Modifier.testTag(tab.tabTag).selectable(selected, onClick = onClick, role = Role.Tab),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(LABEL_GAP),
  ) {
    Box(
        Modifier.testTag(C.Tag.NAV_TAB_PILL)
            .size(PILL_WIDTH, PILL_HEIGHT)
            .background(if (selected) colors.mint else Color.Transparent, RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          painterResource(tab.icon),
          // The label already names the tab, so the icon is decorative
          contentDescription = null,
          modifier = Modifier.testTag(C.Tag.NAV_TAB_ICON).size(ICON_SIZE),
          tint = content,
      )
    }
    Text(
        stringResource(tab.label),
        Modifier.testTag(C.Tag.NAV_TAB_LABEL),
        color = content,
        style = LabelStyle,
        maxLines = 1,
    )
  }
}
