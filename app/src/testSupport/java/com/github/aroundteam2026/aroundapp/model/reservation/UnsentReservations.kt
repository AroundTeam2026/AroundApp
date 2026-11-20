// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * This repository, with the explorer's reservations whose ids are in [unsent] listed as not
 * confirmed by the server yet ([Reservation.pendingWrite]), for tests of what a screen shows while
 * a write is queued. [unsent] changing is the server confirming one.
 */
fun ReservationRepository.withUnsent(unsent: Flow<Set<String>>): ReservationRepository {
  val inner = this
  return object : ReservationRepository by inner {
    override fun observeForExplorer(uid: String): Flow<List<Reservation>> =
        inner.observeForExplorer(uid).combine(unsent) { reservations, ids ->
          reservations.map { it.copy(pendingWrite = it.id in ids) }
        }
  }
}
