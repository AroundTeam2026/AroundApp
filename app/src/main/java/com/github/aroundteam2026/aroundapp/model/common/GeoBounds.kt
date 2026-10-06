// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.common

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * A latitude/longitude rectangle, in degrees.
 *
 * [west] is greater than [east] when the rectangle crosses the antimeridian (longitude ±180°).
 */
data class GeoBounds(val south: Double, val west: Double, val north: Double, val east: Double)

/** Mean radius of the Earth, in meters. */
private const val EARTH_RADIUS_METERS = 6_371_000.0

/**
 * The smallest [GeoBounds] holding every point within [radiusMeters] of this location.
 *
 * When the circle reaches a pole, every longitude is within reach, so the bounds span them all.
 *
 * @throws IllegalArgumentException if [radiusMeters] isn't positive and finite, or this location
 *   isn't on the globe.
 */
fun Location.boundsWithin(radiusMeters: Double): GeoBounds {
  require(radiusMeters > 0 && radiusMeters.isFinite()) {
    "The radius must be positive and finite, was $radiusMeters"
  }
  require(lat in -90.0..90.0 && lng in -180.0..180.0) { "$this is not on the globe" }

  val angularRadius = radiusMeters / EARTH_RADIUS_METERS
  val latitude = Math.toRadians(lat)
  val south = latitude - angularRadius
  val north = latitude + angularRadius
  if (south <= -PI / 2 || north >= PI / 2) {
    return GeoBounds(
        Math.toDegrees(maxOf(south, -PI / 2)),
        -180.0,
        Math.toDegrees(minOf(north, PI / 2)),
        180.0,
    )
  }

  // Widest longitude reached, where a meridian is tangent to the circle
  val halfWidth = Math.toDegrees(asin(sin(angularRadius) / cos(latitude)))
  return GeoBounds(
      Math.toDegrees(south),
      wrapLongitude(lng - halfWidth),
      Math.toDegrees(north),
      wrapLongitude(lng + halfWidth),
  )
}

/** Brings [degrees] back into [-180, 180) after it went past the antimeridian. */
private fun wrapLongitude(degrees: Double) = (degrees + 540.0) % 360.0 - 180.0
