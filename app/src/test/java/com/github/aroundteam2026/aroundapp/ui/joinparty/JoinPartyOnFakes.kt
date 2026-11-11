// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.joinparty

import com.github.aroundteam2026.aroundapp.model.connectivity.FakeConnectivityRepository
import com.github.aroundteam2026.aroundapp.model.party.FakePartyRepository
import com.github.aroundteam2026.aroundapp.model.party.PartyRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import kotlinx.coroutines.runBlocking

/**
 * A friend's party on in-memory repositories, for the tests that show the Join a party screen over
 * a real [JoinPartyViewModel]: the friend Ada has booked the quest `fox` of the venue `venue-1`
 * ("Café Lumen"), and its party code is [CODE].
 */
class JoinPartyOnFakes {
  val questRepository = FakeQuestRepository(initialQuests = listOf(testQuest("fox", "venue-1")))
  val reservations = FakeReservationRepository(quests = questRepository, newCode = { CODE })
  /** The fake the reservations were built with, to make a join fail. */
  val party = reservations.party as FakePartyRepository
  val venues = FakeVenueRepository(listOf(testVenue(id = "venue-1", name = "Café Lumen")))

  init {
    runBlocking {
      reservations.createReservation(newPendingReservation("fox", "venue-1", "Ada", 0L), "friend")
    }
  }

  fun viewModel(
      explorerUid: String = EXPLORER,
      partyRepository: PartyRepository = reservations.party,
  ) =
      JoinPartyViewModel(
          explorerUid,
          reservations,
          questRepository,
          venues,
          partyRepository,
          FakeConnectivityRepository(),
      )

  companion object {
    const val CODE = "7KQM42"
    const val EXPLORER = "explorer"
  }
}
