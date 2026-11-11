// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.party

import kotlinx.coroutines.flow.first
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

  @Test
  fun givesEveryMembershipOfEachPartyByReservation() = runBlocking {
    party.join("r1", "ada").getOrThrow()
    party.join("r1", "bea").getOrThrow()
    party.join("r2", "cleo").getOrThrow()
    party.withdraw("r1", "bea").getOrThrow()

    val parties = party.observeParties(listOf("r1", "r2", "r1")).first()

    assertEquals(setOf("r1", "r2"), parties.keys)
    assertEquals(setOf("ada", "bea"), parties.getValue("r1").map { it.uid }.toSet())
    assertEquals(listOf("cleo"), parties.getValue("r2").map { it.uid })
  }

  @Test
  fun givesAnEmptyPartyForAReservationNobodyJoined() = runBlocking {
    assertEquals(
        mapOf("r1" to emptyList<PartyMember>()),
        party.observeParties(listOf("r1")).first(),
    )
  }

  @Test
  fun givesAnEmptyMapAtOnceWithoutReservations() = runBlocking {
    assertEquals(
        emptyMap<String, List<PartyMember>>(),
        withTimeout(TIMEOUT_MS) { party.observeParties(emptyList()).first() },
    )
  }

  @Test
  fun aWithdrawnMemberIsNoLongerJoined() = runBlocking {
    party.join("r1", "ada").getOrThrow()
    party.join("r1", "bea").getOrThrow()
    party.withdraw("r1", "bea").getOrThrow()

    val members = party.observeParties(listOf("r1")).first().getValue("r1").associateBy { it.uid }

    assertTrue(members.getValue("ada").isJoined)
    assertFalse(members.getValue("bea").isJoined)
  }

  private companion object {
    const val TIMEOUT_MS = 1_000L
  }
}
