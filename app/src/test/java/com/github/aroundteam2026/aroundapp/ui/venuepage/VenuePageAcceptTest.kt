// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import com.github.aroundteam2026.aroundapp.model.quest.ScriptedQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Accepting a quest on the venue page: [VenuePageViewModel.accept] and the state it drives. */
@OptIn(ExperimentalCoroutinesApi::class)
class VenuePageAcceptTest {
  private val dispatcher = StandardTestDispatcher()
  private val clock = { START + dispatcher.scheduler.currentTime }

  private val fake = FakeReservationRepository { START }
  private val reservations = GatedReservationRepository(fake)
  private val quests =
      listOf(
          testQuest(id = "a", venueId = VENUE_ID, minPartySize = 1),
          testQuest(id = "b", venueId = VENUE_ID, minPartySize = 3),
      )

  private var reservationRepository: ReservationRepository = reservations

  private val viewModel by lazy {
    VenuePageViewModel(
        VENUE_ID,
        FakeVenueRepository(listOf(testVenue(id = VENUE_ID))),
        ScriptedQuestRepository(activeQuestsByVenue = { flowOf(quests) }),
        reservationRepository,
        EXPLORER,
        clock,
    )
  }

  private val state
    get() = viewModel.uiState.value

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  /** Opens the page and picks the quest [questId]. */
  private fun TestScope.openAndPick(questId: String) {
    viewModel
    runCurrent()
    viewModel.onQuestSelected(questId)
  }

  private suspend fun stored(): List<Reservation> = fake.observeForExplorer(EXPLORER).first()

  private suspend fun storeFor(questId: String, party: List<String> = listOf(EXPLORER)) =
      fake
          .createReservation(
              newPendingReservation(questId, VENUE_ID, EXPLORER, START).copy(explorerUids = party)
          )
          .getOrThrow()

  @Test
  fun acceptCreatesOnePendingReservationForThePickedQuest() = test {
    openAndPick("a")
    advanceTimeBy(5_000)

    viewModel.accept()
    runCurrent()

    val reservation = stored().single()
    assertEquals("a", reservation.questId)
    assertEquals(VENUE_ID, reservation.venueId)
    assertEquals(listOf(EXPLORER), reservation.explorerUids)
    assertEquals(START + 5_000, reservation.slotStart)
    assertEquals(ReservationStatus.PENDING, reservation.status)
  }

  @Test
  fun acceptingShowsWhileTheWriteIsPendingThenAccepted() = test {
    openAndPick("a")
    val gate = CompletableDeferred<Unit>().also { reservations.gate = it }

    viewModel.accept()
    runCurrent()
    assertEquals(AcceptStatus.ACCEPTING, state.acceptStatus)

    gate.complete(Unit)
    runCurrent()
    assertEquals(AcceptStatus.ACCEPTED, state.acceptStatus)
    assertEquals("a", state.selectedQuestId)
  }

  @Test
  fun acceptWithNothingPickedCreatesNothing() = test {
    viewModel
    runCurrent()

    viewModel.accept()
    runCurrent()

    assertEquals(0, reservations.calls)
    assertEquals(AcceptStatus.NONE, state.acceptStatus)
  }

  @Test
  fun aFailureKeepsThePickedQuestAndShowsFailed() = test {
    openAndPick("a")
    fake.forcedFailure = IOException("offline")

    viewModel.accept()
    runCurrent()

    assertEquals(AcceptStatus.FAILED, state.acceptStatus)
    assertEquals("a", state.selectedQuestId)
    assertTrue(stored().isEmpty())
  }

  @Test
  fun aFailedAcceptCanBeRetried() = test {
    openAndPick("a")
    fake.forcedFailure = IOException("offline")
    viewModel.accept()
    runCurrent()

    fake.forcedFailure = null
    viewModel.accept()
    runCurrent()

    assertEquals(AcceptStatus.ACCEPTED, state.acceptStatus)
    assertEquals(1, stored().size)
  }

  @Test
  fun retryingShowsAcceptingAgainInsteadOfTheOldFailure() = test {
    openAndPick("a")
    fake.forcedFailure = IOException("offline")
    viewModel.accept()
    runCurrent()
    reservations.gate = CompletableDeferred()

    viewModel.accept()
    runCurrent()

    assertEquals(AcceptStatus.ACCEPTING, state.acceptStatus)
  }

  @Test
  fun aDoubleTapCreatesOneReservation() = test {
    openAndPick("a")
    val gate = CompletableDeferred<Unit>().also { reservations.gate = it }

    viewModel.accept()
    viewModel.accept()
    runCurrent()
    gate.complete(Unit)
    runCurrent()

    assertEquals(1, reservations.calls)
    assertEquals(1, stored().size)
  }

