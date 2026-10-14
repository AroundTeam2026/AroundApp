// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.venue

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Builds the app's [VenueRepository], so ViewModels never depend on an implementation.
 *
 * Venues live in Firestore, so a venue created or placed in the app is still there after it closes.
 * Every screen shares the one repository.
 */
object VenueRepositoryProvider {
  /** The app's venues, read from and written to Firestore. */
  val repository: VenueRepository by lazy { VenueRepositoryFirestore(Firebase.firestore) }
}
