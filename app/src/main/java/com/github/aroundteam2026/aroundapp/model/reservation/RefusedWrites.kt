// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

/**
 * The reservations this device created, to tell which ones the server then refused, also after the
 * app was killed and opened again: what it learns goes to [store].
 *
 * Firestore lists a write at once, as pending, before the server accepts or refuses it, and keeps a
 * write that wasn't sent yet across restarts. A refused write then vanishes from the explorer's
 * reservations, while one the venue answered or the explorer cancelled stays listed with its new
 * status. A refusal is remembered until the quest is booked again or another reservation of it
 * stands, so a screen opened after the restart can still say "try again", and whether the explorer
 * was told about it is remembered too. Not thread-safe: use it from the main thread, as a ViewModel
 * does.
 */
class RefusedWrites(private val store: RefusedWritesStore) {
  /** The writes not settled yet, by reservation id. */
  private val written = mutableMapOf<String, RecordedWrite>()

  /** The writes the server refused, by reservation id. */
  private val refused = mutableMapOf<String, RecordedWrite>()

  /**
   * The writes a screen listed as pending before this run, by reservation id. No list a screen
   * hears in this run is older than that, so any screen may refuse them.
   */
  private val listedBefore = mutableSetOf<String>()

  init {
    load()
  }

  /** The quests whose reservation the server refused, until they are booked again. */
  val refusedQuests: Set<String>
    get() = refused.values.mapTo(mutableSetOf()) { it.questId }

  /** Whether the server refused a reservation that [markReported] hasn't covered yet. */
  val hasUnreportedRefusal: Boolean
    get() = refused.values.any { it.state == WriteState.REFUSED }

  /**
   * Records that the explorer was told about the refusals so far, so they aren't told again after a
   * restart. The quests keep showing "try again" in [refusedQuests].
   */
  fun markReported() {
    refused.values
        .filter { it.state == WriteState.REFUSED }
        .forEach { save(it.copy(state = WriteState.REPORTED)) }
  }

  /**
   * Remembers that this device created the reservation [reservationId] for the quest [questId],
   * which ends an earlier refusal of that quest.
   */
  fun record(reservationId: String, questId: String) {
    refused.values.filter { it.questId == questId }.forEach { forget(it.reservationId) }
    save(RecordedWrite(reservationId, questId, WriteState.WRITTEN))
  }

  /**
   * What one screen hears of the explorer's reservations. Screens share a [RefusedWrites] but each
   * listens on its own, and one can still hold an older list than another. So a write made in this
   * run is only refused when the screen judging it listed it as pending itself, then saw it vanish.
   * One listed before the run is refused by any screen: its first list may come after the server
   * refused it, say when the explorer was on another screen.
   */
  inner class Hearing internal constructor() {
    private val pending = mutableSetOf<String>()

    /**
     * The quests whose recorded reservation the server newly refused, given the explorer's latest
     * [reservations]: it was listed as pending, then vanished, e.g. because the quest was
     * deactivated meanwhile. A reservation not listed yet is not refused, nor one that the venue
     * answered or the explorer cancelled. Each refusal is reported once, and stays in
     * [refusedQuests].
     */
    fun refusedIn(reservations: List<Reservation>): Set<String> {
      reservations.filter { it.status == ReservationStatus.PENDING }.mapTo(pending) { it.id }
      return judge(reservations, pending + listedBefore)
    }
  }

  /** A screen's own view of the reservations, to judge refusals by. */
  fun hearing() = Hearing()

  private fun judge(reservations: List<Reservation>, heard: Set<String>): Set<String> {
    val byId = reservations.associateBy { it.id }
    val standing = reservations.filter { it.status.isActive }.map { it.questId }.toSet()
    refused.values.filter { it.questId in standing }.forEach { forget(it.reservationId) }
    val newlyRefused = mutableSetOf<String>()
    for (write in written.values.toList()) {
      val current = byId[write.reservationId]
      when {
        current == null && write.state == WriteState.SHOWN && write.reservationId in heard -> {
          newlyRefused += write.questId
          save(write.copy(state = WriteState.REFUSED))
        }
        current == null -> Unit // Not listed yet, or by a screen with an older list
        current.status == ReservationStatus.PENDING -> {
          if (write.state != WriteState.SHOWN) save(write.copy(state = WriteState.SHOWN))
        }
        else -> forget(write.reservationId)
      }
    }
    return newlyRefused
  }

  private fun load() {
    written.clear()
    refused.clear()
    val remembered = store.all()
    remembered.forEach(::track)
    remembered.filter { it.state == WriteState.SHOWN }.mapTo(listedBefore) { it.reservationId }
  }

  private fun save(write: RecordedWrite) {
    track(write)
    store.put(write)
  }

  /** Files [write] under what is unsettled or refused, whichever its state says. */
  private fun track(write: RecordedWrite) {
    val refusedNow = write.state == WriteState.REFUSED || write.state == WriteState.REPORTED
    (if (refusedNow) refused else written)[write.reservationId] = write
    (if (refusedNow) written else refused).remove(write.reservationId)
  }

  private fun forget(reservationId: String) {
    written.remove(reservationId)
    refused.remove(reservationId)
    store.remove(reservationId)
  }
}
