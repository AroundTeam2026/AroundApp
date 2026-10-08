// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

import com.github.aroundteam2026.aroundapp.model.demo.MapDemoData

/**
 * Builds the app's [QuestRepository], so ViewModels never depend on an implementation.
 *
 * Until the app reads quests from Firestore, it shows the demo quests, kept in memory and shared by
 * every screen.
 */
object QuestRepositoryProvider {
  /** The app's quests: the demo quests, dated from when they were first asked for. */
  val repository: QuestRepository by lazy { create(System::currentTimeMillis) }

  /** A new in-memory repository holding the demo quests, dated from [now]. */
  fun create(now: () -> Long): QuestRepository = FakeQuestRepository(now, MapDemoData.quests(now()))
}
