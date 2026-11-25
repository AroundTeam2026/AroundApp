// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * This repository, with the completions of the reservations whose ids are in [unsent] listed as not
 * confirmed by the server yet ([Completion.pendingWrite]), for tests of what a screen shows while a
 * proof is queued. [unsent] changing is the server confirming one.
 */
fun CompletionRepository.withUnsent(unsent: Flow<Set<String>>): CompletionRepository {
  val inner = this
  return object : CompletionRepository by inner {
    override fun observeForReservation(reservationId: String): Flow<List<Completion>> =
        inner.observeForReservation(reservationId).combine(unsent, ::mark)

    override fun observeForReservations(
        reservationIds: Collection<String>
    ): Flow<List<Completion>> = inner.observeForReservations(reservationIds).combine(unsent, ::mark)

    private fun mark(completions: List<Completion>, ids: Set<String>) = completions.map {
      it.copy(pendingWrite = it.reservationId in ids)
    }
  }
}
