// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

import android.content.Context
import androidx.annotation.VisibleForTesting

/**
 * Builds the app's [RefusedQuestWrites], so ViewModels never depend on an implementation. Every
 * screen of a venue gets the same one, as the form that writes a quest and the list that hears the
 * outcome don't share a ViewModel.
 */
object RefusedQuestWritesProvider {
  private var current: Pair<String, RefusedQuestWrites>? = null

  /**
   * The refused quest writes of the venue [venueUid], kept in the app's private preferences.
   * Another venue signing in gets its own, and the previous one's is dropped from memory.
   */
  @Synchronized
  fun refusedWrites(context: Context, venueUid: String): RefusedQuestWrites {
    current
        ?.takeIf { (uid, _) -> uid == venueUid }
        ?.let {
          return it.second
        }
    val writes =
        RefusedQuestWrites(RefusedQuestWritesStoreSharedPrefs(context.applicationContext, venueUid))
    current = venueUid to writes
    return writes
  }

  /**
   * Forgets what is remembered of the venue [venueUid], as when it signs out: another account on
   * this phone mustn't hear of its refusals, nor the venue itself after it signs in again.
   */
  @Synchronized
  fun forget(context: Context, venueUid: String) {
    RefusedQuestWritesStoreSharedPrefs.forget(context.applicationContext, venueUid)
    if (current?.first == venueUid) current = null
  }

  /** Forgets the venue's [RefusedQuestWrites], as a new app process starts with none. */
  @Synchronized
  @VisibleForTesting
  internal fun reset() {
    current = null
  }
}
