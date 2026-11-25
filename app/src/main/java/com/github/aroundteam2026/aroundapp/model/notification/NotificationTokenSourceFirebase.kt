// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * [NotificationTokenSource] backed by [FirebaseMessaging].
 *
 * @param messaging the FCM client whose token this phone receives notifications on.
 */
class NotificationTokenSourceFirebase(private val messaging: FirebaseMessaging) :
    NotificationTokenSource {

  override suspend fun currentToken(): Result<String> =
      try {
        Result.success(messaging.token.await())
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }
}
