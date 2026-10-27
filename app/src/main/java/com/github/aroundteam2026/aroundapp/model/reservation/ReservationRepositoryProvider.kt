// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Builds the app's [ReservationRepository], so ViewModels never depend on an implementation.
 *
 * Reservations live in Firestore, so a party's booking is still there after the app closes and its
 * venue sees it. Every screen shares the one repository.
 */
object ReservationRepositoryProvider {
  /** The app's reservations, read from and written to Firestore. */
  val repository: ReservationRepository by lazy {
    ReservationRepositoryFirestore(Firebase.firestore)
  }
}
