// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Builds the app's [CompletionRepository], so ViewModels never depend on an implementation.
 *
 * Completions live in Firestore, so the venue sees the proof an explorer sends. Every screen shares
 * the one repository.
 */
object CompletionRepositoryProvider {
  /** The app's completions, read from and written to Firestore. */
  val repository: CompletionRepository by lazy { CompletionRepositoryFirestore(Firebase.firestore) }
}
