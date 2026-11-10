// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import com.github.aroundteam2026.aroundapp.model.party.FakePartyRepository
import com.github.aroundteam2026.aroundapp.model.party.MAX_PARTY_SIZE
import com.github.aroundteam2026.aroundapp.model.party.PartyMember
import com.github.aroundteam2026.aroundapp.model.party.PartyMemberStatus
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.testQuest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests [FakeReservationRepository] against the [ReservationRepository] contract, one behaviour per
 * test.
 */
class FakeReservationRepositoryTest {
  private lateinit var party: FakePartyRepository
  private lateinit var repository: FakeReservationRepository

  /**
   * Gives every test a fresh, empty repository whose clock always returns [FIXED_NOW], with its
   * parties in [party], so tests can make other explorers join or withdraw.
   */
  @Before
  fun setUp() {
    party = FakePartyRepository(now = { FIXED_NOW })
    repository = FakeReservationRepository(now = { FIXED_NOW }, party = party)
  }

  /**
   * Builds a reservation with a blank id by default and values that differ from what the repository
   * sets, so tests can tell the caller's values from the repository's.
   */
  private fun reservation(
      id: String = "",
      venueId: String = "v1",
      status: ReservationStatus = ReservationStatus.PENDING,
      createdAt: Long = 1_000L,
  ) =
      Reservation(
          id = id,
          questId = "q1",
          venueId = venueId,
          slotStart = 5_000L,
          status = status,
          createdAt = createdAt,
          explorerName = "Ada",
      )

  @Test
  fun createReservation_assignsAnIdAndTheCreationTime() =
      runBlocking<Unit> {
        val id = repository.createReservation(reservation(), "u1").getOrThrow()

        val stored = repository.observeForVenue("v1").first().single()
        assertEquals(id, stored.id)
        assertEquals(FIXED_NOW, stored.createdAt)
      }

  @Test
  fun createReservation_makesTheCreatorAJoinedMember() =
      runBlocking<Unit> {
        val id = repository.createReservation(reservation(), "u1").getOrThrow()

        assertEquals(
            listOf(PartyMember(id, "u1", PartyMemberStatus.JOINED, FIXED_NOW, FIXED_NOW)),
            party.observeMembers(id).first(),
        )
      }

  @Test
  fun createReservation_keepsTheExplorerName() =
      runBlocking<Unit> {
        repository.createReservation(reservation(), "u1").getOrThrow()

        assertEquals("Ada", repository.observeForVenue("v1").first().single().explorerName)
      }

  @Test
  fun createReservation_ignoresTheCallersIdCreatedAtAndStatus() =
      runBlocking<Unit> {
        val id =
            repository
                .createReservation(
                    reservation(id = "caller", status = ReservationStatus.APPROVED, createdAt = 1L),
                    "u1",
                )
                .getOrThrow()

        val stored = repository.observeForVenue("v1").first().single()
        assertNotEquals("caller", id)
        assertEquals(id, stored.id)
        assertEquals(ReservationStatus.PENDING, stored.status)
        assertEquals(FIXED_NOW, stored.createdAt)
      }

  @Test
  fun createReservation_generatesADifferentIdEachTime() =
      runBlocking<Unit> {
        val first = repository.createReservation(reservation(), "u1").getOrThrow()
        val second = repository.createReservation(reservation(), "u1").getOrThrow()

        assertNotEquals(first, second)
        assertEquals(2, repository.observeForVenue("v1").first().size)
      }

  @Test
  fun createReservation_whenFailureIsForced_returnsItAndStoresNothing() =
      runBlocking<Unit> {
        val failure = IllegalStateException("offline")
        repository.forcedFailure = failure

        assertEquals(failure, repository.createReservation(reservation(), "u1").exceptionOrNull())
        assertTrue(repository.observeForVenue("v1").first().isEmpty())
        assertTrue(party.observeMembers("reservation-1").first().isEmpty())
      }

  @Test
  fun createReservation_whenJoiningFails_returnsItAndStoresNoReservation() =
      runBlocking<Unit> {
        val failure = IllegalStateException("offline")
        party.forcedFailure = failure

        assertEquals(failure, repository.createReservation(reservation(), "u1").exceptionOrNull())
        assertTrue(repository.observeForVenue("v1").first().isEmpty())
      }

  @Test
  fun observeForVenue_returnsOnlyThatVenuesReservations() =
      runBlocking<Unit> {
        val mine = repository.createReservation(reservation(venueId = "v1"), "u1").getOrThrow()
        repository.createReservation(reservation(venueId = "v2"), "u1").getOrThrow()

        assertEquals(listOf(mine), repository.observeForVenue("v1").first().map { it.id })
      }

