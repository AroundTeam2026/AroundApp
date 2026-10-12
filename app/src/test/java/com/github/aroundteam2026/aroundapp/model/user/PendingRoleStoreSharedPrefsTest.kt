// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.user

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingRoleStoreSharedPrefsTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  /** The preferences file itself: what a new process reads after this one dies. */
  private val file =
      File(context.dataDir, "shared_prefs/${PendingRoleStoreSharedPrefs.PREFS_NAME}.xml")

  private fun TestScope.store() =
      PendingRoleStoreSharedPrefs(context, StandardTestDispatcher(testScheduler))

  /** A store whose every write fails, the way `commit()` reports a full disk. */
  private fun TestScope.failingStore(): PendingRoleStoreSharedPrefs {
    val editor = mockk<SharedPreferences.Editor>()
    every { editor.putString(any(), any()) } returns editor
    every { editor.remove(any()) } returns editor
    every { editor.commit() } returns false
    val failing = mockk<Context>()
    every { failing.getSharedPreferences(any(), any()) } returns
        mockk { every { edit() } returns editor }
    return PendingRoleStoreSharedPrefs(failing, StandardTestDispatcher(testScheduler))
  }

  @Test
  fun save_isOnDiskWhenItReturns_andLoadReturnsIt() = runTest {
    store().save(PendingRole(Role.VENUE, "uid-1"))

    assertTrue(file.readText().contains("uid-1"))
    assertEquals(PendingRole(Role.VENUE, "uid-1"), store().load())
  }

  @Test fun load_withNothingRecorded_returnsNull() = runTest { assertNull(store().load()) }

  @Test
  fun save_replacesAnEarlierRecord() = runTest {
    store().save(PendingRole(Role.VENUE, "uid-1"))

    store().save(PendingRole(Role.EXPLORER, uid = null))

    // The earlier uid must not stick to a choice made before sign-up.
    assertEquals(PendingRole(Role.EXPLORER, uid = null), store().load())
  }

  // apply() can't report a failed write, so the next two tests also pin both writes to commit().
  @Test
  fun save_whenTheWriteFails_throwsIOException() = runTest {
    val error = runCatching { failingStore().save(PendingRole(Role.VENUE, null)) }.exceptionOrNull()

    assertTrue(error is IOException)
  }

  @Test
  fun clear_whenTheWriteFails_throwsIOException() = runTest {
    val error = runCatching { failingStore().clear() }.exceptionOrNull()

    assertTrue(error is IOException)
  }

  @Test
  fun clear_removesTheRecordFromDisk() = runTest {
    store().save(PendingRole(Role.VENUE, "uid-1"))

    store().clear()

    assertNull(store().load())
    assertFalse(file.readText().contains("uid-1"))
  }

  @Test
  fun load_withAValueThatIsNoLongerARole_returnsNull() = runTest {
    context
        .getSharedPreferences(PendingRoleStoreSharedPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString("role", "ADMIN")
        .commit()

    assertNull(store().load())
  }
}
