// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.party

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests [FakePartyRepository] against the [PartyRepository] contract, one behaviour per test. */
class FakePartyRepositoryTest {
  private var clock = JOINED_AT
  private lateinit var repository: FakePartyRepository

  /** Gives every test a fresh, empty repository on a clock the test can move. */
  @Before
  fun setUp() {
    repository = FakePartyRepository(now = { clock })
  }

  /** The party of [reservationId] as it is now. */
  private suspend fun party(reservationId: String = "r1") =
      repository.observeMembers(reservationId).first()

  @Test
  fun join_storesAJoinedMembershipAtTheCurrentTime() =
      runBlocking<Unit> {
        repository.join("r1", "u1").getOrThrow()

        assertEquals(
            listOf(PartyMember("r1", "u1", PartyMemberStatus.JOINED, JOINED_AT, JOINED_AT)),
            party(),
        )
      }

  @Test
  fun join_twice_failsAndKeepsTheFirstMembership() =
      runBlocking<Unit> {
        repository.join("r1", "u1").getOrThrow()
        clock = WITHDRAWN_AT

        assertTrue(repository.join("r1", "u1").exceptionOrNull() is IllegalStateException)
        assertEquals(JOINED_AT, party().single().joinedAt)
      }

  /** A withdrawn explorer still has a membership, so they can't join that party again. */
  @Test
  fun join_afterWithdrawing_fails() =
      runBlocking<Unit> {
        repository.join("r1", "u1").getOrThrow()
        repository.withdraw("r1", "u1").getOrThrow()

        assertTrue(repository.join("r1", "u1").isFailure)
        assertEquals(PartyMemberStatus.WITHDRAWN, party().single().status)
      }

  @Test
  fun withdraw_keepsTheMembershipAndRecordsWhen() =
      runBlocking<Unit> {
        repository.join("r1", "u1").getOrThrow()
        clock = WITHDRAWN_AT

        repository.withdraw("r1", "u1").getOrThrow()

        assertEquals(
            listOf(PartyMember("r1", "u1", PartyMemberStatus.WITHDRAWN, JOINED_AT, WITHDRAWN_AT)),
            party(),
        )
      }

  @Test
  fun withdraw_twice_failsWithIllegalState() =
      runBlocking<Unit> {
        repository.join("r1", "u1").getOrThrow()
        repository.withdraw("r1", "u1").getOrThrow()

        assertTrue(repository.withdraw("r1", "u1").exceptionOrNull() is IllegalStateException)
      }

  @Test
  fun withdraw_withoutAMembership_failsWithNoSuchElement() =
      runBlocking<Unit> {
        repository.join("r1", "u1").getOrThrow()

        assertTrue(repository.withdraw("r1", "u2").exceptionOrNull() is NoSuchElementException)
        assertTrue(repository.withdraw("r2", "u1").exceptionOrNull() is NoSuchElementException)
      }

  @Test
  fun forcedFailure_failsEveryWriteAndChangesNothing() =
      runBlocking<Unit> {
        repository.join("r1", "u1").getOrThrow()
        val failure = IllegalStateException("offline")
        repository.forcedFailure = failure

        assertEquals(failure, repository.join("r1", "u2").exceptionOrNull())
        assertEquals(failure, repository.withdraw("r1", "u1").exceptionOrNull())
        assertEquals(listOf(PartyMemberStatus.JOINED), party().map { it.status })
      }

  @Test
  fun observeMembers_returnsOnlyThatReservationsParty() =
      runBlocking<Unit> {
        repository.join("r1", "u1").getOrThrow()
        repository.join("r1", "u2").getOrThrow()
        repository.join("r2", "u1").getOrThrow()

        assertEquals(setOf("u1", "u2"), party().map { it.uid }.toSet())
      }

  /** Another party changing must not make this party's flow emit again. */
  @Test
  fun observeMembers_emitsOnlyWhenThatPartyChanges() =
      runBlocking<Unit> {
        val emissions =
            async(start = CoroutineStart.UNDISPATCHED) {
              repository.observeMembers("r1").take(2).toList()
            }

        // Yielding lets the collector see each change, which would otherwise be conflated
        repository.join("r2", "u1").getOrThrow()
        yield()
        repository.join("r1", "u1").getOrThrow()

        val uids = withTimeout(TIMEOUT_MS) { emissions.await() }.map { it.map(PartyMember::uid) }
        assertEquals(listOf(emptyList(), listOf("u1")), uids)
      }

  private companion object {
    const val JOINED_AT = 1_000L
    const val WITHDRAWN_AT = 2_000L
    const val TIMEOUT_MS = 1_000L
  }
}
