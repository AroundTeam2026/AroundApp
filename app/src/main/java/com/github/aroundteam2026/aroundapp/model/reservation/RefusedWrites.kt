// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

/**
 * The reservations a screen created, to tell which ones the server then refused.
 *
 * Firestore lists a write at once, as pending, before the server accepts or refuses it. A refused
 * write then vanishes from the explorer's reservations, while one the venue answered or the
 * explorer cancelled stays listed with its new status. Not thread-safe: use it from the main
 * thread, as a ViewModel does.
 */
class RefusedWrites {
  private class Write(val questId: String, var shown: Boolean = false)

  /** The reservations recorded and not settled yet, by reservation id. */
  private val written = mutableMapOf<String, Write>()

  /** Remembers that the screen created the reservation [reservationId] for the quest [questId]. */
  fun record(reservationId: String, questId: String) {
    written[reservationId] = Write(questId)
  }

  /** Forgets every reservation recorded. */
  fun clear() = written.clear()

  /**
   * The quests whose recorded reservation the server refused, given the explorer's latest
   * [reservations]: it was listed as pending, then vanished, e.g. because the quest was deactivated
   * meanwhile. A reservation not listed yet is not refused, nor one that the venue answered or the
   * explorer cancelled. Each refusal is reported once.
   */
  fun refusedIn(reservations: List<Reservation>): Set<String> {
    val byId = reservations.associateBy { it.id }
    val refused = mutableSetOf<String>()
    val writes = written.iterator()
    while (writes.hasNext()) {
      val (id, write) = writes.next()
      val current = byId[id]
      when {
        current == null && write.shown -> {
          refused += write.questId
          writes.remove()
        }
        current == null -> Unit
        current.status == ReservationStatus.PENDING -> write.shown = true
        else -> writes.remove()
      }
    }
    return refused
  }
}