  @Test
  fun aSecondTapOnceAcceptedCreatesNothingMore() = test {
    openAndPick("a")
    viewModel.accept()
    runCurrent()

    viewModel.accept()
    runCurrent()

    assertEquals(1, reservations.calls)
  }

  @Test
  fun aQuestAcceptedBeforeShowsAcceptedWhenThePageOpens() = test {
    storeFor("a")

    openAndPick("a")
    viewModel.accept()
    runCurrent()

    assertEquals(AcceptStatus.ACCEPTED, state.acceptStatus)
    assertEquals(0, reservations.calls)
  }

  @Test
  fun anApprovedReservationStillCountsAsAccepted() = test {
    val id = storeFor("a")
    fake.updateStatus(id, ReservationStatus.APPROVED)

    openAndPick("a")

    assertEquals(AcceptStatus.ACCEPTED, state.acceptStatus)
  }

  @Test
  fun aRejectedReservationLeavesTheQuestOpenToAcceptAgain() = test {
    fake.updateStatus(storeFor("a"), ReservationStatus.REJECTED)
    openAndPick("a")
    assertEquals(AcceptStatus.NONE, state.acceptStatus)

    viewModel.accept()
    runCurrent()

    assertEquals(AcceptStatus.ACCEPTED, state.acceptStatus)
    assertEquals(1, reservations.calls)
  }

  @Test
  fun aCancelledReservationLeavesTheQuestOpenToAcceptAgain() = test {
    fake.updateStatus(storeFor("a"), ReservationStatus.CANCELLED)
    openAndPick("a")

    assertEquals(AcceptStatus.NONE, state.acceptStatus)
  }

  @Test
  fun acceptingOneQuestDoesNotMarkTheOthersAccepted() = test {
    openAndPick("a")
    viewModel.accept()
    runCurrent()

    viewModel.onQuestSelected("b")

    assertEquals(AcceptStatus.NONE, state.acceptStatus)
  }

  @Test
  fun aReservationAtAnotherVenueDoesNotAcceptThisVenuesQuest() = test {
    fake.createReservation(newPendingReservation("a", "other-venue", EXPLORER, START))

    openAndPick("a")

    assertEquals(AcceptStatus.NONE, state.acceptStatus)
  }

  @Test
  fun anotherQuestCanBeAcceptedOnceTheFirstIsDone() = test {
    openAndPick("a")
    viewModel.accept()
    runCurrent()

    viewModel.onQuestSelected("b")
    viewModel.accept()
    runCurrent()

    assertEquals(setOf("a", "b"), stored().map { it.questId }.toSet())
  }

  @Test
  fun givesHowManyMorePeopleThePickedQuestNeeds() = test {
    openAndPick("b")
    assertEquals(2, state.morePeopleNeeded) // a party of one against a minimum of three

    viewModel.onQuestSelected("a")
    assertEquals(0, state.morePeopleNeeded)
  }

  @Test
  fun aQuestThatNeedsMorePeopleCanStillBeAccepted() = test {
    openAndPick("b")

    viewModel.accept()
    runCurrent()

    assertEquals(AcceptStatus.ACCEPTED, state.acceptStatus)
    assertEquals(2, state.morePeopleNeeded)
  }

  @Test
  fun theBiggerPartyOfAnAcceptedQuestLowersWhatItNeeds() = test {
    storeFor("b", party = listOf(EXPLORER, "friend"))
    openAndPick("b")
    assertEquals(1, state.morePeopleNeeded)

    storeFor("a", party = listOf(EXPLORER, "friend", "other"))
    viewModel.onQuestSelected("a")
    assertEquals(0, state.morePeopleNeeded) // never below zero
  }

  @Test
  fun aFailingReservationListenerFailsThePage() = test {
    reservationRepository =
        object : ReservationRepository by fake {
          override fun observeForExplorer(uid: String): Flow<List<Reservation>> = flow {
            throw IOException("denied")
          }
        }

    viewModel
    runCurrent()

    assertEquals(VenuePageStatus.FAILED, state.status)
  }

  private companion object {
    const val VENUE_ID = "cafe"
    const val EXPLORER = "explorer"
    const val START = 1_800_000_000_000L
  }
}

/** Delegates to [inner], counting [createReservation] calls and holding them until [gate] ends. */
private class GatedReservationRepository(private val inner: ReservationRepository) :
    ReservationRepository by inner {
  var gate: CompletableDeferred<Unit>? = null
  var calls = 0
    private set

  override suspend fun createReservation(reservation: Reservation): Result<String> {
    calls++
    gate?.await()
    return inner.createReservation(reservation)
  }
}
