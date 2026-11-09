// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.overview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.aroundteam2026.aroundapp.model.party.FakePartyRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeCompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository

/**
 * The real overview of an explorer with no accepted quest, on in-memory repositories, for tests
 * that show the whole app: the app's own overview would read Firestore.
 */
@Composable
fun OverviewOnFakes(onOpenQuest: (venueId: String, questId: String) -> Unit) {
  val viewModel = remember {
    val party = FakePartyRepository()
    OverviewViewModel(
        "explorer",
        FakeReservationRepository(party = party),
        FakeQuestRepository(),
        FakeVenueRepository(),
        party,
        FakeCompletionRepository(),
    )
  }
  OverviewScreen(viewModel, onOpenQuest)
}
