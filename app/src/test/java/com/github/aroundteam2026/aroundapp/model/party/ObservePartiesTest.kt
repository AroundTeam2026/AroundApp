// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.party

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests [observeParties] and [isJoined] on a [FakePartyRepository]. */
class ObservePartiesTest {
  private var time = 0L
  private val party = FakePartyRepository { ++time }

  /** The first parties [observeParties] gives for [ids], or a failure instead of a hang. */
  private suspend fun partiesOf(vararg ids: String) =
      withTimeout(TIMEOUT_MS) { party.observeParties(ids.toList()).first() }

  @Test
  fun givesEveryMembershipOfEachPartyByReservation() = runBlocking {
    party.join("r1", "ada").getOrThrow()
    party.join("r1", "bea").getOrThrow()
    party.join("r2", "cleo").getOrThrow()
    party.withdraw("r1", "bea").getOrThrow()

    val parties = partiesOf("r1", "r2", "r1")

    assertEquals(setOf("r1", "r2"), parties.keys)
    assertEquals(setOf("ada", "bea"), parties.getValue("r1").map { it.uid }.toSet())
    assertEquals(listOf("cleo"), parties.getValue("r2").map { it.uid })
  }

  @Test
  fun givesAnEmptyPartyForAReservationNobodyJoined() = runBlocking {
    assertEquals(mapOf("r1" to emptyList<PartyMember>()), partiesOf("r1"))
  }

  @Test
  fun givesAnEmptyMapAtOnceWithoutReservations() = runBlocking {
    assertEquals(emptyMap<String, List<PartyMember>>(), partiesOf())
  }

  @Test
  fun emitsAgainWhenSomeoneJoins() =
      runBlocking<Unit> {
        party.join("r1", "ada").getOrThrow()
        val subscribed = CompletableDeferred<Unit>()
        val emissions = async {
          party.observeParties(listOf("r1")).onEach { subscribed.complete(Unit) }.take(2).toList()
        }
        withTimeout(TIMEOUT_MS) { subscribed.await() }

        party.join("r1", "bea").getOrThrow()

        assertEquals(
            listOf(listOf("ada"), listOf("ada", "bea")),
            withTimeout(TIMEOUT_MS) { emissions.await() }
                .map { parties -> parties.getValue("r1").map { it.uid }.sorted() },
        )
      }

  @Test
  fun aWithdrawnMemberIsNoLongerJoined() = runBlocking {
    party.join("r1", "ada").getOrThrow()
    party.join("r1", "bea").getOrThrow()
    party.withdraw("r1", "bea").getOrThrow()

    val members = partiesOf("r1").getValue("r1").associateBy { it.uid }

    assertTrue(members.getValue("ada").isJoined)
    assertFalse(members.getValue("bea").isJoined)
  }

  private companion object {
    const val TIMEOUT_MS = 5_000L
  }
}
