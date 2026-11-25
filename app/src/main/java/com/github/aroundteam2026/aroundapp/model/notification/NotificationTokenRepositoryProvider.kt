// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Builds the app's [NotificationTokenRepository], so ViewModels never depend on an implementation.
 *
 * Tokens live in Firestore, where Cloud Functions read them to notify the user. Every caller shares
 * the one repository.
 */
object NotificationTokenRepositoryProvider {
  /** The phone's notification tokens, saved in Firestore. */
  val repository: NotificationTokenRepository by lazy {
    NotificationTokenRepositoryFirestore(Firebase.firestore)
  }
}
