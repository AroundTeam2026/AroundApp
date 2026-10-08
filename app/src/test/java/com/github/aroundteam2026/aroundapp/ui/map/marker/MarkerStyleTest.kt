// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests that the default marker style compares by value. The map keys each pin's image on its
 * style, so a style that isn't equal to the last one, as every new object used to be, redraws every
 * pin.
 */
class MarkerStyleTest {

  @Test
  fun theDefaultStyleIsEqualEachTimeItIsBuilt() {
    assertEquals(MarkerDefaults.style(), MarkerDefaults.style())
  }
}
