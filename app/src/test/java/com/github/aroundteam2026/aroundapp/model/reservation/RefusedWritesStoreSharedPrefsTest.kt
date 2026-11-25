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

  private var now = START

  private fun store(explorerUid: String = "ada") =
      RefusedWritesStoreSharedPrefs(context, explorerUid) { now }

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

  @Test
  fun aWriteIsKeptForTwoWeeks() {
    store().put(write)

    now = START + TWO_WEEKS

    assertEquals(listOf(write), store().all())
  }

  // Writes that never settle, like a refusal nobody opens the quest for, don't pile up forever
  @Test
  fun aWriteOlderThanTwoWeeksIsForgottenForGood() {
    store().put(write)

    now = START + TWO_WEEKS + 1
    assertEquals(emptyList<RecordedWrite>(), store().all())

    now = START
    assertEquals(emptyList<RecordedWrite>(), store().all())
  }

  @Test
  fun changingTheStateOfAWriteDoesntRenewIt() {
    store().put(write)
    now = START + TWO_WEEKS - 1
    store().put(write.copy(state = WriteState.REFUSED))

    now = START + TWO_WEEKS + 1

    assertEquals(emptyList<RecordedWrite>(), store().all())
  }

  @Test
  fun aWriteRememberedAgainAfterItWasForgottenStartsOver() {
    store().put(write)
    now = START + TWO_WEEKS + 1
    store().all()

    store().put(write)
    now += TWO_WEEKS

    assertEquals(listOf(write), store().all())
  }

  // Entries from before writes were dated have no age to go by
  @Test
  fun anUndatedEntryIsForgotten() {
    context
        .getSharedPreferences(RefusedWritesStoreSharedPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString("ada/r1", "quest\tSHOWN")
        .commit()

    assertEquals(emptyList<RecordedWrite>(), store().all())
  }

  // A second account signing in on the same phone mustn't see the first one's writes
  @Test
  fun explorersAreKeptApart() {
    store("ada").put(write)

    assertEquals(emptyList<RecordedWrite>(), store("grace").all())
    store("grace").remove("r1")
    assertEquals(listOf(write), store("ada").all())
  }

  // Signing out must leave nothing of the explorer, also what this version can't read
  @Test
  fun clearingForgetsEveryEntryOfTheExplorerAndOnlyTheirs() {
    val prefs =
        context.getSharedPreferences(RefusedWritesStoreSharedPrefs.PREFS_NAME, Context.MODE_PRIVATE)
    store("ada").put(write)
    store("ada").put(RecordedWrite("r2", "other", WriteState.WRITTEN))
    store("grace").put(write)
    prefs.edit().putString("ada/old", "quest\tSHOWN").putString("ada/broken", "?").commit()

    store("ada").clear()

    assertEquals(emptyList<String>(), prefs.all.keys.filter { it.startsWith("ada/") })
    assertEquals(listOf(write), store("grace").all())
  }

  private companion object {
    const val START = 1_000_000L
    val TWO_WEEKS = RefusedWritesStoreSharedPrefs.MAX_AGE_MILLIS
  }
}
