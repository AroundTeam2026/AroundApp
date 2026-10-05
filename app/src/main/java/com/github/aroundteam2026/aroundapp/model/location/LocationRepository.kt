// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.location

import com.github.aroundteam2026.aroundapp.model.common.Location

/** Gives the device's position. */
interface LocationRepository {
  /**
   * The device's current position, or null when it can't be known: the location permission is
   * missing, location is turned off, or no fix is available.
   */
  suspend fun currentLocation(): Location?
}
