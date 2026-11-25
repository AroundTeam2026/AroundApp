// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import com.google.firebase.messaging.FirebaseMessagingService

/**
 * Receives FCM's events. For now, the phone's new tokens; #637 adds the notifications themselves.
 */
class AroundMessagingService : FirebaseMessagingService() {
  override fun onNewToken(token: String) {
    NotificationTokenRegistrarProvider.registrar.onNewToken(token)
  }
}
