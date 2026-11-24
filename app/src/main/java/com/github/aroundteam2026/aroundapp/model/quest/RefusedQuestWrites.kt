// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

import java.util.concurrent.TimeUnit

/**
 * The quest writes this venue made on this device, to tell which ones the server then refused, also
 * after the app was killed and opened again: what it learns goes to [store].
 *
 * Firestore lists a write at once, as pending, with the `updatedAt` the app gave it, and keeps it
 * across restarts until the server answers. A refused create then vanishes from the venue's quests,
 * and a refused edit or archive brings back the quest as the server has it, with its earlier
 * `updatedAt`. One the server accepted stays, with the `updatedAt` it was listed with. So a quest
 * listed as pending is a write of this device, remembered from then on, and one that was never
 * listed as pending can't be judged. A refusal is told about once, and a write older than two weeks
 * is forgotten. Not thread-safe: use it from the main thread, as a ViewModel does.
 *
 * @param clock The time now, in epoch milliseconds.
 */
class RefusedQuestWrites(
    private val store: RefusedQuestWritesStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
  /** The writes by quest id, until the server confirms them or the venue is told of the refusal. */
  private val writes = mutableMapOf<String, RecordedQuestWrite>()

  init {
    store.all().forEach {
      if (clock() - it.writtenAt > MAX_AGE_MS) store.remove(it.questId) else writes[it.questId] = it
    }
  }

  /** Whether the server refused a quest write that [markReported] hasn't covered yet. */
  val hasUnreportedRefusal: Boolean
    get() = writes.values.any { it.refused }

  /**
   * Judges the writes against the venue's latest [quests]. One that was listed as pending and now
   * is gone, or listed with another `updatedAt` than before, was refused; one listed with the same
   * `updatedAt` and no longer pending was confirmed.
   *
   * Only the venue's Quests tab hears, and it passes the live listener's latest list. Don't pass a
   * list older than one already heard, such as a cached one judged again: a quest listed as pending
   * since would read as refused. A second screen that listens on its own needs its own view of the
   * list first, as [com.github.aroundteam2026.aroundapp.model.reservation.RefusedWrites.Hearing]
   * gives for reservations.
   */
  fun hear(quests: List<Quest>) {
    val byId = quests.associateBy { it.id }
    quests.filter { it.pendingWrite }.forEach(::remember)
    for (write in writes.values.filter { !it.refused }) {
      val current = byId[write.questId]
      when {
        current?.pendingWrite == true -> Unit // Not answered yet
        current?.updatedAt == write.updatedAt -> forget(write.questId)
        else -> save(write.copy(refused = true))
      }
    }
  }

  /** Records that the venue was told about the refusals so far, so they aren't told again. */
  fun markReported() {
    writes.values.filter { it.refused }.forEach { forget(it.questId) }
  }

  /** Remembers the quest [pending], or its newer `updatedAt` if it was written again. */
  private fun remember(pending: Quest) {
    val known = writes[pending.id]
    // A refusal not told yet stays, and the write that follows it is remembered afterwards
    if (known == null || (!known.refused && known.updatedAt != pending.updatedAt)) {
      save(RecordedQuestWrite(pending.id, pending.updatedAt, writtenAt = clock()))
    }
  }

  private fun save(write: RecordedQuestWrite) {
    writes[write.questId] = write
    store.put(write)
  }

  private fun forget(questId: String) {
    writes.remove(questId)
    store.remove(questId)
  }

  private companion object {
    /** How long a write is remembered, so one that was never judged doesn't stay for ever. */
    val MAX_AGE_MS = TimeUnit.DAYS.toMillis(14)
  }
}
