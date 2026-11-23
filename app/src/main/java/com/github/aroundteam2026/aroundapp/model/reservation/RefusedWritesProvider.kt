// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.content.Context
import androidx.annotation.VisibleForTesting

/**
 * Builds the app's [RefusedWrites], so ViewModels never depend on an implementation. Every screen
 * of an explorer gets the same one: a refusal is reported to whoever hears it first, so screens
 * with their own would each miss what the others heard.
 */
object RefusedWritesProvider {
  private var current: Pair<String, RefusedWrites>? = null

  /**
   * The refused writes of the explorer [explorerUid], kept in the app's private preferences.
   * Another explorer signing in gets their own, and the previous one's is dropped from memory.
   */
  @Synchronized
  fun refusedWrites(context: Context, explorerUid: String): RefusedWrites {
    current
        ?.takeIf { (uid, _) -> uid == explorerUid }
        ?.let {
          return it.second
        }
    val writes =
        RefusedWrites(RefusedWritesStoreSharedPrefs(context.applicationContext, explorerUid))
    current = explorerUid to writes
    return writes
  }

  /** Forgets the explorer's [RefusedWrites], as a new app process starts with none. */
  @Synchronized
  @VisibleForTesting
  internal fun reset() {
    current = null
  }
}
