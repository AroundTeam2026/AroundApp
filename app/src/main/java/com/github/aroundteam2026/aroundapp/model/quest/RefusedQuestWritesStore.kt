// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

/**
 * A write of the quest [questId] that this device made and the server hadn't answered when it was
 * last listed: [updatedAt] is the quest's `updatedAt` as listed then, [writtenAt] when that was in
 * epoch milliseconds, and [refused] whether the server turned it down since.
 */
data class RecordedQuestWrite(
    val questId: String,
    val updatedAt: Long,
    val writtenAt: Long,
    val refused: Boolean = false,
)

/**
 * What a device remembers of the quest writes of one venue, so a refusal that comes after the app
 * was killed is still told apart. Firestore keeps the write itself across restarts, but leaves no
 * record of a refusal.
 */
interface RefusedQuestWritesStore {
  /** Every write remembered. */
  fun all(): List<RecordedQuestWrite>

  /** Remembers [write], replacing what was remembered for its quest. */
  fun put(write: RecordedQuestWrite)

  /** Forgets the write of the quest [questId]. */
  fun remove(questId: String)
}
