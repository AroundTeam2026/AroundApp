// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.github.aroundteam2026.aroundapp.model.PerUser

/**
 * Builds the app's [RefusedQuestWrites], so ViewModels never depend on an implementation. Every
 * screen of a venue gets the same one, as any screen that hears the venue's quests should judge the
 * same writes.
 */
object RefusedQuestWritesProvider {
  private val perVenue = PerUser { context, venueUid ->
    RefusedQuestWrites(RefusedQuestWritesStoreSharedPrefs(context, venueUid))
  }

  /**
   * The refused quest writes of the venue [venueUid], kept in the app's private preferences.
   * Another venue signing in gets its own, and the previous one's is dropped from memory.
   */
  fun refusedWrites(context: Context, venueUid: String): RefusedQuestWrites =
      perVenue.get(context, venueUid)

  /**
   * Forgets what is remembered of the venue [venueUid], as when it signs out: another account on
   * this phone mustn't hear of its refusals, nor the venue itself after it signs in again.
   */
  fun forget(context: Context, venueUid: String) {
    RefusedQuestWritesStoreSharedPrefs.forget(context.applicationContext, venueUid)
    perVenue.forget(venueUid)
  }

  /** Forgets the venue's [RefusedQuestWrites], as a new app process starts with none. */
  @VisibleForTesting
  internal fun reset() {
    perVenue.reset()
  }
}
