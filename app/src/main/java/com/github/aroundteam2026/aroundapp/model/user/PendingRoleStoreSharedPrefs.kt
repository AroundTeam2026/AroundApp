// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.user

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [PendingRoleStore] kept in the app's private SharedPreferences.
 *
 * Writes use `commit()`, not `apply()`, so they are on disk when they return, and a failed write is
 * reported. A stored value that is no longer a [Role] name loads as no record, so it can never turn
 * into a different role.
 */
class PendingRoleStoreSharedPrefs(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PendingRoleStore {

  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  override suspend fun load(): PendingRole? =
      withContext(ioDispatcher) {
        val name = prefs.getString(KEY_ROLE, null)
        Role.entries
            .firstOrNull { it.name == name }
            ?.let { PendingRole(it, prefs.getString(KEY_UID, null)) }
      }

  override suspend fun save(pending: PendingRole) =
      withContext(ioDispatcher) {
        // A null uid removes the key, so no uid from an earlier record is left behind.
        prefs
            .edit()
            .putString(KEY_ROLE, pending.role.name)
            .putString(KEY_UID, pending.uid)
            .commitOrThrow("Couldn't record the chosen role")
      }

  override suspend fun clear() =
      withContext(ioDispatcher) {
        prefs.edit().remove(KEY_ROLE).remove(KEY_UID).commitOrThrow("Couldn't clear the role")
      }

  private fun SharedPreferences.Editor.commitOrThrow(message: String) {
    if (!commit()) throw IOException(message)
  }

  companion object {
    /** The preferences file; tests read it to check what reached the disk. */
    const val PREFS_NAME = "pending_role"
    private const val KEY_ROLE = "role"
    private const val KEY_UID = "uid"
  }
}
