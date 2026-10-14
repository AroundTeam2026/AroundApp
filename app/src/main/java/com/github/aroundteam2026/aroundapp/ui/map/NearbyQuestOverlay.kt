// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundCorners
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import kotlin.math.roundToInt

// The overlay's own geometry, from Figma (frames 2006:688 and 2015:852)
private val BUTTON_HEIGHT = 48.dp
private val BUTTON_ICON_SIZE = 20.dp
private val BUTTON_START_PADDING = 16.dp
private val BUTTON_END_PADDING = 18.dp
private val GRABBER_WIDTH = 40.dp
private val GRABBER_HEIGHT = 4.dp
private val GRABBER_TOP = 10.dp
private val OVERLAY_PADDING = 18.dp
private val BUTTON_ELEVATION = 4.dp
private val OUTLINE_WIDTH = 1.dp
private val OVERLAY_ELEVATION = 12.dp
// Figma gives the overlay this same plum shadow in light and dark mode, so it isn't a theme colour
private val OVERLAY_SHADOW = Color(0x2E1F142E)

/** How fast a downward fling must be to close the overlay, however short it is. */
private val CLOSING_FLING = 1_000.dp

/**
 * The "Nearby quests" pill, which opens the nearby quests over the map.
 *
 * @param onClick Called when the explorer taps the pill.
 * @param modifier Places the pill; the map puts it in its bottom-end corner.
 */
@Composable
fun NearbyQuestsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  Button(
      onClick,
      modifier.testTag(C.Tag.NEARBY_BUTTON).height(BUTTON_HEIGHT),
      shape = AroundPillShape,
      elevation = ButtonDefaults.buttonElevation(defaultElevation = BUTTON_ELEVATION),
      contentPadding = PaddingValues(start = BUTTON_START_PADDING, end = BUTTON_END_PADDING),
  ) {
    Icon(
        painterResource(R.drawable.ic_address_pin),
        contentDescription = null,
        Modifier.size(BUTTON_ICON_SIZE),
    )
    Text(
        stringResource(R.string.map_nearby_title),
        Modifier.padding(start = AroundSpacing.Small),
        style = AroundTextStyles.Subtitle,
    )
  }
}

/**
 * The nearby quests over the map: the pager on a rounded surface, under a grabber. It isn't modal,
 * so the map stays usable around it.
 *
 * The explorer closes it with the pager's close button, or by dragging it down past a third of its
 * height or flinging it down; a shorter drag springs it back.
 *
 * @param quests The quests to page through, already ordered.
 * @param locationOn Whether the explorer's location is on, so [quests] are closest first.
 * @param onQuestClick Called with a page's quest when the explorer taps the page.
 * @param onViewQuest Called with the shown quest when the explorer taps "View quest".
 * @param onClose Called when the explorer closes the overlay.
 * @param filters The filters [quests] already passed.
 * @param onFiltersChange Called with the filters the explorer picks in the pager.
 * @param modifier Places the overlay; the map puts it along its bottom edge, and the overlay keeps
 *   its own margin inside it.
 */
@Composable
fun NearbyQuestOverlay(
    quests: List<NearbyQuest>,
    locationOn: Boolean,
    onQuestClick: (NearbyQuest) -> Unit,
    onViewQuest: (NearbyQuest) -> Unit,
    onClose: () -> Unit,
    filters: NearbyFilters,
    onFiltersChange: (NearbyFilters) -> Unit,
    modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val closingFling = with(LocalDensity.current) { CLOSING_FLING.toPx() }
  var height by remember { mutableIntStateOf(0) }
  // How far down the explorer has dragged it; it never goes above its place
  var dragged by remember { mutableFloatStateOf(0f) }
  Column(
      modifier
          .padding(AroundSpacing.Large)
          .fillMaxWidth()
          .onSizeChanged { height = it.height }
          // Before the offset, so the drag is measured where the overlay rests: measured on the
          // overlay as it follows the finger, a fling's speed would read as almost nothing
          .draggable(
              rememberDraggableState { delta -> dragged = (dragged + delta).coerceAtLeast(0f) },
              Orientation.Vertical,
              onDragStopped = { velocity ->
                if (dragged > height / 3f || velocity > closingFling) {
                  onClose()
                } else {
                  animate(dragged, 0f) { value, _ -> dragged = value }
                }
              },
          )
          .offset { IntOffset(0, dragged.roundToInt()) }
          .testTag(C.Tag.NEARBY_OVERLAY)
          .shadow(
              OVERLAY_ELEVATION,
              AroundCorners.Overlay,
              ambientColor = OVERLAY_SHADOW,
              spotColor = OVERLAY_SHADOW,
          )
          .background(colors.surface, AroundCorners.Overlay)
          .border(OUTLINE_WIDTH, colors.outline, AroundCorners.Overlay)
          .padding(
              start = OVERLAY_PADDING,
              top = GRABBER_TOP,
              end = OVERLAY_PADDING,
              bottom = OVERLAY_PADDING,
          ),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.SectionGap),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
        Modifier.testTag(C.Tag.NEARBY_GRABBER)
            .size(GRABBER_WIDTH, GRABBER_HEIGHT)
            .background(colors.outline, RoundedCornerShape(GRABBER_HEIGHT / 2))
    )
    NearbyQuestPager(
        quests,
        locationOn,
        onQuestClick,
        onViewQuest,
        onClose = onClose,
        filters = filters,
        onFiltersChange = onFiltersChange,
    )
  }
}
