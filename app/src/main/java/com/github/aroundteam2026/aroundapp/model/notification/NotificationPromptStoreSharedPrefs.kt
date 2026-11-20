// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [NotificationPromptStore] kept in the app's private SharedPreferences, like the pending role.
 *
 * The answer belongs to the phone, not the account: Android's permission is the app's, so a second
 * account signing in on the same phone finds it already granted or refused.
 */
class NotificationPromptStoreSharedPrefs(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : NotificationPromptStore {

  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  override suspend fun wasAnswered(): Boolean =
      withContext(ioDispatcher) { prefs.getBoolean(KEY_ANSWERED, false) }

  override suspend fun markAnswered() =
      withContext(ioDispatcher) { prefs.edit().putBoolean(KEY_ANSWERED, true).commitOrThrow() }

  // Not the KTX edit {}: it drops commit()'s result, which is how a failed write shows. The
  // pending role's store does the same.
  private fun SharedPreferences.Editor.commitOrThrow() {
    if (!commit()) throw IOException("Couldn't record the notification answer")
  }

  companion object {
    /** The preferences file; tests read it to check what reached the disk. */
    const val PREFS_NAME = "notification_prompt"
    private const val KEY_ANSWERED = "answered"
  }
}
