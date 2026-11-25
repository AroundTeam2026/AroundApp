// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

/**
 * [NotificationTokenSource] for tests without FCM: gives [token], or fails with [failure] when it
 * is set.
 */
class FakeNotificationTokenSource(var token: String) : NotificationTokenSource {
  /** When set, [currentToken] returns it as its failure. */
  var failure: Throwable? = null

  override suspend fun currentToken(): Result<String> =
      failure?.let { Result.failure<String>(it) } ?: Result.success(token)
}
