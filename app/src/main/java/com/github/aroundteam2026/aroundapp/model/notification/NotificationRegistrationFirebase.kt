// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import com.google.android.gms.tasks.Task
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * [NotificationRegistration] backed by [FirebaseMessaging.register], which needs
 * `firebase_messaging_installation_id_enabled` in the manifest, and by [FirebaseInstallations] for
 * the ID FCM registers the phone under.
 *
 * @param messaging the FCM client this phone receives notifications through.
 * @param installations gives this phone's Firebase Installation ID.
 */
class NotificationRegistrationFirebase(
    private val messaging: FirebaseMessaging,
    private val installations: FirebaseInstallations,
) : NotificationRegistration {

  override suspend fun register(): Result<Unit> = attempt { messaging.register() }.map {}

  override suspend fun id(): Result<String> = attempt { installations.id }

  /**
   * Waits for the task [start] returns, returning any exception but a cancellation as a failure.
   */
  private suspend fun <T> attempt(start: () -> Task<T>): Result<T> =
      try {
        Result.success(start().await())
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }
}
