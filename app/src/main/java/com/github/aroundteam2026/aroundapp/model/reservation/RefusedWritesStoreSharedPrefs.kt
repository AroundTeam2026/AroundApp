// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.content.Context
import androidx.core.content.edit

/**
 * [RefusedWritesStore] kept in the app's private SharedPreferences, one entry per reservation of
 * the explorer [explorerUid]: a second account signing in on the same phone has its own.
 *
 * It writes with `apply()`, which is on disk a moment later. Losing the last write to a crash only
 * means one refusal isn't reported, which is how it was before writes were remembered.
 */
class RefusedWritesStoreSharedPrefs(context: Context, explorerUid: String) : RefusedWritesStore {
  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val prefix = "$explorerUid/"

  override fun all(): List<RecordedWrite> =
      prefs.all.mapNotNull { (key, value) ->
        if (key.startsWith(prefix)) parse(key.removePrefix(prefix), value as? String) else null
      }

  override fun put(write: RecordedWrite) {
    prefs.edit { putString(prefix + write.reservationId, "${write.questId}\t${write.state.name}") }
  }

  override fun remove(reservationId: String) {
    prefs.edit { remove(prefix + reservationId) }
  }

  /** The write an entry stands for, or null if it isn't one this version wrote. */
  private fun parse(reservationId: String, value: String?): RecordedWrite? {
    val (questId, state) = value?.split('\t', limit = 2)?.takeIf { it.size == 2 } ?: return null
    val known = WriteState.entries.firstOrNull { it.name == state } ?: return null
    return RecordedWrite(reservationId, questId, known)
  }

  companion object {
    /** The preferences file. */
    const val PREFS_NAME = "refused_writes"
  }
}
