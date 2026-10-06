// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.location

import com.github.aroundteam2026.aroundapp.model.common.Location

/**
 * Gives the device's approximate position, to about a city block: enough to show what's around the
 * explorer, but not to check that they visit a venue or enter a geofence.
 */
interface LocationRepository {
  /**
   * The device's current approximate position, or null when it can't be known: the location
   * permission is missing or revoked during the request, location is turned off, location services
   * fail, or no fix is available.
   */
  suspend fun currentLocation(): Location?
}
