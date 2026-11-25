// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import kotlinx.coroutines.CompletableDeferred

/**
 * [NotificationRegistration] for tests without FCM. It counts the calls it [started] and the
 * [registrations] that succeeded, fails with [failure] when it is set, and holds every call until
 * [gate] completes when it is set, so a test can act while a registration is still running. Like
 * FCM's answer, the ID is handed to the registrar by the test itself, through `onRegistered`;
 * [phoneId] is what [id] gives, or [idFailure] when it is set.
 */
class FakeNotificationRegistration : NotificationRegistration {
  /** How many calls to [register] started, finished or not. */
  var started = 0
    private set

  /** How many calls to [register] succeeded. */
  var registrations = 0
    private set

  /** When set, [register] returns it as its failure. */
  var failure: Throwable? = null

  /** When set, [register] waits for it to complete before answering. */
  var gate: CompletableDeferred<Unit>? = null

  /** The ID [id] gives. */
  var phoneId: String = DEFAULT_PHONE_ID

  /** When set, [id] returns it as its failure. */
  var idFailure: Throwable? = null

  override suspend fun register(): Result<Unit> {
    started++
    gate?.await()
    failure?.let {
      return Result.failure(it)
    }
    registrations++
    return Result.success(Unit)
  }

  override suspend fun id(): Result<String> =
      idFailure?.let { Result.failure(it) } ?: Result.success(phoneId)

  companion object {
    /** The ID [id] gives until a test sets [phoneId]. */
    const val DEFAULT_PHONE_ID = "phone-installation-id"
  }
}
