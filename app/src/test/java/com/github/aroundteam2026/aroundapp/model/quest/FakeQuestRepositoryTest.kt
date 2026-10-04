// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

import com.github.aroundteam2026.aroundapp.model.common.Location
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

  /**
   * Builds a quest with a blank id by default and timestamps that differ from [FIXED_NOW], so tests
   * can tell the caller's values from the repository's.
   */
  private fun quest(
      id: String = "",
      venueId: String = "v1",
      status: QuestStatus = QuestStatus.ACTIVE,
      title: String = "Title",
      reward: Reward? = null,
      createdAt: Long = 1_000L,
      updatedAt: Long = 1_000L,
  ) =
      Quest(
          id = id,
          venueId = venueId,
          venueName = "Cafe",
          location = Location(46.52, 6.57),
          radiusMeters = 50,
          title = title,
          description = "Description",
          requirements = "Requirements",
          proofType = ProofType.PHOTO,
          reward = reward,
          status = status,
          createdAt = createdAt,
          updatedAt = updatedAt,
      )

  @Test
  fun observeActiveQuests_excludesDraftAndArchived() = runBlocking {
    repository.createQuest(quest(status = QuestStatus.DRAFT))
    repository.createQuest(quest(status = QuestStatus.ARCHIVED))
    val activeId = repository.createQuest(quest(status = QuestStatus.ACTIVE)).getOrThrow()

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

      repository.createQuest(quest())

      assertEquals(listOf(0, 1), emissions.await().map { it.size })
    }
  }

  @Test
  fun observeQuestsByVenue_returnsAllOfThatVenuesQuestsWhateverTheirStatus() = runBlocking {
    val activeId = repository.createQuest(quest(venueId = "v1")).getOrThrow()
    val draftId =
        repository.createQuest(quest(venueId = "v1", status = QuestStatus.DRAFT)).getOrThrow()
    val archivedId =
        repository.createQuest(quest(venueId = "v1", status = QuestStatus.ARCHIVED)).getOrThrow()
    repository.createQuest(quest(venueId = "v2"))

    val quests = repository.observeQuestsByVenue("v1").first()

    assertEquals(setOf(activeId, draftId, archivedId), quests.map { it.id }.toSet())
  }

  @Test
  fun createQuest_returnsNewUniqueId() = runBlocking {
    val first = repository.createQuest(quest()).getOrThrow()
    val second = repository.createQuest(quest()).getOrThrow()

    assertTrue(first.isNotBlank())
    assertNotEquals(first, second)
  }

  @Test
  fun createQuest_ignoresCallerId() = runBlocking {
    val first = repository.createQuest(quest(id = "caller", title = "First")).getOrThrow()
    val second = repository.createQuest(quest(id = "caller", title = "Second")).getOrThrow()

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
        quest(
            title = "Find the mural",
            reward = Reward(description = "Free coffee", terms = "One per visit", expiresAt = 5L),
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
    val id = defaultRepository.createQuest(quest()).getOrThrow()
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

    val result = repository.createQuest(quest())

    assertSame(error, result.exceptionOrNull())
    assertTrue(repository.observeActiveQuests().first().isEmpty())
  }

  private companion object {
    /** The time the test clock always returns, in epoch milliseconds. */
    const val FIXED_NOW = 1_700_000_000_000L

    /** Fails a flow test instead of hanging if an expected emission never comes. */
    const val TIMEOUT_MS = 1_000L
  }
}
