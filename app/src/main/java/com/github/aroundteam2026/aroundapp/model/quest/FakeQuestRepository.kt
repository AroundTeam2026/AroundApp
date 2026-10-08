// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [QuestRepository] for unit tests and for running the app without Firestore.
 *
 * Quests are kept in insertion order in a [MutableStateFlow]. Each `observe*` flow emits again only
 * when its own result changes, not on every write. Ids are generated as `quest-1`, `quest-2`, ...
 * and are unique per instance. Not thread-safe; use one instance per test.
 *
 * @param now Clock used for `createdAt` and `updatedAt`, in epoch milliseconds. Pass a fixed value
 *   in tests to make timestamps predictable.
 * @param initialQuests Quests stored from the start exactly as given, ids and timestamps included.
 *   Their ids must be unique; generated ids never reuse them.
 */
class FakeQuestRepository(
    private val now: () -> Long = System::currentTimeMillis,
    initialQuests: List<Quest> = emptyList(),
) : QuestRepository {
  /**
   * Simulates a backend error (e.g. no network or a rejected write) so callers' error paths can be
   * tested. While non-null, [createQuest] returns `Result.failure(forcedFailure)` and stores
   * nothing. Set it back to null to make writes succeed again.
   */
  var forcedFailure: Throwable? = null

  /** All stored quests, keyed by id. */
  private val quests = MutableStateFlow(initialQuests.associateBy { it.id })

  init {
    require(quests.value.size == initialQuests.size) { "Initial quests must have unique ids" }
  }

  /** Number used for the next generated id. */
  private var nextId = 1

  /** Emits the stored [QuestStatus.ACTIVE] quests, and again whenever that list changes. */
  override fun observeActiveQuests(): Flow<List<Quest>> =
      quests
          .map { all -> all.values.filter { it.status == QuestStatus.ACTIVE } }
          .distinctUntilChanged()

  /**
   * Emits every stored quest of [venueId], drafts included, and again whenever that list changes.
   */
  override fun observeQuestsByVenue(venueId: String): Flow<List<Quest>> =
      quests.map { all -> all.values.filter { it.venueId == venueId } }.distinctUntilChanged()

  /** Returns the stored quest with [questId], or null if there is none. */
  override suspend fun getQuest(questId: String): Quest? = quests.value[questId]

  /**
   * Stores a copy of [quest] with a new id and both timestamps set to [now], and returns that id.
   * Returns [forcedFailure] instead, storing nothing, if it is set.
   */
  override suspend fun createQuest(quest: Quest): Result<String> {
    forcedFailure?.let {
      return Result.failure(it)
    }
    val id = generateSequence { "quest-${nextId++}" }.first { it !in quests.value }
    val time = now()
    quests.update { it + (id to quest.copy(id = id, createdAt = time, updatedAt = time)) }
    return Result.success(id)
  }
}
