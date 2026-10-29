// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.party

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/** Builds the app's [PartyRepository], so ViewModels never depend on an implementation. */
object PartyRepositoryProvider {
  /** The app's party memberships, read from and written to Firestore. */
  val repository: PartyRepository by lazy { PartyRepositoryFirestore(Firebase.firestore) }
}
