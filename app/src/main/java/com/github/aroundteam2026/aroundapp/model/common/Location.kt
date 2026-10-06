// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.common

/**
 * A geographic point used by all domain models instead of Firestore `GeoPoint` or Maps `LatLng`.
 * Conversion happens inside the repository implementations.
 *
 * @property lat Latitude in degrees.
 * @property lng Longitude in degrees.
 */
data class Location(val lat: Double, val lng: Double)
