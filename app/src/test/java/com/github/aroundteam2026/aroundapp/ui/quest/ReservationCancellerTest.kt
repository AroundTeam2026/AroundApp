// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest

import com.github.aroundteam2026.aroundapp.model.party.FakePartyRepository
import com.github.aroundteam2026.aroundapp.model.party.isJoined
import com.github.aroundteam2026.aroundapp.model.reservation.FakeProofUploadQueue
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ReservationCanceller]: cancelling or leaving a reservation, and its queued proof with it. */
class ReservationCancellerTest {
  /**
   * The creator's membership is written at `createdAt`, so the member joins a millisecond later.
   */
  private var time = START
  private val party = FakePartyRepository { time }
  private val reservations = FakeReservationRepository({ time }, party)
  private val uploads = FakeProofUploadQueue()
  private val canceller = ReservationCanceller(reservations, party, uploads)

  /** A reservation [CREATOR] made and [MEMBER] joined, with a proof queued; returns its id. */
  private suspend fun sharedReservationWithQueuedProof(): String {
    val id =
        reservations
            .createReservation(newPendingReservation("fox", CAFE, "Creator", START), CREATOR)
            .getOrThrow()
    time++
    party.join(id, MEMBER).getOrThrow()
    uploads.enqueue(id, CAFE, MEMBER, File("proof.jpg")).getOrThrow()
    return id
  }

  private suspend fun status(id: String): ReservationStatus =
      reservations.observeForExplorer(CREATOR).first().single { it.id == id }.status

  private suspend fun joined(id: String, uid: String): Boolean =
      party.observeMembers(id).first().single { it.uid == uid }.isJoined

  @Test
  fun theCreatorCancelsTheReservationAndItsQueuedProof() = runTest {
    val id = sharedReservationWithQueuedProof()

    assertTrue(canceller.cancel(id, CREATOR, leaving = false).isSuccess)

    assertEquals(ReservationStatus.CANCELLED, status(id))
    assertTrue(joined(id, MEMBER))
    assertEquals(listOf(id), uploads.cancelled)
  }

  @Test
  fun aMemberLeavesThePartyAndCancelsTheQueuedProof() = runTest {
    val id = sharedReservationWithQueuedProof()

    assertTrue(canceller.cancel(id, MEMBER, leaving = true).isSuccess)

    assertFalse(joined(id, MEMBER))
    assertEquals(ReservationStatus.PENDING, status(id))
    assertEquals(listOf(id), uploads.cancelled)
  }

  @Test
  fun aFailedCancelIsReturnedAndKeepsTheQueuedProof() = runTest {
    val id = sharedReservationWithQueuedProof()
    val failure = IOException("offline")
    reservations.forcedFailure = failure

    assertEquals(failure, canceller.cancel(id, CREATOR, leaving = false).exceptionOrNull())

    assertTrue(uploads.cancelled.isEmpty())
  }

  @Test
  fun aFailedLeaveIsReturnedAndKeepsTheQueuedProof() = runTest {
    val id = sharedReservationWithQueuedProof()
    val failure = IOException("offline")
    party.forcedFailure = failure

    assertEquals(failure, canceller.cancel(id, MEMBER, leaving = true).exceptionOrNull())

    assertTrue(uploads.cancelled.isEmpty())
  }

  // The reservation is gone either way; only a stale copy is left, which expires on its own
  @Test
  fun aQueuedProofThatCantBeCancelledStillCancelsTheReservation() = runTest {
    val id = sharedReservationWithQueuedProof()
    uploads.cancelFailure = IllegalStateException("WorkManager unavailable")

    assertTrue(canceller.cancel(id, CREATOR, leaving = false).isSuccess)

    assertEquals(ReservationStatus.CANCELLED, status(id))
  }

  private companion object {
    const val START = 1_000L
    const val CAFE = "cafe"
    const val CREATOR = "creator"
    const val MEMBER = "member"
  }
}
