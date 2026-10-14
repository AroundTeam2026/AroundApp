// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Builds the app's [QuestRepository], so ViewModels never depend on an implementation.
 *
 * Quests live in Firestore, so a quest a venue creates is still there after the app closes, and
 * explorers see it on the map. Every screen shares the one repository.
 */
object QuestRepositoryProvider {
  /** The app's quests, read from and written to Firestore. */
  val repository: QuestRepository by lazy { QuestRepositoryFirestore(Firebase.firestore) }
}
