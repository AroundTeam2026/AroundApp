// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

/** Gives this phone's current FCM token, so callers never depend on Firebase Messaging. */
interface NotificationTokenSource {
  /** The phone's current token, or a failure if FCM can't give one (e.g. no Play services). */
  suspend fun currentToken(): Result<String>
}
