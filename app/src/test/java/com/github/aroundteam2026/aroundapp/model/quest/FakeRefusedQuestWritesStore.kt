// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

/**
 * A [RefusedQuestWritesStore] that forgets everything when the test ends: no preferences needed.
 */
class FakeRefusedQuestWritesStore : RefusedQuestWritesStore {
  private val writes = linkedMapOf<String, RecordedQuestWrite>()

  override fun all() = writes.values.toList()

  override fun put(write: RecordedQuestWrite) {
    writes[write.questId] = write
  }

  override fun remove(questId: String) {
    writes.remove(questId)
  }
}

/** What a ViewModel under test remembers of the venue [venueId]'s writes, and forgets with it. */
@Suppress("UNUSED_PARAMETER")
fun fakeRefusedQuestWrites(venueId: String) = RefusedQuestWrites(FakeRefusedQuestWritesStore())
