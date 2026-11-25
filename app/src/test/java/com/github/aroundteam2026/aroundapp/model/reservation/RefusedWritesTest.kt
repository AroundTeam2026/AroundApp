// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Telling a refused write apart with [RefusedWrites], also after the app was killed. */
class RefusedWritesTest {
  private val store = FakeRefusedWritesStore()
  private val writes = RefusedWrites(store).apply { record("r1", "quest") }
  private val hearing = writes.hearing()

  /** The app killed and opened again: what it remembered is all the new [RefusedWrites] has. */
  private fun afterARestart() = RefusedWrites(store)

  private fun listed(status: ReservationStatus = ReservationStatus.PENDING, id: String = "r1") =
      listOf(newPendingReservation("quest", "venue", "Ada", 0L).copy(id = id, status = status))

  @Test
  fun aWriteNotListedYetIsNotRefused() {
    assertEquals(emptySet<String>(), hearing.refusedIn(emptyList()))
  }

  @Test
  fun aWriteListedAsPendingThenGoneIsRefusedOnce() {
    hearing.refusedIn(listed())

    assertEquals(setOf("quest"), hearing.refusedIn(emptyList()))
    assertEquals(emptySet<String>(), hearing.refusedIn(emptyList()))
  }

  @Test
  fun aWriteTheVenueAnsweredOrTheExplorerCancelledIsNotRefused() {
    for (status in listOf(ReservationStatus.APPROVED, ReservationStatus.CANCELLED)) {
      val answered = RefusedWrites(FakeRefusedWritesStore()).apply { record("r1", "quest") }
      val answering = answered.hearing()
      answering.refusedIn(listed())
      answering.refusedIn(listed(status))

      assertEquals(emptySet<String>(), answering.refusedIn(emptyList()))
    }
  }

  // The write is queued on the phone, the app is killed, and the server refuses it afterwards
  @Test
  fun aWriteListedAsPendingBeforeARestartIsRefusedWhenItGoesAfterIt() {
    hearing.refusedIn(listed())

    val restarted = afterARestart()
    val hearing = restarted.hearing()
    hearing.refusedIn(listed())

    assertEquals(setOf("quest"), hearing.refusedIn(emptyList()))
  }

  // The server refuses it while the app is open on another screen: no list from this run ever
  // had the write, and none is older than what the phone remembers
  @Test
  fun aWriteListedBeforeARestartIsRefusedByAScreenWhoseFirstListLacksIt() {
    hearing.refusedIn(listed())

    val restarted = afterARestart()

    assertEquals(setOf("quest"), restarted.hearing().refusedIn(emptyList()))
    assertEquals(setOf("quest"), restarted.refusedQuests)
  }

  // A cache that lost the write, say, tells nothing: only a write that was listed can vanish
  @Test
  fun aWriteNeverListedBeforeARestartIsNotRefusedBySayingNothingAfterIt() {
    val restarted = afterARestart()

    val hearing = restarted.hearing()

    assertEquals(emptySet<String>(), hearing.refusedIn(emptyList()))
    assertEquals(emptySet<String>(), hearing.refusedIn(emptyList()))
  }

  @Test
  fun aRefusalIsRememberedAcrossRestartsUntilTheQuestIsBookedAgain() {
    hearing.refusedIn(listed())
    hearing.refusedIn(emptyList())
    assertEquals(setOf("quest"), writes.refusedQuests)

    assertEquals(setOf("quest"), afterARestart().refusedQuests)
    assertEquals(setOf("quest"), afterARestart().refusedQuests)

    val booked = afterARestart()
    booked.record("r2", "quest")
    assertEquals(emptySet<String>(), booked.refusedQuests)
    assertEquals(emptySet<String>(), afterARestart().refusedQuests)
  }

  @Test
  fun aRefusalEndsOnceAnotherReservationOfTheQuestStands() {
    hearing.refusedIn(listed())
    hearing.refusedIn(emptyList())

    hearing.refusedIn(listed(ReservationStatus.APPROVED, id = "elsewhere"))

    assertEquals(emptySet<String>(), writes.refusedQuests)
    assertEquals(emptySet<String>(), afterARestart().refusedQuests)
  }

  @Test
  fun aCancelledReservationOfTheQuestDoesntEndARefusal() {
    hearing.refusedIn(listed())
    hearing.refusedIn(emptyList())

    hearing.refusedIn(listed(ReservationStatus.CANCELLED, id = "old"))

    assertEquals(setOf("quest"), writes.refusedQuests)
  }

  @Test
  fun aRefusalIsUnreportedUntilItIsReported() {
    hearing.refusedIn(listed())
    assertFalse(writes.hasUnreportedRefusal)

    hearing.refusedIn(emptyList())
    assertTrue(writes.hasUnreportedRefusal)

    writes.markReported()
    assertFalse(writes.hasUnreportedRefusal)
    assertEquals(setOf("quest"), writes.refusedQuests)
  }

  @Test
  fun whetherARefusalWasReportedIsRememberedAcrossRestarts() {
    hearing.refusedIn(listed())
    hearing.refusedIn(emptyList())
    assertTrue(afterARestart().hasUnreportedRefusal)

    writes.markReported()

    val restarted = afterARestart()
    assertFalse(restarted.hasUnreportedRefusal)
    assertEquals(setOf("quest"), restarted.refusedQuests)
  }

  @Test
  fun aRefusalAfterAReportedOneIsUnreported() {
    hearing.refusedIn(listed())
    hearing.refusedIn(emptyList())
    writes.markReported()
    writes.record("r2", "other")
    val other = newPendingReservation("other", "venue", "Ada", 0L).copy(id = "r2")
    hearing.refusedIn(listOf(other))

    hearing.refusedIn(emptyList())

    assertTrue(writes.hasUnreportedRefusal)
    assertEquals(setOf("quest", "other"), writes.refusedQuests)
  }

  @Test
  fun aReportedRefusalEndsWhenTheQuestIsBookedAgain() {
    hearing.refusedIn(listed())
    hearing.refusedIn(emptyList())
    writes.markReported()

    writes.record("r2", "quest")

    assertEquals(emptySet<String>(), writes.refusedQuests)
    assertEquals(emptySet<String>(), afterARestart().refusedQuests)
  }

  @Test
  fun aWriteTheVenueAnsweredIsNotRememberedAnyMore() {
    hearing.refusedIn(listed())
    hearing.refusedIn(listed(ReservationStatus.APPROVED))

    assertEquals(emptyList<RecordedWrite>(), store.all())
  }

  // Each screen listens on its own, and one can still hold a list from before the write
  @Test
  fun aWriteAnotherScreenListedIsNotRefusedByAScreenThatNeverListedIt() {
    val other = writes.hearing()
    hearing.refusedIn(listed())

    assertEquals(emptySet<String>(), other.refusedIn(emptyList()))
    assertEquals(emptySet<String>(), writes.refusedQuests)
    assertEquals(setOf("quest"), hearing.refusedIn(emptyList()))
  }

  @Test
  fun aScreenRefusesAWriteOnceItListedItAsPendingToo() {
    val other = writes.hearing()
    hearing.refusedIn(listed())
    other.refusedIn(listed())

    assertEquals(setOf("quest"), other.refusedIn(emptyList()))
    assertEquals(emptySet<String>(), hearing.refusedIn(emptyList()))
  }
}
