// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import android.annotation.SuppressLint
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives FCM's events: the ID the phone is registered under (#634), and the notifications
 * themselves (#637).
 */
// Lint wants onNewToken(), but with firebase_messaging_installation_id_enabled on in the manifest,
// FCM answers in onRegistered() instead, and onNewToken() is deprecated
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class AroundMessagingService : FirebaseMessagingService() {
  override fun onRegistered(id: String) {
    NotificationTokenRegistrarProvider.registrar.onRegistered(id)
  }

  /**
   * Shows the notification [message] carries. The functions send data-only messages (#632), so this
   * runs whether the app is open or not, and Android shows nothing by itself.
   */
  override fun onMessageReceived(message: RemoteMessage) {
    val notification = AroundNotification.fromData(message.data)
    if (notification == null) {
      // Not its fields: they hold names and quest titles
      Log.w(TAG, "Ignored a message with a missing or unknown field: ${message.data.keys}")
      return
    }
    NotificationPoster(this).post(notification)
  }

  private companion object {
    const val TAG = "AroundMessagingService"
  }
}
