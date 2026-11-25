// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.content.Context
import androidx.core.content.edit
import java.util.concurrent.TimeUnit

/**
 * [RefusedWritesStore] kept in the app's private SharedPreferences, one entry per reservation of
 * the explorer [explorerUid]: a second account signing in on the same phone has its own.
 *
 * Each entry is dated when it is first put, by [clock] in milliseconds, and forgotten once it is
 * older than [MAX_AGE_MILLIS]: a write that never settles, or a refusal nobody opens the quest for,
 * doesn't stay for ever. Entries nobody dated, or this version can't read, go with them.
 *
 * It writes with `apply()`, which is on disk a moment later. Losing the last write to a crash only
 * means one refusal isn't reported, which is how it was before writes were remembered.
 */
class RefusedWritesStoreSharedPrefs(
    context: Context,
    explorerUid: String,
    private val clock: () -> Long,
) : RefusedWritesStore {
  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val prefix = "$explorerUid/"

  override fun all(): List<RecordedWrite> {
    val now = clock()
    val forgotten = mutableListOf<String>()
    val writes =
        prefs.all.mapNotNull { (key, value) ->
          if (!key.startsWith(prefix)) return@mapNotNull null
          val entry = parse(key.removePrefix(prefix), value as? String)
          if (entry == null || now - entry.writtenAt > MAX_AGE_MILLIS) {
            forgotten += key
            null
          } else {
            entry.write
          }
        }
    if (forgotten.isNotEmpty()) prefs.edit { forgotten.forEach { remove(it) } }
    return writes
  }

  override fun put(write: RecordedWrite) {
    val key = prefix + write.reservationId
    // A write keeps the date it was first put, whatever happens to it after
    val writtenAt = prefs.getString(key, null)?.let { parse(write.reservationId, it) }?.writtenAt
    prefs.edit { putString(key, "${write.questId}\t${write.state.name}\t${writtenAt ?: clock()}") }
  }

  override fun remove(reservationId: String) {
    prefs.edit { remove(prefix + reservationId) }
  }

  override fun clear() {
    // One edit for all of them, whether or not this version can read them
    prefs.edit { prefs.all.keys.filter { it.startsWith(prefix) }.forEach(::remove) }
  }

  private class Entry(val write: RecordedWrite, val writtenAt: Long)

  /** The entry [value] stands for, or null if it isn't a dated one this version wrote. */
  private fun parse(reservationId: String, value: String?): Entry? {
    val parts = value?.split('\t', limit = 3)?.takeIf { it.size == 3 } ?: return null
    val state = WriteState.entries.firstOrNull { it.name == parts[1] } ?: return null
    val writtenAt = parts[2].toLongOrNull() ?: return null
    return Entry(RecordedWrite(reservationId, parts[0], state), writtenAt)
  }

  companion object {
    /** The preferences file. */
    const val PREFS_NAME = "refused_writes"

    /** How long a write is remembered: two weeks. */
    val MAX_AGE_MILLIS = TimeUnit.DAYS.toMillis(14)
  }
}
