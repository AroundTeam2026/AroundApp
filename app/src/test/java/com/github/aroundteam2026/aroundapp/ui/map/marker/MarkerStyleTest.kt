// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import androidx.compose.ui.graphics.Color
import com.github.aroundteam2026.aroundapp.ui.quest.ChipColors
import com.github.aroundteam2026.aroundapp.ui.quest.QuestAvatarStyle
import com.github.aroundteam2026.aroundapp.ui.quest.QuestChipStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests what the marker style hands the shared quest pieces. That the default style compares by
 * value, which the map needs because it keys each pin's image on it, is tested in
 * [MarkerColorsTest].
 */
class MarkerStyleTest {

  @Test
  fun theChipsAndAvatarTakeTheMarkersColoursTypeAndSizes() {
    // A different colour for every role, so a swapped one shows
    val colors =
        MarkerColors(
            accent = Color(1),
            onAccent = Color(2),
            secondary = Color(3),
            onSecondary = Color(4),
            pinRing = Color(5),
            pinSurface = Color(6),
            surface = Color(7),
            onSurface = Color(8),
            onSurfaceVariant = Color(9),
            outline = Color(10),
            area = Color(11),
        )
    val style = MarkerDefaults.style(colors = colors)
    val dimensions = style.dimensions

    assertEquals(
        QuestChipStyle(
            reward = ChipColors(container = Color(1), content = Color(2)),
            party = ChipColors(container = Color(7), content = Color(8), outline = Color(10)),
            distance = ChipColors(container = Color(3), content = Color(4)),
            textStyle = style.typography.chip,
            shape = style.chipShape,
            spacing = dimensions.chipSpacing,
            horizontalPadding = dimensions.chipHorizontalPadding,
            verticalPadding = dimensions.chipVerticalPadding,
            outlineWidth = dimensions.outlineWidth,
        ),
        style.chipStyle,
    )
    assertEquals(
        QuestAvatarStyle(
            size = dimensions.cardAvatarSize,
            padding = dimensions.cardAvatarPadding,
            background = Color(3),
            content = Color(4),
            shape = style.avatarShape,
            textStyle = style.typography.initials,
        ),
        style.avatarStyle,
    )
  }
}
