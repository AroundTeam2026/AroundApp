// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Tests that marker styles compare by value. The map keys each pin's image on its style, so a style
 * that isn't equal to the last one, as every new object used to be, redraws every pin.
 */
class MarkerStyleTest {

  @Test
  fun theDefaultStyleIsEqualEachTimeItIsBuilt() {
    assertEquals(MarkerDefaults.style(), MarkerDefaults.style())
  }

  @Test
  fun stylesWithDifferentSizesDiffer() {
    // Equal styles would keep a stale image after a real change
    val bigger = MarkerDefaults.style(dimensions = MarkerDimensions(pinDiameter = 60.dp))

    assertNotEquals(MarkerDefaults.style(), bigger)
  }

  @Test
  fun pinShapesAreEqualWhenTheirPointersAre() {
    assertEquals(PinShape(14.dp), PinShape(14.dp))
    assertEquals(PinShape(14.dp).hashCode(), PinShape(14.dp).hashCode())
    assertNotEquals(PinShape(14.dp), PinShape(16.dp))
  }
}
