// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import com.github.aroundteam2026.aroundapp.model.TEST_EXPLORER_NAME
import com.github.aroundteam2026.aroundapp.model.party.FakePartyRepository
import com.github.aroundteam2026.aroundapp.model.party.PartyMember
import com.github.aroundteam2026.aroundapp.model.party.PartyMemberStatus
import com.github.aroundteam2026.aroundapp.model.quest.ScriptedQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeCompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testUsers
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** The party code on the venue page: who [VenuePageUiState.partyCode] shows it to, and when. */
@OptIn(ExperimentalCoroutinesApi::class)
class VenuePagePartyCodeTest {
  private val dispatcher = StandardTestDispatcher()

  /** The fakes' clock, which a test moves on so that a join comes after the reservation. */
  private var now = START
  private val party = FakePartyRepository { now }
  private val reservations = FakeReservationRepository({ now }, party, newCode = { CODE })
  private val quests =
      listOf(
          testQuest(id = "a", venueId = VENUE_ID, minPartySize = 2),
          testQuest(id = "pair", venueId = VENUE_ID, minPartySize = 2).copy(exactPartySize = true),
      )

  private val venue = MutableStateFlow<Venue?>(testVenue(id = VENUE_ID))

  private val viewModel by lazy {
    VenuePageViewModel(
        VENUE_ID,
        // The fake, but with a venue the test can take away
        object : VenueRepository by FakeVenueRepository(listOf(testVenue(id = VENUE_ID))) {
          override fun observeVenue(venueId: String): Flow<Venue?> = venue
        },
        ScriptedQuestRepository(activeQuestsByVenue = { flowOf(quests) }),
        reservations,
        testUsers(EXPLORER),
        party,
        FakeCompletionRepository { START },
        EXPLORER,
        { START + dispatcher.scheduler.currentTime },
    )
  }

  private val state
    get() = viewModel.uiState.value

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  /**
   * Stores a reservation of [questId] made by [creator], which [friends] join a moment later;
   * returns its id.
   */
  private suspend fun storeFor(
      questId: String,
      creator: String = EXPLORER,
      friends: List<String> = emptyList(),
  ): String {
    val id =
        reservations
            .createReservation(
                newPendingReservation(questId, VENUE_ID, TEST_EXPLORER_NAME, now),
                creator,
            )
            .getOrThrow()
    now += 1
    friends.forEach { party.join(id, it).getOrThrow() }
    return id
  }

  /** Opens the page on the details of the quest [questId]. */
  private fun TestScope.openDetails(questId: String) {
    viewModel
    runCurrent()
    viewModel.onQuestDetailsOpened(questId)
  }

  @Test
  fun theCreatorOfAPendingReservationIsShownItsCode() = test {
    storeFor("a")

    openDetails("a")

    assertEquals(CODE, state.partyCode)
  }

  // Their membership joined after the reservation was made, so they aren't its creator
  @Test
  fun aFriendWhoJoinedIsNotShownTheCode() = test {
    storeFor("a", creator = "friend", friends = listOf(EXPLORER))

    openDetails("a")

    assertEquals(2, state.acceptedParties["a"])
    assertNull(state.partyCode)
  }

  // Friends can only join a pending reservation, so the code is of no use once it's approved
  @Test
  fun anApprovedReservationHidesTheCode() = test {
    val id = storeFor("a")
    openDetails("a")
    assertEquals(CODE, state.partyCode)

    reservations.updateStatus(id, ReservationStatus.APPROVED).getOrThrow()
    runCurrent()

    assertNull(state.partyCode)
  }

  // The quest takes exactly 2, so the friend who makes 2 is the last who can join
  @Test
  fun aFullPartyHidesTheCode() = test {
    val id = storeFor("pair")
    openDetails("pair")
    assertEquals(CODE, state.partyCode)

    party.join(id, "friend").getOrThrow()
    runCurrent()

    assertNull(state.partyCode)
  }

  // A venue that's gone takes its page, codes included, with it
  @Test
  fun aVenueThatDisappearsTakesTheCodesWithIt() = test {
    storeFor("a")
    openDetails("a")
    assertEquals(CODE, state.partyCode)

    venue.value = null
    runCurrent()

    assertEquals(VenuePageUiState(status = VenuePageStatus.NOT_FOUND), state)
  }

  @Test
  fun partyCodesOf_keepsOnlyThePendingReservationsTheExplorerCreatedWithACode() {
    fun reservation(id: String, status: ReservationStatus = ReservationStatus.PENDING) =
        Reservation(id, "a", VENUE_ID, START, status, createdAt = START, partyCode = "C$id")
    fun member(id: String, uid: String = EXPLORER, joinedAt: Long = START) =
        PartyMember(id, uid, PartyMemberStatus.JOINED, joinedAt, joinedAt)

    val codes =
        partyCodesOf(
            listOf(
                reservation("mine"),
                reservation("joined"),
                reservation("approved", ReservationStatus.APPROVED),
                reservation("old").copy(partyCode = null),
                reservation("elsewhere").copy(venueId = "other"),
                reservation("unread"),
            ),
            mapOf(
                "mine" to listOf(member("mine")),
                "joined" to
                    listOf(member("joined", "friend"), member("joined", joinedAt = START + 1)),
                "approved" to listOf(member("approved")),
                "old" to listOf(member("old")),
                "elsewhere" to listOf(member("elsewhere")),
            ),
            EXPLORER,
            VENUE_ID,
        )

    assertEquals(mapOf("mine" to "Cmine"), codes)
  }

  private companion object {
    const val VENUE_ID = "cafe"
    const val EXPLORER = "explorer"
    const val CODE = "7KQM42"
    const val START = 1_800_000_000_000L
  }
}
