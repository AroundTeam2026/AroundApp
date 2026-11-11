// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.model.reservation

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

/**
 * Tests [FakeCompletionRepository] against the [CompletionRepository] contract, one behaviour per
 * test. [canTransition] itself is tested in [CompletionStatusTest].
 */
class FakeCompletionRepositoryTest {
  private lateinit var repository: FakeCompletionRepository

  /** Gives every test a fresh, empty repository whose clock always returns [FIXED_NOW]. */
  @Before
  fun setUp() {
    repository = FakeCompletionRepository(now = { FIXED_NOW })
  }

  /** Submits a completion with [PROOF] and returns its id. */
  private suspend fun submit(
      reservationId: String = RESERVATION_ID,
      venueId: String = VENUE_ID,
      explorerUid: String = EXPLORER_UID,
  ) = repository.submit(reservationId, venueId, explorerUid, PROOF).getOrThrow()

  /** The only completion stored for [VENUE_ID]. */
  private suspend fun stored() = repository.observeForVenue(VENUE_ID).first().single()

  @Test
  fun submit_storesAPendingCompletionSubmittedNowUnderItsId() =
      runBlocking<Unit> {
        assertEquals(COMPLETION_ID, submit())

        val expected =
            Completion(
                id = COMPLETION_ID,
                reservationId = RESERVATION_ID,
                venueId = VENUE_ID,
                explorerUid = EXPLORER_UID,
                proof = PROOF,
                status = CompletionStatus.PENDING,
                rejectReason = null,
                submittedAt = FIXED_NOW,
            )
        assertEquals(expected, stored())
      }

  @Test
  fun submit_twiceForTheSameReservationAndExplorer_failsAndKeepsTheFirst() =
      runBlocking<Unit> {
        submit()
        val first = stored()

        val result = repository.submit(RESERVATION_ID, VENUE_ID, EXPLORER_UID, OTHER_PROOF)

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(first, stored())
      }

  @Test
  fun submit_byAnotherMemberOfTheParty_failsAndKeepsTheFirst() =
      runBlocking<Unit> {
        submit()
        val first = stored()

        val result = repository.submit(RESERVATION_ID, VENUE_ID, OTHER_ID, OTHER_PROOF)

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(first, stored())
      }

  @Test
  fun submit_whenFailureIsForced_returnsItAndStoresNothing() =
      runBlocking<Unit> {
        val failure = IllegalStateException(OFFLINE)
        repository.forcedFailure = failure

        val result = repository.submit(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PROOF)

        assertEquals(failure, result.exceptionOrNull())
        assertTrue(repository.observeForVenue(VENUE_ID).first().isEmpty())
      }

  @Test
  fun observeForVenue_returnsOnlyThatVenuesCompletions() =
      runBlocking<Unit> {
        val mine = submit()
        submit(reservationId = OTHER_ID, venueId = OTHER_ID)

        assertEquals(listOf(mine), repository.observeForVenue(VENUE_ID).first().map { it.id })
      }

  @Test
  fun observeForReservation_returnsOnlyThatReservationsCompletion() =
      runBlocking<Unit> {
        val partyProof = submit()
        submit(reservationId = OTHER_ID)

        assertEquals(
            listOf(partyProof),
            repository.observeForReservation(RESERVATION_ID).first().map { it.id },
        )
      }

  @Test
  fun observe_emitsAgainOnlyWhenItsOwnListChanges() =
      runBlocking<Unit> {
        val emissions =
            async(start = CoroutineStart.UNDISPATCHED) {
              repository.observeForVenue(VENUE_ID).take(2).toList()
            }

        submit(reservationId = OTHER_ID, venueId = OTHER_ID)
        // Lets the collector see the write above before the next one replaces it
        yield()
        val mine = submit()

        val ids = withTimeout(TIMEOUT_MS) { emissions.await() }.map { list -> list.map { it.id } }
        assertEquals(listOf(emptyList(), listOf(mine)), ids)
      }

  @Test
  fun approve_pendingCompletion_changesOnlyTheStatus() =
      runBlocking<Unit> {
        val id = submit()
        val before = stored()

        repository.approve(id).getOrThrow()

        assertEquals(before.copy(status = CompletionStatus.APPROVED), stored())
      }

  @Test
  fun reject_pendingCompletion_changesOnlyTheStatusAndReason() =
      runBlocking<Unit> {
        val id = submit()
        val before = stored()

        repository.reject(id, REASON).getOrThrow()

        assertEquals(
            before.copy(status = CompletionStatus.REJECTED, rejectReason = REASON),
            stored(),
        )
      }

  @Test
  fun approve_rejectedCompletion_removesTheReason() =
      runBlocking<Unit> {
        val id = submit()
        val before = stored()
        repository.reject(id, REASON).getOrThrow()

        repository.approve(id).getOrThrow()

        assertEquals(before.copy(status = CompletionStatus.APPROVED), stored())
      }

  @Test
  fun reject_blankReason_failsBeforeTheBackendAndTheLookup() =
      runBlocking<Unit> {
        repository.forcedFailure = IllegalStateException(OFFLINE)

        val result = repository.reject(OTHER_ID, BLANK)

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
      }

  @Test
  fun answer_alreadyAnsweredCompletion_failsAndKeepsTheAnswer() =
      runBlocking<Unit> {
        val id = submit()
        repository.approve(id).getOrThrow()
        val approved = stored()

        val result = repository.reject(id, REASON)

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(approved, stored())
      }

  @Test
  fun reject_reasonAtAndOverTheLimit_refusesOnlyTheLongOneLeavingTheProof() = runBlocking {
    val id = submit()
    val before = stored()
    assertTrue(
        repository.reject(id, "x".repeat(MAX_REJECT_REASON_LENGTH + 1)).exceptionOrNull()
            is IllegalArgumentException
    )
    assertEquals(before, stored())

    val reason = "x".repeat(MAX_REJECT_REASON_LENGTH)
    repository.reject(id, reason).getOrThrow()
    assertEquals(reason, stored().rejectReason)
  }

  @Test
  fun reject_oversizedReason_failsBeforeTheBackendAndTheLookup() = runBlocking {
    repository.forcedFailure = IllegalStateException(OFFLINE)
    assertTrue(
        repository.reject(OTHER_ID, "x".repeat(MAX_REJECT_REASON_LENGTH + 1)).exceptionOrNull()
            is IllegalArgumentException
    )
  }

  @Test
  fun answer_unknownId_fails() =
      runBlocking<Unit> {
        assertTrue(repository.approve(OTHER_ID).exceptionOrNull() is NoSuchElementException)
      }

  @Test
  fun answer_whenFailureIsForced_returnsItAndChangesNothing() =
      runBlocking<Unit> {
        val id = submit()
        val failure = IllegalStateException(OFFLINE)
        repository.forcedFailure = failure

        assertEquals(failure, repository.approve(id).exceptionOrNull())
        assertEquals(CompletionStatus.PENDING, stored().status)
      }

  private companion object {
    const val FIXED_NOW = 42_000L
    const val TIMEOUT_MS = 5_000L
    const val RESERVATION_ID = "r1"
    const val VENUE_ID = "v1"
    const val EXPLORER_UID = "u1"
    const val OTHER_ID = "other"
    const val COMPLETION_ID = RESERVATION_ID
    const val REASON = "Blurry photo"
    const val BLANK = " "
    const val OFFLINE = "offline"
    val PROOF = Proof.Text("The answer")
    val OTHER_PROOF = Proof.Text("Another answer")
  }
}
