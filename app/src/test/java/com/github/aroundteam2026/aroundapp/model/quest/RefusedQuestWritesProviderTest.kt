// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.testQuest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RefusedQuestWritesProviderTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Before fun setUp() = RefusedQuestWritesProvider.reset()

  @Test
  fun everyScreenOfAVenueGetsTheSameRefusedQuestWrites() {
    assertSame(
        RefusedQuestWritesProvider.refusedWrites(context, "cafe"),
        RefusedQuestWritesProvider.refusedWrites(context, "cafe"),
    )
  }

  @Test
  fun anotherVenueGetsItsOwn() {
    val cafe = RefusedQuestWritesProvider.refusedWrites(context, "cafe")

    assertNotSame(cafe, RefusedQuestWritesProvider.refusedWrites(context, "bar"))
  }

  // After signing out, what the venue's writes held must be gone, in memory as on the phone
  @Test
  fun forgettingAVenueDropsWhatItKnewAndStartsItOver() {
    val pending = testQuest(id = "q1", updatedAt = 2_000L).copy(pendingWrite = true)
    val before = RefusedQuestWritesProvider.refusedWrites(context, "cafe")
    before.hear(listOf(pending))
    before.hear(emptyList())

    RefusedQuestWritesProvider.forget(context, "cafe")

    assertEquals(
        emptyList<RecordedQuestWrite>(),
        RefusedQuestWritesStoreSharedPrefs(context, "cafe").all(),
    )
    assertNotSame(before, RefusedQuestWritesProvider.refusedWrites(context, "cafe"))
    assertFalse(RefusedQuestWritesProvider.refusedWrites(context, "cafe").hasUnreportedRefusal)
  }

  // What it learns is kept on the phone, under the venue, for the next time the app opens
  @Test
  fun whatItLearnsIsKeptInThePhonesPreferences() {
    val pending = testQuest(id = "q1", updatedAt = 2_000L).copy(pendingWrite = true)
    RefusedQuestWritesProvider.refusedWrites(context, "cafe").hear(listOf(pending))

    val remembered = RefusedQuestWritesStoreSharedPrefs(context, "cafe").all().single()

    assertEquals("q1", remembered.questId)
    assertEquals(2_000L, remembered.updatedAt)
  }
}
