// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

/** A [RefusedWritesStore] that forgets everything when the test ends: no preferences needed. */
class FakeRefusedWritesStore : RefusedWritesStore {
  private val writes = linkedMapOf<String, RecordedWrite>()

  override fun all() = writes.values.toList()

  override fun put(write: RecordedWrite) {
    writes[write.reservationId] = write
  }

  override fun remove(reservationId: String) {
    writes.remove(reservationId)
  }

  override fun clear() {
    writes.clear()
  }
}

/** What a ViewModel under test remembers, and forgets with the test. */
fun fakeRefusedWrites() = RefusedWrites(FakeRefusedWritesStore())
