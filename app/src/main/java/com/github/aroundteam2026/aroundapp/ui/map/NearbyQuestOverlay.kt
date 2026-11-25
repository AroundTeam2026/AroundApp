// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundColors
import com.github.aroundteam2026.aroundapp.ui.theme.AroundCorners
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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

/** How far under the map's top the pulled-up sheet stops, from Figma (frame 5488:31562). */
internal val PULLED_UP_TOP = 96.dp

/** How dark the map gets behind the pulled-up sheet, from Figma, in the light and dark themes. */
private const val SCRIM_ALPHA_LIGHT = 0.32f
private const val SCRIM_ALPHA_DARK = 0.5f

/** How far up a sheet let go must be to settle pulled up rather than at rest, with no fling. */
private const val HALFWAY = 0.5f

/** How fast a fling must be to close the overlay, or to pull it up or down, however short it is. */
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
 * The nearby quests over the map: the pager on a rounded sheet, under a grabber. At rest it isn't
 * modal, so the map stays usable around it.
 *
 * Dragged up, from the grabber, the header or the list, the sheet grows until it spans the width,
 * sits on the bottom edge and stops [PULLED_UP_TOP] under the top, as in Figma; only then does the
 * list scroll. The map behind it dims as it grows, and a tap there takes it back to rest. Let go
 * partway, it settles where a fling heads to, or else at rest or pulled up, whichever is nearer.
 * How far it is pulled up survives recreation.
 *
 * Dragged down, the list scrolls back to its top first, then the sheet shrinks back to rest. From
 * rest, the explorer closes it by dragging it down past a third of its height or flinging it down;
 * a shorter drag springs it back. The pager's close button closes it too.
 *
 * @param quests The quests to list, already ordered.
 * @param locationOn Whether the explorer's location is on, so [quests] are closest first.
 * @param onOpenQuest Called with a card's quest when the explorer taps the card.
 * @param onShowOnMap Called with a card's quest when the explorer taps its pin.
 * @param onClose Called when the explorer closes the overlay.
 * @param filters The filters [quests] already passed.
 * @param onFiltersChange Called with the filters the explorer picks in the pager.
 * @param acceptState Where accepting each quest stands; "Accept quest" shows only when given.
 * @param onAccept Called with a card's quest when the explorer taps its "Accept quest".
 * @param modifier Places the overlay over the map's whole area; at rest the sheet keeps its own
 *   margin inside it, along the bottom edge.
 */
@Composable
fun NearbyQuestOverlay(
    quests: List<NearbyQuest>,
    locationOn: Boolean,
    onOpenQuest: (NearbyQuest) -> Unit,
    onShowOnMap: (NearbyQuest) -> Unit,
    onClose: () -> Unit,
    filters: NearbyFilters,
    onFiltersChange: (NearbyFilters) -> Unit,
    modifier: Modifier = Modifier,
    acceptState: NearbyAcceptUiState? = null,
    onAccept: (NearbyQuest) -> Unit = {},
) {
  val colors = MaterialTheme.colorScheme
  val density = LocalDensity.current
  val flingSpeed = with(density) { CLOSING_FLING.toPx() }
  val scope = rememberCoroutineScope()
  val close by rememberUpdatedState(onClose)
  val motion = rememberSaveable(saver = SheetMotion.Saver) { SheetMotion() }
  val settle: suspend (velocity: Float) -> Unit = { velocity ->
    motion.settle(velocity, flingSpeed) { close() }
  }
  val listDrag = remember { ListDrag(motion) { settle(it) } }
  val scrimAlpha = if (AroundTheme.isDark) SCRIM_ALPHA_DARK else SCRIM_ALPHA_LIGHT
  BoxWithConstraints(modifier.fillMaxSize()) {
    val large = with(density) { AroundSpacing.Large.toPx() }
    // How far the sheet's top travels from rest, a margin above the bottom, to pulled up
    val reach =
        (constraints.maxHeight - with(density) { PULLED_UP_TOP.toPx() } - motion.restHeight - large)
            .coerceAtLeast(0f)
    SideEffect { motion.reach = reach }
    if (motion.pulled > 0f) {
      Box(
          Modifier.testTag(C.Tag.NEARBY_SCRIM)
              .fillMaxSize()
              .background(Color.Black.copy(alpha = scrimAlpha * motion.pulled))
              .pointerInput(Unit) { detectTapGestures { scope.launch { motion.lower() } } }
      )
    }
    // The margin around the sheet, which it drops as it is pulled up
    val margin = AroundSpacing.Large * (1f - motion.pulled)
    val shape =
        if (motion.pulled < 1f) AroundCorners.Overlay
        else AroundCorners.Overlay.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
    Column(
        Modifier.align(Alignment.BottomCenter)
            .padding(start = margin, end = margin, bottom = margin)
            .fillMaxWidth()
            .onSizeChanged { if (motion.pulled == 0f) motion.restHeight = it.height }
            // Before the offset, so the drag is measured where the overlay rests: measured on the
            // overlay as it follows the finger, a fling's speed would read as almost nothing
            .draggable(
                rememberDraggableState { motion.drag(it) },
                Orientation.Vertical,
                onDragStopped = { settle(it) },
            )
            .nestedScroll(listDrag)
            .offset { IntOffset(0, motion.dragged.roundToInt()) }
            .testTag(C.Tag.NEARBY_OVERLAY)
            .shadow(
                OVERLAY_ELEVATION,
                shape,
                ambientColor = AroundColors.OverlayShadow,
                spotColor = AroundColors.OverlayShadow,
            )
            .background(colors.surface, shape)
            .border(OUTLINE_WIDTH, colors.outline, shape)
            // The side margin it drops goes inside, so the content keeps its width
            .padding(
                start = OVERLAY_PADDING + AroundSpacing.Large - margin,
                top = GRABBER_TOP,
                end = OVERLAY_PADDING + AroundSpacing.Large - margin,
                bottom = OVERLAY_PADDING * (1f - motion.pulled),
            ),
        verticalArrangement = Arrangement.spacedBy(AroundSpacing.SectionGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
          Modifier.testTag(C.Tag.NEARBY_GRABBER)
              .size(GRABBER_WIDTH, GRABBER_HEIGHT)
              .background(colors.outline, AroundPillShape)
      )
      NearbyQuestPager(
          quests,
          locationOn,
          onOpenQuest,
          onShowOnMap,
          onClose = onClose,
          filters = filters,
          onFiltersChange = onFiltersChange,
          acceptState = acceptState,
          onAccept = onAccept,
          // The list takes the whole travel, plus the bottom margin and padding the sheet drops
          listExtraHeight =
              with(density) { ((reach + large + OVERLAY_PADDING.toPx()) * motion.pulled).toDp() },
      )
    }
  }
}

