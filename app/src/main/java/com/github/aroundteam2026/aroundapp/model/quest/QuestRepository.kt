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
   * @throws Exception from the flow if the backend stops the listener (e.g. permission denied after
   *   sign-out). Collectors must catch it, e.g. with `.catch`, or it crashes their scope.
   */
  fun observeActiveQuests(): Flow<List<Quest>>

  /**
   * Observes one venue's quests, for its "My quests" list. Emits on collection and on every change.
   *
   * @param venueId id of the venue whose quests to return.
   * @return all of that venue's quests, whatever their status (including drafts), in unspecified
   *   order. Callers that need an order must sort the list themselves.
   * @throws Exception from the flow if the backend stops the listener (e.g. permission denied after
   *   sign-out). Collectors must catch it, e.g. with `.catch`, or it crashes their scope.
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
   * @return the new quest's id, or a failure if the write could not be issued. Success does not
   *   wait for the backend to confirm the write (e.g. while offline), and a later rejection is not
   *   reported here.
   */
  suspend fun createQuest(quest: Quest): Result<String>
}
