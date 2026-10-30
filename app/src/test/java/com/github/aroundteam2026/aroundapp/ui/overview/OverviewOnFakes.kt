// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.overview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository

/**
 * The real overview of an explorer with no accepted quest, on in-memory repositories, for tests
 * that show the whole app: the app's own overview would read Firestore.
 */
@Composable
fun OverviewOnFakes(onOpenVenue: (venueId: String) -> Unit) {
  val viewModel = remember {
    OverviewViewModel(
        "explorer",
        FakeReservationRepository(),
        FakeQuestRepository(),
        FakeVenueRepository(),
    )
  }
  OverviewScreen(viewModel, onOpenVenue)
}