  @Test
  fun observeForExplorer_returnsTheReservationsTheUidJoined() =
      runBlocking<Unit> {
        val solo = repository.createReservation(reservation(), "u1").getOrThrow()
        val friends = repository.createReservation(reservation(), "u2").getOrThrow()
        party.join(friends, "u1").getOrThrow()
        repository.createReservation(reservation(), "u2").getOrThrow()

        assertEquals(
            listOf(solo, friends),
            repository.observeForExplorer("u1").first().map { it.id },
        )
      }

  @Test
  fun observeForExplorer_leavesOutAReservationTheUidWithdrewFrom() =
      runBlocking<Unit> {
        val id = repository.createReservation(reservation(), "u2").getOrThrow()
        party.join(id, "u1").getOrThrow()

        party.withdraw(id, "u1").getOrThrow()

        assertTrue(repository.observeForExplorer("u1").first().isEmpty())
        assertEquals(listOf(id), repository.observeForExplorer("u2").first().map { it.id })
      }

  @Test
  fun observeForExplorer_emitsAgainWhenTheUidJoinsAParty() =
      runBlocking<Unit> {
        val id = repository.createReservation(reservation(), "u2").getOrThrow()
        val subscribed = CompletableDeferred<Unit>()
        val emissions = async {
          repository.observeForExplorer("u1").onEach { subscribed.complete(Unit) }.take(2).toList()
        }
        withTimeout(TIMEOUT_MS) { subscribed.await() }

        party.join(id, "u1").getOrThrow()

        val ids = withTimeout(TIMEOUT_MS) { emissions.await() }.map { list -> list.map { it.id } }
        assertEquals(listOf(emptyList(), listOf(id)), ids)
      }

  @Test
  fun observeForVenue_emitsAgainWhenAReservationIsAdded() =
      runBlocking<Unit> {
        val emissions =
            async(start = CoroutineStart.UNDISPATCHED) {
              repository.observeForVenue("v1").take(2).toList()
            }

        repository.createReservation(reservation(), "u1").getOrThrow()

        val sizes = withTimeout(TIMEOUT_MS) { emissions.await() }.map { it.size }
        assertEquals(listOf(0, 1), sizes)
      }

  @Test
  fun updateStatus_allowedTransition_changesOnlyTheStatus() =
      runBlocking<Unit> {
        val id = repository.createReservation(reservation(), "u1").getOrThrow()
        val before = repository.observeForVenue("v1").first().single()

        repository.updateStatus(id, ReservationStatus.APPROVED).getOrThrow()

        val after = repository.observeForVenue("v1").first().single()
        assertEquals(before.copy(status = ReservationStatus.APPROVED), after)
      }

  @Test
  fun updateStatus_allowsEachStepOfALifecycle() =
      runBlocking<Unit> {
        val id = repository.createReservation(reservation(), "u1").getOrThrow()

        repository.updateStatus(id, ReservationStatus.APPROVED).getOrThrow()
        repository.updateStatus(id, ReservationStatus.CANCELLED).getOrThrow()

        assertEquals(
            ReservationStatus.CANCELLED,
            repository.observeForVenue("v1").first().single().status,
        )
      }

  @Test
  fun updateStatus_forbiddenTransition_failsAndKeepsTheStatus() =
      runBlocking<Unit> {
        val id = repository.createReservation(reservation(), "u1").getOrThrow()
        repository.updateStatus(id, ReservationStatus.REJECTED).getOrThrow()

        val result = repository.updateStatus(id, ReservationStatus.APPROVED)

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(
            ReservationStatus.REJECTED,
            repository.observeForVenue("v1").first().single().status,
        )
      }

  @Test
  fun updateStatus_unknownId_fails() =
      runBlocking<Unit> {
        val result = repository.updateStatus("missing", ReservationStatus.APPROVED)

        assertTrue(result.exceptionOrNull() is NoSuchElementException)
      }

  @Test
  fun updateStatus_whenFailureIsForced_returnsItAndChangesNothing() =
      runBlocking<Unit> {
        val id = repository.createReservation(reservation(), "u1").getOrThrow()
        val failure = IllegalStateException("offline")
        repository.forcedFailure = failure

        assertEquals(
            failure,
            repository.updateStatus(id, ReservationStatus.APPROVED).exceptionOrNull(),
        )
        assertEquals(
            ReservationStatus.PENDING,
            repository.observeForVenue("v1").first().single().status,
        )
      }

