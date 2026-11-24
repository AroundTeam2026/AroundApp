// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

import com.github.aroundteam2026.aroundapp.model.testQuest
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the venue's quest writes are told apart: confirmed, or refused by the server. */
class RefusedQuestWritesTest {
  private val store = FakeRefusedQuestWritesStore()
  private var now = START
  private var writes = RefusedQuestWrites(store) { now }

  /** The app killed and opened again: only what the [store] kept is left. */
  private fun restart() {
    writes = RefusedQuestWrites(store) { now }
  }

  /** The quest [id] as listed while the server hasn't answered its last write. */
  private fun sent(updatedAt: Long = 2_000L, id: String = "q") =
      testQuest(id = id, updatedAt = updatedAt).copy(pendingWrite = true)

  private fun settled(updatedAt: Long, id: String = "q") = testQuest(id = id, updatedAt = updatedAt)

  @Test
  fun aQuestNeverListedAsPendingIsNotJudged() {
    writes.hear(listOf(settled(1_000L)))
    writes.hear(emptyList())

    assertFalse(writes.hasUnreportedRefusal)
    assertEquals(emptyList<RecordedQuestWrite>(), store.all())
  }

  @Test
  fun aNewQuestTheServerRefusesVanishesAfterBeingListed() {
    writes.hear(listOf(sent()))
    assertFalse(writes.hasUnreportedRefusal)

    writes.hear(emptyList())

    assertTrue(writes.hasUnreportedRefusal)
  }

  @Test
  fun aQuestTheServerConfirmsIsNotRefused() {
    writes.hear(listOf(sent(2_000L)))

    writes.hear(listOf(settled(2_000L)))
    writes.hear(emptyList())

    assertFalse(writes.hasUnreportedRefusal)
    assertEquals(emptyList<RecordedQuestWrite>(), store.all())
  }

  // A refused edit or archive isn't removed: the quest goes back to what the server has
  @Test
  fun anEditTheServerRefusesRevertsTheQuest() {
    writes.hear(listOf(sent(2_000L)))

    writes.hear(listOf(settled(1_000L)))

    assertTrue(writes.hasUnreportedRefusal)
  }

  @Test
  fun aQuestStillSentIsNeitherConfirmedNorRefused() {
    writes.hear(listOf(sent(2_000L)))
    now += 1_000

    writes.hear(listOf(sent(2_000L)))

    assertFalse(writes.hasUnreportedRefusal)
    assertEquals(listOf(RecordedQuestWrite("q", 2_000L, START)), store.all())
  }

  // Two edits queued: the server takes the first and refuses the second, so the quest is the first
  @Test
  fun aSecondEditTheServerRefusesFallsBackToTheFirst() {
    writes.hear(listOf(sent(2_000L)))
    writes.hear(listOf(sent(3_000L)))

    writes.hear(listOf(settled(2_000L)))

    assertTrue(writes.hasUnreportedRefusal)
  }

  @Test
  fun aWriteListedBeforeARestartIsRefusedAfterIt() {
    writes.hear(listOf(sent()))
    restart()

    writes.hear(emptyList())

    assertTrue(writes.hasUnreportedRefusal)
  }

  @Test
  fun aRefusalIsRememberedAcrossARestartUntilReported() {
    writes.hear(listOf(sent()))
    writes.hear(emptyList())
    restart()
    assertTrue(writes.hasUnreportedRefusal)

    writes.markReported()
    restart()

    assertFalse(writes.hasUnreportedRefusal)
    assertEquals(emptyList<RecordedQuestWrite>(), store.all())
  }

  // Only telling the venue ends a refusal, whatever the list shows later
  @Test
  fun aRefusalStaysUntilReportedEvenIfTheQuestIsListedAgain() {
    writes.hear(listOf(sent(2_000L)))
    writes.hear(emptyList())

    writes.hear(listOf(settled(2_000L)))
    writes.hear(listOf(sent(3_000L)))

    assertTrue(writes.hasUnreportedRefusal)
  }

  @Test
  fun aWriteAfterAReportedRefusalIsJudgedAgain() {
    writes.hear(listOf(sent(2_000L)))
    writes.hear(emptyList())
    writes.markReported()

    writes.hear(listOf(sent(3_000L)))
    writes.hear(emptyList())

    assertTrue(writes.hasUnreportedRefusal)
  }

  @Test
  fun eachQuestIsJudgedOnItsOwn() {
    writes.hear(listOf(sent(2_000L, id = "a"), sent(2_000L, id = "b")))

    writes.hear(listOf(settled(2_000L, id = "a")))

    assertEquals(listOf("b"), store.all().filter { it.refused }.map { it.questId })
    assertEquals(listOf("b"), store.all().map { it.questId })
  }

  // A venue that never opened its list again doesn't keep an old write for ever
  @Test
  fun aWriteOlderThanTwoWeeksIsForgottenOnTheNextStart() {
    writes.hear(listOf(sent()))
    now += TimeUnit.DAYS.toMillis(14) + 1

    restart()
    writes.hear(emptyList())

    assertFalse(writes.hasUnreportedRefusal)
    assertEquals(emptyList<RecordedQuestWrite>(), store.all())
  }

  @Test
  fun aWriteOfTwoWeeksIsStillKnown() {
    writes.hear(listOf(sent()))
    now += TimeUnit.DAYS.toMillis(14)

    restart()
    writes.hear(emptyList())

    assertTrue(writes.hasUnreportedRefusal)
  }

  private companion object {
    const val START = 5_000_000L
  }
}
