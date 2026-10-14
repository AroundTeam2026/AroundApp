// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model

import com.github.aroundteam2026.aroundapp.model.demo.MapDemoData
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests the quests the app runs on until it reads them from Firestore: the demo data, in memory.
 */
class RepositoryProvidersTest {

  private val now = 1_800_000_000_000L

  @Test
  fun theQuestsAreTheActiveDemoQuests() = runTest {
    val repository = QuestRepositoryProvider.create { now }

    val expected = MapDemoData.quests(now).filter { it.status == QuestStatus.ACTIVE }
    assertEquals(expected.toSet(), repository.observeActiveQuests().first().toSet())
  }

  @Test
  fun eachNewRepositoryStartsFromTheDemoDataAlone() = runTest {
    // Writes to one repository must not reach another, or tests would leak into each other
    val created = QuestRepositoryProvider.create { now }.createQuest(testQuest()).getOrThrow()

    assertNull(QuestRepositoryProvider.create { now }.getQuest(created))
  }
}
