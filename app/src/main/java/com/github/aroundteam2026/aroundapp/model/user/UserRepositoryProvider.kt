// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.user

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Builds the app's [UserRepository], so ViewModels never depend on an implementation.
 *
 * Profiles live in Firestore, so a user's role is still there after the app restarts. Every screen
 * shares the one repository.
 */
object UserRepositoryProvider {
  /** The app's user profiles, read from and written to Firestore. */
  val repository: UserRepository by lazy { UserRepositoryFirestore(Firebase.firestore) }
}
