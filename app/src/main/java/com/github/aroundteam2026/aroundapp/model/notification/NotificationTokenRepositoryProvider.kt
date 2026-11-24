// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Builds the app's [NotificationTokenRepository], so ViewModels never depend on an implementation.
 */
object NotificationTokenRepositoryProvider {
  /**
   * The repository to use, saving tokens in Firestore where Cloud Functions read them. It keeps no
   * state of its own, so each caller may get a new one.
   */
  fun repository(): NotificationTokenRepository =
      NotificationTokenRepositoryFirestore(Firebase.firestore)
}
