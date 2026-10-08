// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.common

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A geographic point used by all domain models instead of Firestore `GeoPoint` or Maps `LatLng`.
 * Conversion happens inside the repository implementations.
 *
 * @property lat Latitude in degrees.
 * @property lng Longitude in degrees.
 */
data class Location(val lat: Double, val lng: Double)

/**
 * The distance to [other] along the Earth's surface, in meters, taking the Earth as a sphere of its
 * mean radius: within about half a percent anywhere.
 */
fun Location.distanceTo(other: Location): Double {
  val fromLat = Math.toRadians(lat)
  val toLat = Math.toRadians(other.lat)
  val halfLat = sin((toLat - fromLat) / 2)
  val halfLng = sin(Math.toRadians(other.lng - lng) / 2)
  // The haversine formula, which stays accurate for short distances
  val h = halfLat * halfLat + cos(fromLat) * cos(toLat) * halfLng * halfLng
  return 2 * EARTH_RADIUS_METERS * asin(sqrt(minOf(h, 1.0)))
}

/** Mean radius of the Earth, in meters, for the geometry of this package. */
internal const val EARTH_RADIUS_METERS = 6_371_000.0
