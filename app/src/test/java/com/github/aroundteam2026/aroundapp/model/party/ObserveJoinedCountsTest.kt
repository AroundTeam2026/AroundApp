// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.party

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

/** Tests [observeJoinedCounts] on a [FakePartyRepository]. */
class ObserveJoinedCountsTest {
  private val party = FakePartyRepository(now = { 0L })

  /** A withdrawn member doesn't count, and each party is counted on its own. */
  @Test
  fun countsOnlyTheJoinedMembersOfEachParty() =
      runBlocking<Unit> {
        party.join("r1", "u1").getOrThrow()
        party.join("r1", "u2").getOrThrow()
        party.join("r1", "u3").getOrThrow()
        party.withdraw("r1", "u3").getOrThrow()
        party.join("r2", "u1").getOrThrow()

        assertEquals(
            mapOf("r1" to 2, "r2" to 1),
            party.observeJoinedCounts(listOf("r1", "r2")).first(),
        )
      }

  /** Every id asked for is in the map, so callers never have to guess a missing party's size. */
  @Test
  fun aPartyWithNoMembers_countsZero() =
      runBlocking<Unit> {
        assertEquals(mapOf("r1" to 0), party.observeJoinedCounts(listOf("r1")).first())
      }

  /** Without ids there is nothing to wait for: a combine of no flows would never emit. */
  @Test
  fun noReservations_emitsAnEmptyMap() =
      runBlocking<Unit> {
        val counts = withTimeout(TIMEOUT_MS) { party.observeJoinedCounts(emptyList()).first() }

        assertEquals(emptyMap<String, Int>(), counts)
      }

  @Test
  fun emitsAgainWhenAMemberWithdraws() =
      runBlocking<Unit> {
        party.join("r1", "u1").getOrThrow()
        party.join("r1", "u2").getOrThrow()
        val subscribed = CompletableDeferred<Unit>()
        val emissions = async {
          party
              .observeJoinedCounts(listOf("r1"))
              .onEach { subscribed.complete(Unit) }
              .take(2)
              .toList()
        }
        withTimeout(TIMEOUT_MS) { subscribed.await() }

        party.withdraw("r1", "u2").getOrThrow()

        assertEquals(
            listOf(mapOf("r1" to 2), mapOf("r1" to 1)),
            withTimeout(TIMEOUT_MS) { emissions.await() },
        )
      }

  /** A party whose listener fails, e.g. after sign-out, fails the whole flow. */
  @Test
  fun aFailingParty_failsTheFlow() {
    val failure = IllegalStateException("permission denied")
    val broken =
        object : PartyRepository by party {
          override fun observeMembers(reservationId: String): Flow<List<PartyMember>> =
              if (reservationId == "r2") flow { throw failure }
              else party.observeMembers(reservationId)
        }

    val thrown =
        assertThrows(IllegalStateException::class.java) {
          runBlocking { broken.observeJoinedCounts(listOf("r1", "r2")).first() }
        }

    // Coroutines may rethrow a copy of the exception, so it is told apart by its message
    assertEquals(failure.message, thrown.message)
  }

  private companion object {
    const val TIMEOUT_MS = 5_000L
  }
}
