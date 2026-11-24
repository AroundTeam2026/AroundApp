// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest

import android.util.Log
import com.github.aroundteam2026.aroundapp.model.party.PartyRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ProofUploadQueue
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus

/**
 * Cancels a reservation for the explorer, as both the Quests tab and the venue's page do: its
 * creator cancels it, anyone else who joined leaves its party instead. Once that succeeds, the
 * reservation's proof waiting in [proofUploadQueue], if any, is cancelled too, since the rules
 * would refuse its upload (#625).
 */
class ReservationCanceller(
    private val reservationRepository: ReservationRepository,
    private val partyRepository: PartyRepository,
    private val proofUploadQueue: ProofUploadQueue,
) {
  /**
   * Cancels [reservationId], or withdraws [explorerUid] from its party if [leaving].
   *
   * @return success once the reservation is cancelled or left, even if its queued proof couldn't be
   *   cancelled, since only a stale copy is left then; or the failure that kept it.
   */
  suspend fun cancel(reservationId: String, explorerUid: String, leaving: Boolean): Result<Unit> {
    val result =
        if (leaving) partyRepository.withdraw(reservationId, explorerUid)
        else reservationRepository.updateStatus(reservationId, ReservationStatus.CANCELLED)
    result
        .onSuccess {
          proofUploadQueue.cancel(reservationId).onFailure {
            Log.w(TAG, "Cancelling the queued proof of $reservationId failed", it)
          }
        }
        .onFailure {
          val what = if (leaving) "Leaving the party of" else "Cancelling"
          Log.w(TAG, "$what reservation $reservationId failed", it)
        }
    return result
  }

  private companion object {
    const val TAG = "ReservationCanceller"
  }
}