  /**
   * A repository, with parties of its own, that checks the size of a party on approval against a
   * quest of [PARTY_SIZE] explorers, and the id of a reservation of that quest with a party of
   * [joined] explorers.
   */
  private suspend fun sizedReservation(
      exactPartySize: Boolean,
      joined: Int,
  ): Pair<FakeReservationRepository, String> {
    val quest =
        testQuest(id = reservation().questId, minPartySize = PARTY_SIZE)
            .copy(exactPartySize = exactPartySize)
    val sizedParty = FakePartyRepository(now = { FIXED_NOW })
    val sized =
        FakeReservationRepository(
            now = { FIXED_NOW },
            party = sizedParty,
            quests = FakeQuestRepository(initialQuests = listOf(quest)),
        )
    val id = sized.createReservation(reservation(), "u0").getOrThrow()
    (1 until joined).forEach { sizedParty.join(id, "u$it").getOrThrow() }
    return sized to id
  }

  @Test
  fun updateStatus_approvingAPartyAboveAnAtLeastSize_succeeds() =
      runBlocking<Unit> {
        val (sized, id) = sizedReservation(exactPartySize = false, joined = PARTY_SIZE + 1)

        sized.updateStatus(id, ReservationStatus.APPROVED).getOrThrow()
      }

  @Test
  fun updateStatus_approvingAPartyOfAnExactSize_succeeds() =
      runBlocking<Unit> {
        val (sized, id) = sizedReservation(exactPartySize = true, joined = PARTY_SIZE)

        sized.updateStatus(id, ReservationStatus.APPROVED).getOrThrow()
      }

  @Test
  fun updateStatus_approvingAPartyBelowItsQuestsSize_failsAndKeepsTheStatus() =
      runBlocking<Unit> {
        val (sized, id) = sizedReservation(exactPartySize = false, joined = PARTY_SIZE - 1)

        val result = sized.updateStatus(id, ReservationStatus.APPROVED)

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(ReservationStatus.PENDING, sized.observeForVenue("v1").first().single().status)
      }

  @Test
  fun updateStatus_approvingAPartyAboveAnExactSize_fails() =
      runBlocking<Unit> {
        val (sized, id) = sizedReservation(exactPartySize = true, joined = PARTY_SIZE + 1)

        assertTrue(
            sized.updateStatus(id, ReservationStatus.APPROVED).exceptionOrNull()
                is IllegalStateException
        )
      }

  /** Only approving needs the size: the venue can reject or cancel a party of any size. */
  @Test
  fun updateStatus_rejectingOrCancellingAPartyBelowItsQuestsSize_succeeds() =
      runBlocking<Unit> {
        val (rejecting, rejected) = sizedReservation(exactPartySize = false, joined = 1)
        rejecting.updateStatus(rejected, ReservationStatus.REJECTED).getOrThrow()

        val (cancelling, cancelled) = sizedReservation(exactPartySize = false, joined = 1)
        cancelling.updateStatus(cancelled, ReservationStatus.CANCELLED).getOrThrow()
      }

  @Test
  fun updateStatus_approvingWithAMissingQuest_failsWithNoSuchElement() =
      runBlocking<Unit> {
        val sized =
            FakeReservationRepository(
                now = { FIXED_NOW },
                party = party,
                quests = FakeQuestRepository(),
            )
        val id = sized.createReservation(reservation(), "u1").getOrThrow()

        assertTrue(
            sized.updateStatus(id, ReservationStatus.APPROVED).exceptionOrNull()
                is NoSuchElementException
        )
      }

  /** Without a party given, the repository's own one fills up at the size of an exact quest. */
  @Test
  fun defaultParty_ofAnExactQuest_isFullAtItsSize() =
      runBlocking<Unit> {
        val quest =
            testQuest(id = reservation().questId, minPartySize = PARTY_SIZE)
                .copy(exactPartySize = true)
        val sized =
            FakeReservationRepository(
                now = { FIXED_NOW },
                quests = FakeQuestRepository(initialQuests = listOf(quest)),
            )
        val id = sized.createReservation(reservation(), "u0").getOrThrow()
        (1 until PARTY_SIZE).forEach { sized.party.join(id, "u$it").getOrThrow() }

        assertTrue(sized.party.join(id, "u$PARTY_SIZE").exceptionOrNull() is IllegalStateException)
      }

  /**
   * Without quests, the repository's own party holds [MAX_PARTY_SIZE], as for an at least quest.
   */
  @Test
  fun defaultParty_withoutQuests_isFullAtTheMaximum() =
      runBlocking<Unit> {
        val unsized = FakeReservationRepository(now = { FIXED_NOW })
        val id = unsized.createReservation(reservation(), "u0").getOrThrow()
        (1 until MAX_PARTY_SIZE).forEach { unsized.party.join(id, "u$it").getOrThrow() }

        assertTrue(
            unsized.party.join(id, "u$MAX_PARTY_SIZE").exceptionOrNull() is IllegalStateException
        )
      }

  private companion object {
    const val PARTY_SIZE = 3
    const val FIXED_NOW = 42_000L
    const val TIMEOUT_MS = 5_000L
  }
}
