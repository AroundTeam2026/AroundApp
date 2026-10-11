// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

import com.github.aroundteam2026.aroundapp.model.testQuest
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests [FakeQuestRepository] against the [QuestRepository] contract, one behaviour per test. */
class FakeQuestRepositoryTest {
  private lateinit var repository: FakeQuestRepository

  /** Gives every test a fresh, empty repository whose clock always returns [FIXED_NOW]. */
  @Before
  fun setUp() {
    repository = FakeQuestRepository(now = { FIXED_NOW })
  }

  @Test
  fun observeActiveQuests_excludesDraftAndArchived() = runBlocking {
    repository.createQuest(testQuest(status = QuestStatus.DRAFT))
    repository.createQuest(testQuest(status = QuestStatus.ARCHIVED))
    val activeId = repository.createQuest(testQuest(status = QuestStatus.ACTIVE)).getOrThrow()

    val active = repository.observeActiveQuests().first()

    assertEquals(listOf(activeId), active.map { it.id })
  }

  @Test
  fun observeActiveQuests_emitsAgainAfterCreateQuest() = runBlocking {
    withTimeout(TIMEOUT_MS) {
      val emissions =
          async(start = CoroutineStart.UNDISPATCHED) {
            repository.observeActiveQuests().take(2).toList()
          }

      repository.createQuest(testQuest())

      assertEquals(listOf(0, 1), emissions.await().map { it.size })
    }
  }

  @Test
  fun observeActiveQuests_doesNotEmitWhenADraftIsCreated() = runBlocking {
    withTimeout(TIMEOUT_MS) {
      val emissions =
          async(start = CoroutineStart.UNDISPATCHED) {
            repository.observeActiveQuests().take(2).toList()
          }

      repository.createQuest(testQuest(status = QuestStatus.DRAFT))
      // createQuest never suspends, so without this the collector would only see the state after
      // both writes and the test would pass even if the draft caused a duplicate emission.
      yield()
      repository.createQuest(testQuest(status = QuestStatus.ACTIVE))

      assertEquals(listOf(0, 1), emissions.await().map { it.size })
    }
  }

  @Test
  fun observeQuestsByVenue_doesNotEmitWhenAnotherVenueCreatesAQuest() = runBlocking {
    withTimeout(TIMEOUT_MS) {
      val emissions =
          async(start = CoroutineStart.UNDISPATCHED) {
            repository.observeQuestsByVenue("v1").take(2).toList()
          }

      repository.createQuest(testQuest(venueId = "v2"))
      // Lets the collector see the state after the first write; see the active-quests test above.
      yield()
      repository.createQuest(testQuest(venueId = "v1"))

      assertEquals(listOf(0, 1), emissions.await().map { it.size })
    }
  }

  @Test
  fun observeQuestsByVenue_returnsAllOfThatVenuesQuestsWhateverTheirStatus() = runBlocking {
    val activeId = repository.createQuest(testQuest(venueId = "v1")).getOrThrow()
    val draftId =
        repository.createQuest(testQuest(venueId = "v1", status = QuestStatus.DRAFT)).getOrThrow()
    val archivedId =
        repository
            .createQuest(testQuest(venueId = "v1", status = QuestStatus.ARCHIVED))
            .getOrThrow()
    repository.createQuest(testQuest(venueId = "v2"))

    val quests = repository.observeQuestsByVenue("v1").first()

    assertEquals(setOf(activeId, draftId, archivedId), quests.map { it.id }.toSet())
  }

  @Test
  fun createQuest_returnsNewUniqueId() = runBlocking {
    val first = repository.createQuest(testQuest()).getOrThrow()
    val second = repository.createQuest(testQuest()).getOrThrow()

    assertTrue(first.isNotBlank())
    assertNotEquals(first, second)
  }

  @Test
  fun createQuest_ignoresCallerId() = runBlocking {
    val first = repository.createQuest(testQuest(id = "caller", title = "First")).getOrThrow()
    val second = repository.createQuest(testQuest(id = "caller", title = "Second")).getOrThrow()

    assertNotEquals("caller", first)
    assertNotEquals("caller", second)
    assertNotEquals(first, second)
    assertEquals("First", repository.getQuest(first)?.title)
    assertEquals("Second", repository.getQuest(second)?.title)
  }

