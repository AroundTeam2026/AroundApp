// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import kotlinx.coroutines.CompletableDeferred

/**
 * [NotificationRegistration] for tests without FCM. It counts the [registrations] that succeeded,
 * fails with [failure] when it is set, and holds every call until [gate] completes when it is set,
 * so a test can act while a registration is still running. Like FCM's answer, the ID is handed to
 * the registrar by the test itself, through `onRegistered`.
 */
class FakeNotificationRegistration : NotificationRegistration {
  /** How many calls to [register] succeeded. */
  var registrations = 0
    private set

  /** When set, [register] returns it as its failure. */
  var failure: Throwable? = null

  /** When set, [register] waits for it to complete before answering. */
  var gate: CompletableDeferred<Unit>? = null

  override suspend fun register(): Result<Unit> {
    gate?.await()
    failure?.let {
      return Result.failure(it)
    }
    registrations++
    return Result.success(Unit)
  }
}
