// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.graphics.Point
import android.graphics.Region
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.github.aroundteam2026.aroundapp.ui.map.marker.MarkerStyle
import kotlin.math.floor
import kotlin.math.roundToInt

/** Both marker images stand on their bottom-centre pointer. */
internal val QuestMarkerAnchor = Offset(0.5f, 1f)

/** Matches a tap against the painted shapes, in the same order as the marker stack. */
internal class QuestMarkerHitTest(
    private val style: MarkerStyle,
    private val displayDensity: Density,
    private val layoutDirection: LayoutDirection,
) {
  private val dimensions = style.dimensions
  private val pin by
      lazy(LazyThreadSafetyMode.NONE) {
        with(displayDensity) {
          val width = dimensions.pinDiameter.roundToPx()
          val height = (dimensions.pinDiameter + dimensions.pointerHeight).roundToPx()
          val path = Path()
          path.addOutline(
              style.pinShape.createOutline(
                  Size(width.toFloat(), height.toFloat()),
                  layoutDirection,
                  displayDensity,
              )
          )
          MarkerHitArea(IntSize(width, height), path)
        }
      }
  private var card: Pair<String, MarkerHitArea>? = null

  /** Records the measured bitmap size of the open card. Its text and chips can wrap. */
  fun recordCardSize(key: String, size: IntSize) {
    if (card?.first == key && card?.second?.size == size) return
    val path =
        with(displayDensity) {
          val pointerHeight = dimensions.pointerHeight.roundToPx()
          val pointerWidth = dimensions.pointerWidth.roundToPx()
          val bodyHeight = size.height - pointerHeight
          if (bodyHeight <= 0 || size.width <= 0) return
          Path().apply {
            val body = Size(size.width.toFloat(), bodyHeight.toFloat())
            addOutline(style.cardShape.createOutline(body, layoutDirection, displayDensity))
            val pointer =
                Path().apply {
                  val pointerSize = Size(pointerWidth.toFloat(), pointerHeight.toFloat())
                  addOutline(
                      style.pointerShape.createOutline(pointerSize, layoutDirection, displayDensity)
                  )
                }
            addPath(
                pointer,
                Offset(
                    ((size.width - pointerWidth) / 2f).roundToInt().toFloat(),
                    bodyHeight.toFloat(),
                ),
            )
          }
        }
    card = key to MarkerHitArea(size, path)
  }

  fun hasCard(key: String): Boolean = card?.first == key

  /** Returns the visible marker at [tap], or null when the tap lands on the map. */
  fun find(
      tap: Offset,
      pins: List<VenuePin>,
      selectedKey: String?,
      project: (VenuePin) -> Point,
  ): VenuePin? {
    val open = pins.firstOrNull { it.key == selectedKey }
    if (open != null) {
      val area = card?.takeIf { it.first == open.key }?.second
      if (area != null && area.contains(tap, project(open))) return open
    }
    return pins.firstOrNull { it.key != selectedKey && pin.contains(tap, project(it)) }
  }
}

private class MarkerHitArea(val size: IntSize, path: Path) {
  private val region =
      Region().apply { setPath(path.asAndroidPath(), Region(0, 0, size.width, size.height)) }

  fun contains(tap: Offset, anchor: Point): Boolean {
    val left = anchor.x - (size.width * QuestMarkerAnchor.x).roundToInt()
    val top = anchor.y - (size.height * QuestMarkerAnchor.y).roundToInt()
    return region.contains(floor(tap.x - left).toInt(), floor(tap.y - top).toInt())
  }
}
