// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.model.location

import com.github.aroundteam2026.aroundapp.model.common.Location

/**
 * Gives the device's position. [currentLocation] is approximate, while [currentPreciseLocation]
 * requests a fresh, fine-permission fix for a venue visit.
 */
interface LocationRepository {
  /**
   * The device's current approximate position, or null when it can't be known: the location
   * permission is missing or revoked during the request, location is turned off, location services
   * fail, or no fix is available.
   */
  suspend fun currentLocation(): Location?

  /** A fresh precise position, or null without fine permission or an available fix. */
  suspend fun currentPreciseLocation(): Location?
}
