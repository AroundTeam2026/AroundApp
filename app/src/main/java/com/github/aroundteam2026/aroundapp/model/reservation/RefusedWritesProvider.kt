// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.github.aroundteam2026.aroundapp.model.PerUser

/**
 * Builds the app's [RefusedWrites], so ViewModels never depend on an implementation. Every screen
 * of an explorer gets the same one: a refusal is reported to whoever hears it first, so screens
 * with their own would each miss what the others heard.
 */
object RefusedWritesProvider {
  private val perExplorer = PerUser { context, explorerUid ->
    RefusedWrites(storeOf(context, explorerUid))
  }

  /**
   * The refused writes of the explorer [explorerUid], kept in the app's private preferences.
   * Another explorer signing in gets their own, and the previous one's is dropped from memory.
   */
  fun refusedWrites(context: Context, explorerUid: String): RefusedWrites =
      perExplorer.get(context, explorerUid)

  /**
   * Forgets everything the phone remembers of the explorer [explorerUid], as when they sign out:
   * whoever signs in next, them again included, starts clean.
   */
  fun forget(context: Context, explorerUid: String) {
    storeOf(context, explorerUid).clear()
    perExplorer.forget(explorerUid)
  }

  private fun storeOf(context: Context, explorerUid: String) =
      RefusedWritesStoreSharedPrefs(
          context.applicationContext,
          explorerUid,
          System::currentTimeMillis,
      )

  /** Forgets the explorer's [RefusedWrites], as a new app process starts with none. */
  @VisibleForTesting
  internal fun reset() {
    perExplorer.reset()
  }
}
