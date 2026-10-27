// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository

/**
 * The real venue page of [venueId], on in-memory repositories, for navigation tests: the app's own
 * page would read Firestore.
 */
@Composable
fun VenuePageOnFakes(venueId: String, onBack: () -> Unit) {
  val viewModel =
      remember(venueId) {
        VenuePageViewModel(
            venueId,
            FakeVenueRepository(listOf(testVenue(id = venueId))),
            FakeQuestRepository(),
            FakeReservationRepository(),
            "explorer",
        )
      }
  VenuePageScreen(viewModel, onBack)
}
