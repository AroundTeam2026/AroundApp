// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests [GeoBounds.contains], which decides which venues are on screen. */
class GeoBoundsContainsTest {

  private val lausanne = GeoBounds(south = 46.50, west = 6.60, north = 46.55, east = 6.66)

  @Test
  fun holdsAPointInside() {
    assertTrue(Location(46.52, 6.63) in lausanne)
  }

  @Test
  fun holdsPointsOnEveryEdgeAndCorner() {
    // A venue exactly on the screen's edge is on screen
    listOf(
            Location(46.50, 6.63),
            Location(46.55, 6.63),
            Location(46.52, 6.60),
            Location(46.52, 6.66),
            Location(46.50, 6.60),
            Location(46.55, 6.66),
        )
        .forEach { assertTrue("$it should be inside", it in lausanne) }
  }

  @Test
  fun leavesOutPointsNorthOrSouth() {
    assertFalse(Location(46.5501, 6.63) in lausanne)
    assertFalse(Location(46.4999, 6.63) in lausanne)
  }

  @Test
  fun leavesOutPointsEastOrWest() {
    assertFalse(Location(46.52, 6.6601) in lausanne)
    assertFalse(Location(46.52, 6.5999) in lausanne)
  }

  @Test
  fun boundsAcrossTheAntimeridianHoldBothSidesOfTheDateLine() {
    val fiji = GeoBounds(south = -18.0, west = 179.0, north = -17.0, east = -179.0)

    assertTrue(Location(-17.5, 179.5) in fiji)
    assertTrue(Location(-17.5, -179.5) in fiji)
    assertTrue(Location(-17.5, 180.0) in fiji)
    assertTrue(Location(-17.5, -180.0) in fiji)
  }

  @Test
  fun boundsAcrossTheAntimeridianLeaveOutTheRestOfTheWorld() {
    // Read as west..east, these bounds would cover everything but the date line
    val fiji = GeoBounds(south = -18.0, west = 179.0, north = -17.0, east = -179.0)

    assertFalse(Location(-17.5, 0.0) in fiji)
    assertFalse(Location(-17.5, 178.9) in fiji)
    assertFalse(Location(-17.5, -178.9) in fiji)
  }

  @Test
  fun boundsSpanningEveryLongitudeHoldThemAll() {
    // As near a pole, or when the map is zoomed out to the whole world
    val world = GeoBounds(south = -85.0, west = -180.0, north = 85.0, east = 180.0)

    listOf(-180.0, -90.0, 0.0, 90.0, 180.0).forEach {
      assertTrue("longitude $it should be inside", Location(0.0, it) in world)
    }
  }

  @Test
  fun aPointBoundsHoldsOnlyThatPoint() {
    val point = GeoBounds(south = 46.5, west = 6.6, north = 46.5, east = 6.6)

    assertTrue(Location(46.5, 6.6) in point)
    assertFalse(Location(46.5, 6.6001) in point)
  }
}
