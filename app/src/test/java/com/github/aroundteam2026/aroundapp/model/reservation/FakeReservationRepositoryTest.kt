// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
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
  private lateinit var repository: FakeReservationRepository

  /** Gives every test a fresh, empty repository whose clock always returns [FIXED_NOW]. */
  @Before
  fun setUp() {
    repository = FakeReservationRepository(now = { FIXED_NOW })
  }

  /**
   * Builds a reservation with a blank id by default and values that differ from what the repository
   * sets, so tests can tell the caller's values from the repository's.
   */
  private fun reservation(
      id: String = "",
      venueId: String = "v1",
      explorerUids: List<String> = listOf("u1"),
      status: ReservationStatus = ReservationStatus.PENDING,
      createdAt: Long = 1_000L,
  ) =
      Reservation(
          id = id,
          questId = "q1",
          venueId = venueId,
          explorerUids = explorerUids,
          slotStart = 5_000L,
          status = status,
          createdAt = createdAt,
      )

  @Test
  fun createReservation_assignsAnIdAndTheCreationTime() =
      runBlocking<Unit> {
        val id = repository.createReservation(reservation()).getOrThrow()

        val stored = repository.observeForVenue("v1").first().single()
        assertEquals(id, stored.id)
        assertEquals(FIXED_NOW, stored.createdAt)
      }

  @Test
  fun createReservation_ignoresTheCallersIdCreatedAtAndStatus() =
      runBlocking<Unit> {
        val id =
            repository
                .createReservation(
                    reservation(id = "caller", status = ReservationStatus.APPROVED, createdAt = 1L)
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
        val first = repository.createReservation(reservation()).getOrThrow()
        val second = repository.createReservation(reservation()).getOrThrow()

        assertNotEquals(first, second)
        assertEquals(2, repository.observeForVenue("v1").first().size)
      }

  @Test
  fun createReservation_whenFailureIsForced_returnsItAndStoresNothing() =
      runBlocking<Unit> {
        val failure = IllegalStateException("offline")
        repository.forcedFailure = failure

        assertEquals(failure, repository.createReservation(reservation()).exceptionOrNull())
        assertTrue(repository.observeForVenue("v1").first().isEmpty())
      }

  @Test
  fun observeForVenue_returnsOnlyThatVenuesReservations() =
      runBlocking<Unit> {
        val mine = repository.createReservation(reservation(venueId = "v1")).getOrThrow()
        repository.createReservation(reservation(venueId = "v2")).getOrThrow()

        assertEquals(listOf(mine), repository.observeForVenue("v1").first().map { it.id })
      }

  @Test
  fun observeForExplorer_returnsReservationsWhosePartyIncludesTheUid() =
      runBlocking<Unit> {
        val solo =
            repository.createReservation(reservation(explorerUids = listOf("u1"))).getOrThrow()
        val party =
            repository
                .createReservation(reservation(explorerUids = listOf("u2", "u1")))
                .getOrThrow()
        repository.createReservation(reservation(explorerUids = listOf("u2"))).getOrThrow()

        assertEquals(listOf(solo, party), repository.observeForExplorer("u1").first().map { it.id })
      }

  @Test
  fun observeForVenue_emitsAgainWhenAReservationIsAdded() =
      runBlocking<Unit> {
        val emissions =
            async(start = CoroutineStart.UNDISPATCHED) {
              repository.observeForVenue("v1").take(2).toList()
            }

        repository.createReservation(reservation()).getOrThrow()

        val sizes = withTimeout(TIMEOUT_MS) { emissions.await() }.map { it.size }
        assertEquals(listOf(0, 1), sizes)
      }

  @Test
  fun updateStatus_allowedTransition_changesOnlyTheStatus() =
      runBlocking<Unit> {
        val id =
            repository
                .createReservation(reservation(explorerUids = listOf("u1", "u2")))
                .getOrThrow()
        val before = repository.observeForVenue("v1").first().single()

        repository.updateStatus(id, ReservationStatus.APPROVED).getOrThrow()

        val after = repository.observeForVenue("v1").first().single()
        assertEquals(before.copy(status = ReservationStatus.APPROVED), after)
      }

  @Test
  fun updateStatus_allowsEachStepOfALifecycle() =
      runBlocking<Unit> {
        val id = repository.createReservation(reservation()).getOrThrow()

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
        val id = repository.createReservation(reservation()).getOrThrow()
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
        val id = repository.createReservation(reservation()).getOrThrow()
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

  private companion object {
    const val FIXED_NOW = 42_000L
    const val TIMEOUT_MS = 5_000L
  }
}
