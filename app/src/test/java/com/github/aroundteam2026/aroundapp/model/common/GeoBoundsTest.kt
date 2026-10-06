// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.common

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoBoundsTest {

  private val radius = 5_000.0

  /** 5 km as an angle on the Earth's surface, in degrees. */
  private val radiusDegrees = Math.toDegrees(radius / 6_371_000.0)

  @Test
  fun atTheEquatorTheRadiusSpansTheSameDegreesEveryWay() {
    val bounds = Location(0.0, 0.0).boundsWithin(radius)

    assertEquals(-radiusDegrees, bounds.south, 1e-9)
    assertEquals(radiusDegrees, bounds.north, 1e-9)
    assertEquals(-radiusDegrees, bounds.west, 1e-9)
    assertEquals(radiusDegrees, bounds.east, 1e-9)
  }

  @Test
  fun fartherFromTheEquatorTheSameRadiusSpansMoreLongitude() {
    // At 60°, a degree of longitude is half as long as at the equator
    val bounds = Location(60.0, 10.0).boundsWithin(radius)

    val latitudeSpan = bounds.north - bounds.south
    val longitudeSpan = bounds.east - bounds.west
    assertEquals(2.0, longitudeSpan / latitudeSpan, 1e-3)
  }

  @Test
  fun everyPointAtTheRadiusIsInside() {
    val places =
        listOf(
            Location(46.5197, 6.6323), // Lausanne
            Location(-33.8688, 151.2093), // Sydney, southern hemisphere
            Location(69.6492, 18.9553), // Tromsø, far north
            Location(0.0, 179.99), // On the antimeridian
        )
    places.forEach { center ->
      val bounds = center.boundsWithin(radius)
      for (bearing in 0 until 360 step 5) {
        val point = center.destination(bearing.toDouble(), radius)
        assertTrue("$point, $bearing° from $center, is outside $bounds", bounds.holds(point))
      }
    }
  }

  @Test
  fun boundsAreNoLargerThanTheRadius() {
    // A bigger box would frame more than 5 km, so the map would zoom out too far
    val center = Location(46.5197, 6.6323)
    val bounds = center.boundsWithin(radius)
    val points = (0 until 3600).map { center.destination(it / 10.0, radius) }

    assertEquals(points.maxOf { it.lat }, bounds.north, 1e-6)
    assertEquals(points.minOf { it.lat }, bounds.south, 1e-6)
    assertEquals(points.maxOf { it.lng }, bounds.east, 1e-6)
    assertEquals(points.minOf { it.lng }, bounds.west, 1e-6)
  }

  @Test
  fun crossingTheAntimeridianWrapsTheLongitudes() {
    val east = Location(10.0, 179.99).boundsWithin(radius)
    assertTrue("west ${east.west} should exceed east ${east.east}", east.west > east.east)
    assertTrue(east.east in -180.0..-179.0)

    val west = Location(10.0, -179.99).boundsWithin(radius)
    assertTrue("west ${west.west} should exceed east ${west.east}", west.west > west.east)
    assertTrue(west.west in 179.0..180.0)
  }

  @Test
  fun reachingAPoleCoversEveryLongitude() {
    // 5 km from these points lies past the pole, so every longitude is within reach
    val north = Location(89.99, 10.0).boundsWithin(radius)
    assertBounds(GeoBounds(89.99 - radiusDegrees, -180.0, 90.0, 180.0), north)

    val south = Location(-89.99, 10.0).boundsWithin(radius)
    assertBounds(GeoBounds(-90.0, -180.0, -89.99 + radiusDegrees, 180.0), south)
  }

  private fun assertBounds(expected: GeoBounds, actual: GeoBounds) {
    assertEquals("south", expected.south, actual.south, 1e-9)
    assertEquals("west", expected.west, actual.west, 1e-9)
    assertEquals("north", expected.north, actual.north, 1e-9)
    assertEquals("east", expected.east, actual.east, 1e-9)
  }

  @Test
  fun rejectsARadiusThatIsNotPositiveAndFinite() {
    val center = Location(46.5197, 6.6323)
    listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
      assertThrows("radius $it", IllegalArgumentException::class.java) { center.boundsWithin(it) }
    }
  }

  @Test
  fun rejectsCoordinatesOffTheGlobe() {
    listOf(
            Location(90.1, 0.0),
            Location(-90.1, 0.0),
            Location(0.0, 180.1),
            Location(Double.NaN, 0.0),
        )
        .forEach {
          assertThrows("$it", IllegalArgumentException::class.java) { it.boundsWithin(radius) }
        }
  }
}

/** Whether [point] lies in these bounds, including when they wrap around the antimeridian. */
private fun GeoBounds.holds(point: Location, tolerance: Double = 1e-9): Boolean {
  val inLatitude = point.lat in (south - tolerance)..(north + tolerance)
  val inLongitude =
      if (west <= east) point.lng in (west - tolerance)..(east + tolerance)
      else point.lng >= west - tolerance || point.lng <= east + tolerance
  return inLatitude && inLongitude
}

/** The point [meters] away from this one, heading [bearing] degrees clockwise from north. */
private fun Location.destination(bearing: Double, meters: Double): Location {
  val distance = meters / 6_371_000.0
  val heading = Math.toRadians(bearing)
  val fromLat = Math.toRadians(lat)
  val toLat = asin(sin(fromLat) * cos(distance) + cos(fromLat) * sin(distance) * cos(heading))
  val toLng =
      Math.toRadians(lng) +
          atan2(
              sin(heading) * sin(distance) * cos(fromLat),
              cos(distance) - sin(fromLat) * sin(toLat),
          )
  val wrapped = (Math.toDegrees(toLng) + 540.0) % 360.0 - 180.0
  return Location(Math.toDegrees(toLat), wrapped)
}
