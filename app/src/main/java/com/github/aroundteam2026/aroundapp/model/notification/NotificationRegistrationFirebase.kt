// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * [NotificationRegistration] backed by [FirebaseMessaging.register], which needs
 * `firebase_messaging_installation_id_enabled` in the manifest.
 *
 * @param messaging the FCM client this phone receives notifications through.
 */
class NotificationRegistrationFirebase(private val messaging: FirebaseMessaging) :
    NotificationRegistration {

  override suspend fun register(): Result<Unit> =
      try {
        messaging.register().await()
        Result.success(Unit)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }
}
