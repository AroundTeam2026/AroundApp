// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import com.google.firebase.messaging.FirebaseMessagingService

/**
 * Receives FCM's events. For now, the ID the phone is registered under; #637 adds the notifications
 * themselves.
 */
class AroundMessagingService : FirebaseMessagingService() {
  override fun onRegistered(id: String) {
    NotificationTokenRegistrarProvider.registrar.onRegistered(id)
  }
}
