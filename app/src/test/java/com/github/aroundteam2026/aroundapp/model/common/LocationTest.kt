// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.common

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests [Location.distanceTo], which the card shows as how far away a venue is. */
class LocationTest {

  private val lausanne = Location(46.5197, 6.6323)
  private val geneva = Location(46.2044, 6.1432)

  @Test
  fun measuresAlongTheEarthsSurface() {
    // Great-circle distance on a sphere of the Earth's mean radius
    assertEquals(51_359.2, lausanne.distanceTo(geneva), 1.0)
  }

  @Test
  fun crossesTheAntimeridianTheShortWay() {
    // A naive longitude difference would go 359 degrees round the other side
    assertEquals(111_194.9, Location(0.0, 179.5).distanceTo(Location(0.0, -179.5)), 1.0)
  }

  @Test
  fun oppositeSidesOfTheEarthAreHalfItsCircumferenceApart() {
    assertEquals(20_015_086.8, Location(0.0, 0.0).distanceTo(Location(0.0, 180.0)), 1.0)
  }
}
