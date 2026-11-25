// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

// NOSONAR below: Sonar's S6517 asks for a fun interface, but a fun interface can't have a suspend
// member, so its suggestion doesn't compile
/**
 * Registers this phone with FCM, so callers never depend on Firebase Messaging.
 *
 * The phone's ID doesn't come back from [register]: FCM hands it to
 * [AroundMessagingService.onRegistered], every time [register] is called, even when it hasn't
 * changed.
 */
interface NotificationRegistration { // NOSONAR
  /** Asks FCM for this phone's ID, or fails if FCM can't register it (e.g. no Play services). */
  suspend fun register(): Result<Unit>

  /**
   * The ID this phone is registered under, its Firebase Installation ID, which is what
   * [NotificationTokenRepository] saves for the signed-in user. Works offline once the phone has
   * one; fails if it can't get one (e.g. no Play services).
   */
  suspend fun id(): Result<String>
}
