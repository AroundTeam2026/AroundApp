// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** What [RefusedQuestWritesStoreSharedPrefs] keeps for the next launch. */
@RunWith(AndroidJUnit4::class)
class RefusedQuestWritesStoreSharedPrefsTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  private fun store(venueUid: String = "cafe") =
      RefusedQuestWritesStoreSharedPrefs(context, venueUid)

  private val write = RecordedQuestWrite("q1", updatedAt = 2_000L, writtenAt = 9L)

  @Test
  fun aNewPhoneRemembersNothing() {
    assertEquals(emptyList<RecordedQuestWrite>(), store("nobody").all())
  }

  @Test
  fun whatWasPutIsThereForTheNextLaunch() {
    store().put(write)

    assertEquals(listOf(write), store().all())
  }

  @Test
  fun putAgainReplacesTheWriteOfTheQuest() {
    store().put(write)
    store().put(write.copy(refused = true))

    assertEquals(listOf(write.copy(refused = true)), store().all())
  }

  @Test
  fun aRemovedWriteIsGone() {
    val other = write.copy(questId = "q2")
    store().put(write)
    store().put(other)

    store().remove("q1")

    assertEquals(listOf(other), store().all())
  }

  // A second account signing in on the same phone mustn't see the first one's writes
  @Test
  fun venuesAreKeptApart() {
    store("cafe").put(write)

    assertEquals(emptyList<RecordedQuestWrite>(), store("bar").all())
    store("bar").remove("q1")
    assertEquals(listOf(write), store("cafe").all())
  }

  @Test
  fun forgettingAVenueDropsOnlyItsWrites() {
    store("cafe").put(write)
    store("bar").put(write)

    RefusedQuestWritesStoreSharedPrefs.forget(context, "cafe")

    assertEquals(emptyList<RecordedQuestWrite>(), store("cafe").all())
    assertEquals(listOf(write), store("bar").all())
  }

  // An entry another version wrote, or a damaged one, must not crash the app at start
  @Test
  fun anEntryItCantReadIsLeftOut() {
    val prefs =
        context.getSharedPreferences(
            RefusedQuestWritesStoreSharedPrefs.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
    prefs.edit {
      putString("cafe/bad1", "nonsense")
      putString("cafe/bad2", "x\t9\tfalse")
      putString("cafe/bad3", "5\ty\tfalse")
      putString("cafe/bad4", "5\t9\tmaybe")
      putInt("cafe/bad5", 7)
    }
    store().put(write)

    assertEquals(listOf(write), store().all())
  }
}
