// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

import android.content.Context
import androidx.core.content.edit

/**
 * [RefusedQuestWritesStore] kept in the app's private SharedPreferences, one entry per quest of the
 * venue [venueUid]: a second account signing in on the same phone has its own.
 *
 * It writes with `apply()`, which is on disk a moment later. Losing the last write to a crash only
 * means one refusal isn't reported, which is how it was before writes were remembered.
 */
class RefusedQuestWritesStoreSharedPrefs(context: Context, venueUid: String) :
    RefusedQuestWritesStore {
  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val prefix = "$venueUid/"

  override fun all(): List<RecordedQuestWrite> =
      prefs.all.mapNotNull { (key, value) ->
        if (key.startsWith(prefix)) parse(key.removePrefix(prefix), value as? String) else null
      }

  override fun put(write: RecordedQuestWrite) {
    prefs.edit {
      putString(
          prefix + write.questId,
          "${write.updatedAt}\t${write.writtenAt}\t${write.refused}",
      )
    }
  }

  override fun remove(questId: String) {
    prefs.edit { remove(prefix + questId) }
  }

  /** The write an entry stands for, or null if it isn't one this version wrote. */
  private fun parse(questId: String, value: String?): RecordedQuestWrite? {
    val (updatedAt, writtenAt, refused) =
        value?.split('\t', limit = 3)?.takeIf { it.size == 3 } ?: return null
    return RecordedQuestWrite(
        questId,
        updatedAt.toLongOrNull() ?: return null,
        writtenAt.toLongOrNull() ?: return null,
        refused.toBooleanStrictOrNull() ?: return null,
    )
  }

  companion object {
    /** The preferences file. */
    const val PREFS_NAME = "refused_quest_writes"

    /** Forgets everything remembered for the venue [venueUid], as when it signs out. */
    fun forget(context: Context, venueUid: String) {
      val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      prefs.edit { prefs.all.keys.filter { it.startsWith("$venueUid/") }.forEach(::remove) }
    }
  }
}
