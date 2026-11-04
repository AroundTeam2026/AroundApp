// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.overview

import com.github.aroundteam2026.aroundapp.model.TEST_EXPLORER_NAME
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.party.FakePartyRepository
import com.github.aroundteam2026.aroundapp.model.party.PartyMember
import com.github.aroundteam2026.aroundapp.model.party.PartyRepository
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.quest.ScriptedQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  /** The creation time the fake gives the next reservation; tests move it to order them. */
  private var now = START
  private val party = FakePartyRepository { now }
  private val reservations = FakeReservationRepository({ now }, party)
  private val listener = BreakableReservations(reservations)

  private val venues =
      FakeVenueRepository(
          listOf(
              testVenue(id = CAFE, name = "Café Lumen", address = "Rue du Pont 1"),
              testVenue(id = BAKERY, name = "Boulangerie Soleil"),
          )
      )

  private val quests =
      mutableMapOf(
          "fox" to testQuest(id = "fox", venueId = CAFE, title = "Find the hidden fox"),
          "bread" to testQuest(id = "bread", venueId = BAKERY, title = "Bake with us"),
          "cake" to testQuest(id = "cake", venueId = CAFE, title = "Taste the cake"),
      )
  private val questReads = mutableListOf<String>()
  private val questRepository = ScriptedQuestRepository(quest = ::readQuest)

  private suspend fun readQuest(id: String): Quest? {
    questReads += id
    return quests[id]
  }

  private val viewModel by lazy {
    OverviewViewModel(EXPLORER, listener, questRepository, venues, party)
  }

  private val state
    get() = viewModel.uiState.value

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  /**
   * Books [questId] at [venueId] as the explorer, with its slot at [slotStart], and has [friends]
   * join its party; returns its id.
   */
  private suspend fun book(
      questId: String,
      venueId: String = CAFE,
      slotStart: Long = START,
      friends: List<String> = emptyList(),
  ): String {
    val id =
        reservations
            .createReservation(
                newPendingReservation(questId, venueId, TEST_EXPLORER_NAME, slotStart),
                EXPLORER,
            )
            .getOrThrow()
    friends.forEach { party.join(id, it).getOrThrow() }
    return id
  }

  private suspend fun answer(id: String, status: ReservationStatus) {
    reservations.updateStatus(id, status).getOrThrow()
  }

  private fun titles() = state.rows.map { it.questTitle }

  @Test fun startsLoading() = test { assertEquals(OverviewUiState(), state) }

  @Test
  fun withNoReservationsShowsAnEmptyList() = test {
    viewModel
    runCurrent()

    assertEquals(OverviewUiState(status = OverviewStatus.LOADED), state)
  }

  @Test
  fun aPendingReservationShowsWithItsQuestAndVenue() = test {
    val id = book("fox", slotStart = START + HOUR)
    viewModel
    runCurrent()

    assertEquals(OverviewStatus.LOADED, state.status)
    assertEquals(
        listOf(
            OverviewRow(
                reservationId = id,
                questId = "fox",
                venueId = CAFE,
                questTitle = "Find the hidden fox",
                reward = Reward.FreeItem("Coffee", specifics = null),
                venueName = "Café Lumen",
                address = "Rue du Pont 1",
                avatar = VenueAvatar.Initials("CL"),
                slotStart = START + HOUR,
                partySize = 1,
                status = OverviewRowStatus.WAITING,
            )
        ),
        state.rows,
    )
  }

  @Test
  fun anApprovedReservationShowsApproved() = test {
    answer(book("fox"), ReservationStatus.APPROVED)
    viewModel
    runCurrent()

    assertEquals(OverviewRowStatus.APPROVED, state.rows.single().status)
  }

  @Test
  fun thePartySizeCountsEveryJoinedExplorer() = test {
    book("fox", friends = listOf("friend", "cousin"))
    viewModel
    runCurrent()

    assertEquals(3, state.rows.single().partySize)
  }

  @Test
  fun aFriendWhoWithdrawsLeavesThePartySize() = test {
    val id = book("fox", friends = listOf("friend", "cousin"))
    viewModel
    runCurrent()

    party.withdraw(id, "cousin").getOrThrow()
    runCurrent()

    assertEquals(2, state.rows.single().partySize)
  }

  @Test
  fun aReservationTheExplorerWithdrewFromLeavesTheList() = test {
    val id = book("fox", friends = listOf("friend"))
    book("cake")
    viewModel
    runCurrent()

    party.withdraw(id, EXPLORER).getOrThrow()
    runCurrent()

    assertEquals(listOf("Taste the cake"), titles())
  }

  /** The party sizes are always all known; if one were missing, the explorer still counts. */
  @Test
  fun aPartyWithNoKnownSizeCountsJustTheExplorer() {
    val reservation =
        newPendingReservation("fox", CAFE, TEST_EXPLORER_NAME, START).copy(id = "reservation-1")
    val quest = testQuest(id = "fox", venueId = CAFE)

    val rows = overviewRows(listOf(reservation to quest), listOf(testVenue(id = CAFE)), emptyMap())

    assertEquals(1, rows.single().partySize)
  }

  @Test
  fun rejectedAndCancelledReservationsAreLeftOut() = test {
    answer(book("fox"), ReservationStatus.REJECTED)
    answer(book("bread", venueId = BAKERY), ReservationStatus.CANCELLED)
    book("cake")
    viewModel
    runCurrent()

    assertEquals(listOf("Taste the cake"), titles())
  }

  @Test
  fun aReservationTheVenueRejectsLeavesTheList() = test {
    val id = book("fox")
    viewModel
    runCurrent()

    answer(id, ReservationStatus.REJECTED)
    runCurrent()

    assertEquals(OverviewUiState(status = OverviewStatus.LOADED), state)
  }

  @Test
  fun aReservationTheVenueApprovesTurnsApproved() = test {
    val id = book("fox")
    viewModel
    runCurrent()

    answer(id, ReservationStatus.APPROVED)
    runCurrent()

    assertEquals(OverviewRowStatus.APPROVED, state.rows.single().status)
  }

  @Test
  fun aNewReservationJoinsTheList() = test {
    book("fox")
    viewModel
    runCurrent()

    book("bread", venueId = BAKERY, slotStart = START + HOUR)
    runCurrent()

    assertEquals(listOf("Find the hidden fox", "Bake with us"), titles())
    assertEquals("Boulangerie Soleil", state.rows[1].venueName)
  }

  @Test
  fun ordersBySoonestSlotFirst() = test {
    book("fox", slotStart = START + 2 * HOUR)
    book("bread", venueId = BAKERY, slotStart = START)
    book("cake", slotStart = START + HOUR)
    viewModel
    runCurrent()

    assertEquals(listOf("Bake with us", "Taste the cake", "Find the hidden fox"), titles())
  }

  @Test
  fun aTieOnTheSlotGoesToTheNewestReservation() = test {
    book("fox")
    now = START + 1
    book("cake")
    viewModel
    runCurrent()

    assertEquals(listOf("Taste the cake", "Find the hidden fox"), titles())
  }

  @Test
  fun aTieOnTheSlotAndCreationGoesToTheSmallestId() {
    val tied = newPendingReservation("fox", CAFE, TEST_EXPLORER_NAME, START)
    val first = tied.copy(id = "reservation-1")
    val second = tied.copy(id = "reservation-2")

    // Given in reverse, so only the tie-break on the id puts them in order.
    assertEquals(listOf(first, second), activeReservations(listOf(second, first)))
  }

  @Test
  fun aReservationWhoseQuestIsGoneIsLeftOut() = test {
    quests.remove("fox")
    book("fox")
    book("cake")
    viewModel
    runCurrent()

    assertEquals(OverviewStatus.LOADED, state.status)
    assertEquals(listOf("Taste the cake"), titles())
  }

  @Test
  fun eachQuestIsReadOnce() = test {
    quests.remove("bread")
    val fox = book("fox")
    book("bread", venueId = BAKERY)
    viewModel
    runCurrent()

    answer(fox, ReservationStatus.APPROVED)
    book("cake")
    runCurrent()

    assertEquals(listOf("bread", "cake", "fox"), questReads.sorted())
  }

  @Test
  fun aChangeToAVenueUpdatesItsRows() = test {
    book("fox")
    book("cake", slotStart = START + HOUR)
    viewModel
    runCurrent()

    venues.setArea(CAFE, Location(46.52, 6.63), radiusMeters = 50, address = "Place de la Gare 3")
    runCurrent()

    assertEquals(listOf("Place de la Gare 3", "Place de la Gare 3"), state.rows.map { it.address })
  }

  @Test
  fun aReservationWhoseVenueIsGoneIsLeftOut() = test {
    quests["ghost"] = testQuest(id = "ghost", venueId = "closed", title = "Haunt the hall")
    book("ghost", venueId = "closed")
    book("fox", slotStart = START + HOUR)
    viewModel
    runCurrent()

    assertEquals(listOf("Find the hidden fox"), titles())
  }

  @Test
  fun aFailingReservationListenerShowsFailed() = test {
    book("fox")
    listener.broken = true
    viewModel
    runCurrent()

    assertEquals(OverviewUiState(status = OverviewStatus.FAILED), state)
  }

  @Test
  fun aFailingVenueListenerShowsFailed() = test {
    book("fox")
    val broken =
        object : VenueRepository by venues {
          override fun observeVenues(venueIds: Set<String>): Flow<List<Venue>> = flow {
            throw IOException("denied")
          }
        }
    val viewModel = OverviewViewModel(EXPLORER, listener, questRepository, broken, party)
    runCurrent()

    assertEquals(OverviewUiState(status = OverviewStatus.FAILED), viewModel.uiState.value)
  }

  @Test
  fun aFailingPartyListenerShowsFailed() = test {
    book("fox")
    val broken =
        object : PartyRepository by party {
          override fun observeMembers(reservationId: String): Flow<List<PartyMember>> = flow {
            throw IOException("denied")
          }
        }
    val viewModel = OverviewViewModel(EXPLORER, listener, questRepository, venues, broken)
    runCurrent()

    assertEquals(OverviewUiState(status = OverviewStatus.FAILED), viewModel.uiState.value)
  }

  @Test
  fun aFailingQuestReadShowsFailed() = test {
    questRepository.quest = { throw IOException("offline") }
    book("fox")
    viewModel
    runCurrent()

    assertEquals(OverviewUiState(status = OverviewStatus.FAILED), state)
  }

  @Test
  fun retryAfterAFailureLoadsAgain() = test {
    book("fox")
    listener.broken = true
    viewModel
    runCurrent()

    listener.broken = false
    viewModel.retry()
    assertEquals(OverviewUiState(), state)
    runCurrent()

    assertEquals(OverviewStatus.LOADED, state.status)
    assertEquals(listOf("Find the hidden fox"), titles())
  }

  @Test
  fun retryReadsTheQuestsAgain() = test {
    questRepository.quest = { throw IOException("offline") }
    book("fox")
    viewModel
    runCurrent()

    questRepository.quest = ::readQuest
    viewModel.retry()
    runCurrent()

    assertEquals(listOf("Find the hidden fox"), titles())
  }

  @Test
  fun retryDoesNothingUnlessFailed() = test {
    viewModel
    runCurrent()
    listener.broken = true

    viewModel.retry()
    runCurrent()

    assertEquals(OverviewStatus.LOADED, state.status)
    assertEquals(1, listener.collections)
  }

  private companion object {
    const val EXPLORER = "explorer"
    const val CAFE = "cafe"
    const val BAKERY = "bakery"
    const val START = 1_800_000_000_000L
    const val HOUR = 3_600_000L
  }
}

/** Delegates to [inner], but its explorer listener fails at once while [broken] is set. */
private class BreakableReservations(private val inner: ReservationRepository) :
    ReservationRepository by inner {
  var broken = false
  var collections = 0
    private set

  override fun observeForExplorer(uid: String): Flow<List<Reservation>> = flow {
    collections++
    if (broken) throw IOException("denied")
    emitAll(inner.observeForExplorer(uid))
  }
}