  @Test
  fun getQuest_returnsStoredQuestWithRepositoryTimestamps() = runBlocking {
    // A clock that advances on every call, so reading it twice would give different timestamps.
    var time = FIXED_NOW
    repository = FakeQuestRepository(now = { time++ })
    val input =
        testQuest(
            title = "Find the mural",
            reward = Reward.Discount(10.0, DiscountUnit.PERCENT, "On drinks", expiresAt = 5L),
            createdAt = 1L,
            updatedAt = 2L,
        )
    val id = repository.createQuest(input).getOrThrow()

    assertEquals(
        input.copy(id = id, createdAt = FIXED_NOW, updatedAt = FIXED_NOW),
        repository.getQuest(id),
    )
  }

  @Test
  fun createQuest_defaultClockUsesCurrentTimeMillis() = runBlocking {
    val defaultRepository = FakeQuestRepository()

    val before = System.currentTimeMillis()
    val id = defaultRepository.createQuest(testQuest()).getOrThrow()
    val after = System.currentTimeMillis()

    val createdAt = defaultRepository.getQuest(id)!!.createdAt
    assertTrue(createdAt in before..after)
  }

  @Test
  fun getQuest_returnsNullForUnknownId() = runBlocking {
    assertNull(repository.getQuest("unknown"))
  }

  @Test
  fun createQuest_returnsFailureWhenForced() = runBlocking {
    val error = IllegalStateException("forced")
    repository.forcedFailure = error

    val result = repository.createQuest(testQuest())

    assertSame(error, result.exceptionOrNull())
    assertTrue(repository.observeActiveQuests().first().isEmpty())
  }

  @Test
  fun startsEmptyByDefault() = runBlocking {
    assertEquals(emptyList<Quest>(), FakeQuestRepository().observeActiveQuests().first())
  }

  @Test
  fun keepsInitialQuestsExactlyAsGiven() = runBlocking {
    // Unlike createQuest, seeding keeps ids and timestamps: venues feature quests by id, and the
    // newest quest must stay the newest
    val seeded = testQuest(id = "seed-1", createdAt = 42L)
    val repository = FakeQuestRepository(now = { 9_999L }, initialQuests = listOf(seeded))

    assertEquals(seeded, repository.getQuest("seed-1"))
  }

  @Test
  fun initialQuestsAreObservedLikeCreatedOnes() = runBlocking {
    val active = testQuest(id = "active", venueId = "v1")
    val draft = testQuest(id = "draft", venueId = "v1", status = QuestStatus.DRAFT)
    val repository = FakeQuestRepository(initialQuests = listOf(active, draft))

    assertEquals(listOf(active), repository.observeActiveQuests().first())
    assertEquals(setOf(active, draft), repository.observeQuestsByVenue("v1").first().toSet())
  }

  @Test
  fun createdQuestsNeverReuseASeededId() = runBlocking {
    // The generated ids start at quest-1, so a seed using that id must not be overwritten
    val seeded = testQuest(id = "quest-1", title = "Seeded")
    val repository = FakeQuestRepository(initialQuests = listOf(seeded))

    val id = repository.createQuest(testQuest(title = "Created")).getOrThrow()

    assertNotEquals("quest-1", id)
    assertEquals("Seeded", repository.getQuest("quest-1")?.title)
    assertEquals("Created", repository.getQuest(id)?.title)
  }

  @Test
  fun rejectsInitialQuestsSharingAnId() {
    // Keeping only one of them would silently drop a quest
    assertThrows(IllegalArgumentException::class.java) {
      FakeQuestRepository(initialQuests = listOf(testQuest(id = "same"), testQuest(id = "same")))
    }
  }

  private companion object {
    /** The time the test clock always returns, in epoch milliseconds. */
    const val FIXED_NOW = 1_700_000_000_000L

    /** Fails a flow test instead of hanging if an expected emission never comes. */
    const val TIMEOUT_MS = 1_000L
  }
}
