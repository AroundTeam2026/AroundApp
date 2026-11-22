// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** What [RefusedWritesStoreSharedPrefs] keeps for the next launch. */
@RunWith(AndroidJUnit4::class)
class RefusedWritesStoreSharedPrefsTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  private fun store(explorerUid: String = "ada") =
      RefusedWritesStoreSharedPrefs(context, explorerUid)

  private val write = RecordedWrite("r1", "quest", WriteState.SHOWN)

  @Test
  fun aNewPhoneRemembersNothing() {
    assertEquals(emptyList<RecordedWrite>(), store("nobody").all())
  }

  @Test
  fun whatWasPutIsThereForTheNextLaunch() {
    store().put(write)

    assertEquals(listOf(write), store().all())
  }

  @Test
  fun putAgainReplacesTheStateOfTheWrite() {
    store().put(write)
    store().put(write.copy(state = WriteState.REFUSED))

    assertEquals(listOf(write.copy(state = WriteState.REFUSED)), store().all())
  }

  @Test
  fun aRemovedWriteIsGone() {
    store().put(write)
    store().put(RecordedWrite("r2", "other", WriteState.WRITTEN))

    store().remove("r1")

    assertEquals(listOf(RecordedWrite("r2", "other", WriteState.WRITTEN)), store().all())
  }

  // A second account signing in on the same phone mustn't see the first one's writes
  @Test
  fun explorersAreKeptApart() {
    store("ada").put(write)

    assertEquals(emptyList<RecordedWrite>(), store("grace").all())
    store("grace").remove("r1")
    assertEquals(listOf(write), store("ada").all())
  }
}
