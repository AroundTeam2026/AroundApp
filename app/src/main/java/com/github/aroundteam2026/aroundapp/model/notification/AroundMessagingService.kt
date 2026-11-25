// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import android.annotation.SuppressLint
import com.google.firebase.messaging.FirebaseMessagingService

/**
 * Receives FCM's events. For now, the ID the phone is registered under; #637 adds the notifications
 * themselves.
 */
// Lint wants onNewToken(), but with firebase_messaging_installation_id_enabled on in the manifest,
// FCM answers in onRegistered() instead, and onNewToken() is deprecated
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class AroundMessagingService : FirebaseMessagingService() {
  override fun onRegistered(id: String) {
    NotificationTokenRegistrarProvider.registrar.onRegistered(id)
  }
}
