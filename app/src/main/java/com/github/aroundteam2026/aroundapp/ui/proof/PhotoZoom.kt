// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.proof

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * The zoom of a photo that fits its screen whole, and the furthest it zooms in. Here "screen" is
 * the area that shows the photo, between the viewer's top bar and its zoom pill.
 */
internal const val MIN_ZOOM = 1f
internal const val MAX_ZOOM = 5f

/**
 * How far a photo, drawn whole and centred in its screen, is zoomed in and moved.
 *
 * @property scale The zoom, from [MIN_ZOOM], where the photo fits, to [MAX_ZOOM].
 * @property offset How far the zoomed photo's centre is from the screen's centre, in pixels.
 */
@Immutable
internal data class PhotoZoom(val scale: Float = MIN_ZOOM, val offset: Offset = Offset.Zero) {

  /**
   * This zoom after a pinch of [zoom] around [centroid] and a drag of [pan], both in the pixels of
   * the [screen] that shows a [photo] of that size. The point under the fingers stays under them,
   * and the photo never moves further than its edges.
   */
  fun transformed(
      centroid: Offset,
      pan: Offset,
      zoom: Float,
      photo: Size,
      screen: Size,
  ): PhotoZoom {
    val newScale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
    val fromCentre = centroid - screen.center
    val moved = (offset - fromCentre) * (newScale / scale) + fromCentre + pan
    return PhotoZoom(newScale, clamped(moved, newScale, photo, screen))
  }
}

/** The size of a [photo] drawn whole in [screen], as `ContentScale.Fit` draws it. */
internal fun fittedSize(photo: Size, screen: Size): Size {
  if (photo.width <= 0f || photo.height <= 0f) return Size.Zero
  val factor = min(screen.width / photo.width, screen.height / photo.height)
  return photo * factor
}

/**
 * [offset], kept so the [photo] zoomed by [scale] still covers the [screen] on each side it's
 * bigger than it, and stays centred on each side it isn't.
 */
private fun clamped(offset: Offset, scale: Float, photo: Size, screen: Size): Offset {
  val shown = fittedSize(photo, screen) * scale
  val maxX = max(0f, (shown.width - screen.width) / 2)
  val maxY = max(0f, (shown.height - screen.height) / 2)
  return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

/** [scale] as the zoom pill says it, with at most one decimal: "1", "1.5", "2". */
internal fun zoomLevel(scale: Float, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(scale)
