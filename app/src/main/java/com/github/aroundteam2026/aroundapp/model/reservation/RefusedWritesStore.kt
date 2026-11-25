// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

/** Where a reservation this device wrote stands, as far as [RefusedWrites] has seen. */
enum class WriteState {
  /** Written, and not listed as pending yet. */
  WRITTEN,

  /** Listed as pending: if it vanishes now, the server refused it. */
  SHOWN,

  /** The server refused it; the quest shows "try again" until it is booked again. */
  REFUSED,

  /** Refused, and the explorer was told; the quest still shows "try again". */
  REPORTED,
}

/** A reservation of the quest [questId] that this device wrote, and where it [state] stands. */
data class RecordedWrite(val reservationId: String, val questId: String, val state: WriteState)

/**
 * What a device remembers of the reservations it wrote, for one explorer, so a refusal that comes
 * after the app was killed is still told apart. Firestore keeps the write itself across restarts,
 * but leaves no record of a refusal.
 */
interface RefusedWritesStore {
  /** Every write remembered. */
  fun all(): List<RecordedWrite>

  /** Remembers [write], replacing what was remembered for its reservation. */
  fun put(write: RecordedWrite)

  /** Forgets the write of the reservation [reservationId]. */
  fun remove(reservationId: String)

  /** Forgets every write, also one that can't be read any more. */
  fun clear()
}
