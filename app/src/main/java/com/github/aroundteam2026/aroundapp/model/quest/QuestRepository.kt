// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

import kotlinx.coroutines.flow.Flow

/** Reads and writes [Quest]s. ViewModels depend on this interface only. */
interface QuestRepository {
  /**
   * Observes the quests explorers can see. Emits the current list on collection, then again
   * whenever a quest is added or changes.
   *
   * @return every quest whose status is [QuestStatus.ACTIVE], in unspecified order. Callers that
   *   need an order must sort the list themselves.
   */
  fun observeActiveQuests(): Flow<List<Quest>>

  /**
   * Observes one venue's quests, for its "My quests" list. Emits on collection and on every change.
   *
   * @param venueId id of the venue whose quests to return.
   * @return all of that venue's quests, whatever their status (including drafts), in unspecified
   *   order. Callers that need an order must sort the list themselves.
   */
  fun observeQuestsByVenue(venueId: String): Flow<List<Quest>>

  /**
   * Fetches a single quest once.
   *
   * @param questId id of the quest.
   * @return the quest, or null if no quest has this id.
   */
  suspend fun getQuest(questId: String): Quest?

  /**
   * Stores a new quest. The repository, not the caller, sets `id`, `createdAt` and `updatedAt`: it
   * generates a fresh id and sets both timestamps to the creation time. The values of those three
   * fields in [quest] are ignored.
   *
   * @param quest the quest to store; its `id`, `createdAt` and `updatedAt` are ignored.
   * @return the new quest's id, or a failure if it could not be stored.
   */
  suspend fun createQuest(quest: Quest): Result<String>
}
