// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tests the default [CompletionRepository.observeForReservations] on a [FakeCompletionRepository].
 */
class ObserveForReservationsTest {
  private val completions = FakeCompletionRepository(now = { 0L })

  /** Sends the proof of the reservation [reservationId] as [sender]; returns its id. */
  private suspend fun sendProof(reservationId: String, sender: String = SENDER): String =
      completions.submit(reservationId, VENUE_ID, sender, PROOF).getOrThrow()

  /** Each party's proof is there whichever member sent it, and other parties' are left out. */
  @Test
  fun returnsTheProofOfEachReservationOnly() =
      runBlocking<Unit> {
        val first = sendProof("r1")
        val second = sendProof("r2", sender = "friend")
        sendProof("r3")

        assertEquals(
            setOf(first, second),
            completions.observeForReservations(listOf("r1", "r2")).first().map { it.id }.toSet(),
        )
      }

  /** Without ids there is nothing to wait for: a combine of no flows would never emit. */
  @Test
  fun noReservations_emitsAnEmptyList() =
      runBlocking<Unit> {
        val proofs =
            withTimeout(TIMEOUT_MS) { completions.observeForReservations(emptyList()).first() }

        assertEquals(emptyList<Completion>(), proofs)
      }

  @Test
  fun emitsAgainWhenAMemberSendsTheProof() =
      runBlocking<Unit> {
        val subscribed = CompletableDeferred<Unit>()
        val emissions = async {
          completions
              .observeForReservations(listOf("r1"))
              .onEach { subscribed.complete(Unit) }
              .take(2)
              .toList()
        }
        withTimeout(TIMEOUT_MS) { subscribed.await() }

        val id = sendProof("r1", sender = "friend")

        assertEquals(
            listOf(emptyList(), listOf(id)),
            withTimeout(TIMEOUT_MS) { emissions.await() }.map { list -> list.map { it.id } },
        )
      }

  /** A reservation whose listener fails, e.g. after sign-out, fails the whole flow. */
  @Test
  fun aFailingReservation_failsTheFlow() {
    val failure = IllegalStateException("permission denied")
    val broken =
        object : CompletionRepository by completions {
          override fun observeForReservation(reservationId: String): Flow<List<Completion>> =
              if (reservationId == "r2") flow { throw failure }
              else completions.observeForReservation(reservationId)

          // Delegation would hand this to completions, around the failing listener above
          override fun observeForReservations(
              reservationIds: Collection<String>
          ): Flow<List<Completion>> = super.observeForReservations(reservationIds)
        }

    val thrown =
        assertThrows(IllegalStateException::class.java) {
          runBlocking { broken.observeForReservations(listOf("r1", "r2")).first() }
        }

    // Coroutines may rethrow a copy of the exception, so it is told apart by its message
    assertEquals(failure.message, thrown.message)
  }

  private companion object {
    const val TIMEOUT_MS = 5_000L
    const val VENUE_ID = "v1"
    const val SENDER = "u1"
    val PROOF = Proof.Text("The answer")
  }
}
