// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * A map pin: a circle as wide as the shape, with a triangle below it whose tip is the bottom
 * centre. The circle takes the top of the shape, so the shape must be at least as tall as it is
 * wide; the triangle fills the rest.
 *
 * Pin shapes with the same pointer are equal, so a style holding one stays equal to the next.
 *
 * @param pointerWidth Width of the triangle where it meets the circle; wider is narrowed to the
 *   circle's width, and none leaves the circle alone.
 */
data class PinShape(private val pointerWidth: Dp) : Shape {
  override fun createOutline(
      size: Size,
      layoutDirection: LayoutDirection,
      density: Density,
  ): Outline {
    val radius = size.width / 2
    val halfBase = minOf(with(density) { pointerWidth.toPx() } / 2, radius)
    // Without a pointer the arc below would be a full turn, which draws nothing
    if (halfBase <= 0f)
        return Outline.Generic(Path().apply { addOval(Rect(0f, 0f, size.width, size.width)) })
    // The triangle's sides start where a chord halfBase from the centre line meets the circle
    val baseBelowCentre = sqrt(radius * radius - halfBase * halfBase)
    // Angles run clockwise from the right, as y grows downwards
    val baseAngle = Math.toDegrees(atan2(baseBelowCentre, halfBase).toDouble()).toFloat()
    val path =
        Path().apply {
          // One outline, from the triangle's right corner over the top to its left corner, then
          // down to the tip: overlapping a separate circle and triangle could leave a hole
          arcTo(
              rect = Rect(0f, 0f, size.width, size.width),
              startAngleDegrees = baseAngle,
              sweepAngleDegrees = -(180f + 2 * baseAngle),
              forceMoveTo = true,
          )
          lineTo(radius, size.height)
          close()
        }
    return Outline.Generic(path)
  }
}

/** A triangle pointing down, filling its bounds: the card's pointer. */
val PointerShape: Shape = GenericShape { size, _ ->
  moveTo(0f, 0f)
  lineTo(size.width, 0f)
  lineTo(size.width / 2, size.height)
  close()
}
