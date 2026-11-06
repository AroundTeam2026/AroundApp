// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.aroundteam2026.aroundapp.model.TEST_EXPLORER_NAME
import com.github.aroundteam2026.aroundapp.model.party.FakePartyRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeCompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testUsers
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import kotlinx.coroutines.runBlocking

/** The explorer the venue pages on fakes are shown to. */
const val FAKE_EXPLORER_UID = "explorer"

/** When the approved reservation's slot starts, in epoch milliseconds; any time works. */
const val FAKE_SLOT_START = 1_000L

/** The id the fake repository gives the approved reservation, its first one. */
const val APPROVED_RESERVATION_ID = "reservation-1"

/**
 * The real venue page of [venueId], on in-memory repositories, for navigation tests: the app's own
 * page would read Firestore.
 *
 * @param approvedQuestId A quest the venue offers, whose reservation [APPROVED_RESERVATION_ID] by
 *   [FAKE_EXPLORER_UID] the venue approved, so its details show "Submit proof"; none if null.
 */
@Composable
fun VenuePageOnFakes(
    venueId: String,
    onBack: () -> Unit,
    onSubmitProof: (reservationId: String, venueName: String) -> Unit,
    approvedQuestId: String? = null,
) {
  val viewModel =
      remember(venueId) {
        val party = FakePartyRepository()
        val reservations = FakeReservationRepository(party = party)
        val quests = listOfNotNull(approvedQuestId?.let { testQuest(id = it, venueId = venueId) })
        approvedQuestId?.let { questId ->
          runBlocking {
            val id =
                reservations
                    .createReservation(
                        newPendingReservation(
                            questId,
                            venueId,
                            TEST_EXPLORER_NAME,
                            FAKE_SLOT_START,
                        ),
                        FAKE_EXPLORER_UID,
                    )
                    .getOrThrow()
            reservations.updateStatus(id, ReservationStatus.APPROVED).getOrThrow()
          }
        }
        VenuePageViewModel(
            venueId,
            FakeVenueRepository(listOf(testVenue(id = venueId))),
            FakeQuestRepository(initialQuests = quests),
            reservations,
            testUsers(FAKE_EXPLORER_UID),
            party,
            FakeCompletionRepository(),
            FAKE_EXPLORER_UID,
        )
      }
  VenuePageScreen(viewModel, onBack, onSubmitProof)
}
