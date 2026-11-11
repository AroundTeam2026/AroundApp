// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.review

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.party.FakePartyRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.reservation.FakeCompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeProofPhotoRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Proof
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.user.FakeUserPictureRepository
import com.github.aroundteam2026.aroundapp.model.user.FakeUserRepository
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.ui.dashboard.VenueDashboardViewModel

/**
 * In-memory repositories of a signed-in venue with one quest, shared by its dashboard and the
 * Review proof screen, so a proof sent here shows on both.
 */
class ReviewProofOnFakes {
  val auth = FakeAuthRepository(VENUE_ID)
  val party = FakePartyRepository()
  val reservations = FakeReservationRepository(party = party)
  val completions = FakeCompletionRepository()
  val quests =
      FakeQuestRepository(
          initialQuests =
              listOf(
                  testQuest(
                      id = QUEST_ID,
                      venueId = VENUE_ID,
                      title = QUEST_TITLE,
                      reward = Reward.FreeItem("croissant", specifics = null),
                  )
              )
      )

  /** Sends [proof] for a new approved reservation of the quest; returns the completion's id. */
  suspend fun sendProof(proof: Proof = Proof.Text(ANSWER)): String {
    val reservation =
        newPendingReservation(
            QUEST_ID,
            VENUE_ID,
            EXPLORER_NAME,
            slotStart = System.currentTimeMillis(),
        )
    val reservationId = reservations.createReservation(reservation, EXPLORER_UID).getOrThrow()
    reservations.updateStatus(reservationId, ReservationStatus.APPROVED).getOrThrow()
    return completions.submit(reservationId, VENUE_ID, EXPLORER_UID, proof).getOrThrow()
  }

  /** The Review proof screen's ViewModel of the proof [completionId]. */
  fun reviewProof(completionId: String) =
      ReviewProofViewModel(
          completionId,
          auth,
          completions,
          reservations,
          party,
          quests,
          FakeProofPhotoRepository(),
      )

  /** Builds [reviewProof] for a completion's id, as navigation does. */
  fun reviewProofFactory(completionId: String): ViewModelProvider.Factory = viewModelFactory {
    initializer { reviewProof(completionId) }
  }

  /** Builds the venue's dashboard on the same repositories. */
  fun dashboardFactory(): ViewModelProvider.Factory = viewModelFactory {
    initializer {
      VenueDashboardViewModel(
          auth,
          reservations,
          quests,
          FakeVenueRepository(listOf(testVenue(id = VENUE_ID))),
          party,
          completions,
          FakeUserRepository(),
          FakeUserPictureRepository(),
      )
    }
  }

  companion object {
    const val VENUE_ID = "cafe"
    const val QUEST_ID = "fox"
    const val QUEST_TITLE = "Find the hidden fox"
    const val EXPLORER_UID = "yusuf"
    const val EXPLORER_NAME = "Yusuf S."
    const val ANSWER = "Mochi, the orange one"
  }
}
