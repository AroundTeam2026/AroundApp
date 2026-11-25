// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import com.google.firebase.Firebase
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.messaging

/**
 * Builds the app's [NotificationRegistration], so ViewModels never depend on Firebase Messaging.
 * Every caller shares the one registration.
 */
object NotificationRegistrationProvider {
  /** Registers this phone with FCM and gives the ID it is registered under. */
  val registration: NotificationRegistration by lazy {
    NotificationRegistrationFirebase(Firebase.messaging, FirebaseInstallations.getInstance())
  }
}