/**
 * Where the sheet stands: how far it is pulled up from rest, or dragged down below it, never both.
 *
 * @param pulled How far it starts pulled up, from 0 at rest to 1 pulled up.
 * @param restHeight Its height at rest, in pixels, as last measured.
 */
@Stable
private class SheetMotion(pulled: Float = 0f, restHeight: Int = 0) {
  /** How far it is pulled up: 0 at rest, 1 pulled up. */
  var pulled by mutableFloatStateOf(pulled)

  /** Its height at rest, kept so the pulled-up height is known again after recreation. */
  var restHeight by mutableIntStateOf(restHeight)

  /** How far down it is dragged below rest, in pixels. */
  var dragged by mutableFloatStateOf(0f)

  /** How far its top travels from rest to pulled up, in pixels; 0 when it has no room to grow. */
  var reach = 0f

  /**
   * Moves the sheet by [delta] pixels, down when positive, and returns how much of it was used. Up,
   * it comes back from below rest first, then grows until pulled up; down, it shrinks back to rest
   * first, then goes below it.
   */
  fun drag(delta: Float): Float {
    if (delta < 0f) {
      val back = delta.coerceAtLeast(-dragged)
      dragged += back
      val up = (back - delta).coerceAtMost((1f - pulled) * reach)
      if (up > 0f) pulled = (pulled + up / reach).coerceAtMost(1f)
      return back - up
    }
    val down = delta.coerceAtMost(pulled * reach)
    if (down > 0f) pulled = (pulled - down / reach).coerceAtLeast(0f)
    dragged += delta - down
    return delta
  }

  /**
   * Settles the sheet once let go at [velocity]. Below rest, it [close]s past a third of its height
   * or faster than [flingSpeed] down, else springs back. Partly pulled up, it goes where a fling
   * faster than [flingSpeed] heads to, or else to rest or pulled up, whichever is nearer.
   */
  suspend fun settle(velocity: Float, flingSpeed: Float, close: () -> Unit) {
    when {
      dragged > 0f && (dragged > restHeight / 3f || velocity > flingSpeed) -> close()
      dragged > 0f -> animate(dragged, 0f) { value, _ -> dragged = value }
      pulled > 0f && pulled < 1f -> {
        val target =
            when {
              velocity < -flingSpeed -> 1f
              velocity > flingSpeed -> 0f
              pulled < HALFWAY -> 0f
              else -> 1f
            }
        animate(pulled, target) { value, _ -> pulled = value }
      }
    }
  }

  /** Takes it back to rest from pulled up. */
  suspend fun lower() = animate(pulled, 0f) { value, _ -> pulled = value }

  companion object {
    /** Keeps how far it is pulled up, and its height at rest, over recreation. */
    val Saver =
        listSaver<SheetMotion, Float>(
            save = { listOf(it.pulled, it.restHeight.toFloat()) },
            restore = { SheetMotion(it[0], it[1].toInt()) },
        )
  }
}

/**
 * Lets a drag on the list move the sheet as a drag on its grabber does, while the list doesn't need
 * it: a drag up brings the sheet back from below rest and grows it until pulled up before the list
 * scrolls, and a drag down past the list's top shrinks the sheet, then pulls it down.
 *
 * @param motion The sheet to move.
 * @param settle Settles the sheet once let go, given the fling's speed.
 */
private class ListDrag(
    private val motion: SheetMotion,
    private val settle: suspend (velocity: Float) -> Unit,
) : NestedScrollConnection {
  override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
      if (available.y < 0f) Offset(0f, motion.drag(available.y)) else Offset.Zero

  override fun onPostScroll(
      consumed: Offset,
      available: Offset,
      source: NestedScrollSource,
  ): Offset {
    // Down past the list's top, by the finger rather than a fling
    if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
    return Offset(0f, motion.drag(available.y))
  }

  override suspend fun onPreFling(available: Velocity): Velocity {
    // At rest or pulled up, the fling is the list's
    if (motion.dragged == 0f && (motion.pulled == 0f || motion.pulled == 1f)) return Velocity.Zero
    settle(available.y)
    return available
  }
}
