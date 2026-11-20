// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * This repository, with the venue's quests whose ids are in [unsent] listed as not confirmed by the
 * server yet ([Quest.pendingWrite]), for tests of what a screen shows while a write is queued.
 * [unsent] changing is the server confirming one.
 */
fun QuestRepository.withUnsent(unsent: Flow<Set<String>>): QuestRepository {
  val inner = this
  return object : QuestRepository by inner {
    override fun observeQuestsByVenue(venueId: String): Flow<List<Quest>> =
        inner.observeQuestsByVenue(venueId).combine(unsent) { quests, ids ->
          quests.map { it.copy(pendingWrite = it.id in ids) }
        }
  }
}
