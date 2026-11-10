// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * A [QuestRepository] whose every answer the test chooses: flows that emit, change or fail on cue.
 * It records the calls it gets.
 */
class ScriptedQuestRepository(
    var activeQuests: Flow<List<Quest>> = flowOf(emptyList()),
    var questsByVenue: (String) -> Flow<List<Quest>> = { flowOf(emptyList()) },
    var activeQuestsByVenue: (String) -> Flow<List<Quest>> = { flowOf(emptyList()) },
    var quest: suspend (String) -> Quest? = { null },
    var create: suspend (Quest) -> Result<String> = { Result.success("created") },
    var update: suspend (Quest) -> Result<Unit> = { Result.success(Unit) },
) : QuestRepository {
  val created = mutableListOf<Quest>()
  val updated = mutableListOf<Quest>()
  var activeQuestsCollections = 0

  override fun observeActiveQuests(): Flow<List<Quest>> {
    activeQuestsCollections++
    return activeQuests
  }

  override fun observeQuestsByVenue(venueId: String) = questsByVenue(venueId)

  override fun observeActiveQuestsByVenue(venueId: String) = activeQuestsByVenue(venueId)

  override suspend fun getQuest(questId: String) = quest(questId)

  override suspend fun createQuest(quest: Quest): Result<String> {
    created += quest
    return create(quest)
  }

  override suspend fun updateQuest(quest: Quest): Result<Unit> {
    updated += quest
    return update(quest)
  }

  companion object {
    /** A repository whose active quests are [quests], which the test can change at any time. */
    fun of(quests: MutableStateFlow<List<Quest>>) = ScriptedQuestRepository(activeQuests = quests)
  }
}
