// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import com.github.aroundteam2026.aroundapp.model.TEST_EXPLORER_NAME
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testUsers
import com.github.aroundteam2026.aroundapp.model.user.ScriptedUserRepository
import com.github.aroundteam2026.aroundapp.ui.quest.TestAvatar
import com.github.aroundteam2026.aroundapp.ui.venuepage.AcceptStatus
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Accepting the map's nearby quests: [NearbyAcceptViewModel] and the state it drives. */
@OptIn(ExperimentalCoroutinesApi::class)
class NearbyAcceptViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val fake = FakeReservationRepository({ NOW })
  private val gated = GatedReservations(fake)
  private val users = ScriptedUserRepository(testUsers(EXPLORER))
  private var reservations: ReservationRepository = gated

  private val viewModel by lazy {
    NearbyAcceptViewModel(reservations, users, EXPLORER, clock = { NOW })
  }

  private val state
    get() = viewModel.uiState.value

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  /** Starts listening to the explorer's reservations and lets the first list arrive. */
  private fun TestScope.open() {
    viewModel
    runCurrent()
  }

  private suspend fun stored(): List<Reservation> = fake.observeForExplorer(EXPLORER).first()

  @Test
  fun nothingCanBeAcceptedUntilTheReservationsAreKnown() = test {
    viewModel

    assertFalse(state.available)
    viewModel.accept(quest("a"))
    runCurrent()

    // The first list arrived meanwhile, but the tap before it booked nothing
    assertTrue(state.available)
    assertEquals(emptyList<Reservation>(), stored())
  }

  @Test
  fun acceptingBooksAPendingReservationUnderTheProfilesName() = test {
    open()

    viewModel.accept(quest("a"))
    runCurrent()

    val reservation = stored().single()
    assertEquals("a", reservation.questId)
    assertEquals(VENUE_ID, reservation.venueId)
    assertEquals(ReservationStatus.PENDING, reservation.status)
    assertEquals(TEST_EXPLORER_NAME, reservation.explorerName)
    assertEquals(NOW, reservation.slotStart)
    assertEquals(AcceptStatus.ACCEPTED, state.statusOf("a"))
  }

  @Test
  fun aQuestIsAcceptingUntilItsReservationShowsUp() = test {
    open()
    gated.gate = CompletableDeferred()

    viewModel.accept(quest("a"))
    runCurrent()

    assertEquals(AcceptStatus.ACCEPTING, state.statusOf("a"))
    assertEquals(AcceptStatus.NONE, state.statusOf("b"))
    gated.gate!!.complete(Unit)
    runCurrent()
    assertEquals(AcceptStatus.ACCEPTED, state.statusOf("a"))
  }

  @Test
  fun aDoubleTapBooksOnce() = test {
    open()
    gated.gate = CompletableDeferred()

    viewModel.accept(quest("a"))
    viewModel.accept(quest("a"))
    gated.gate!!.complete(Unit)
    runCurrent()

    assertEquals(1, gated.calls)
    assertEquals(1, stored().size)
  }

  @Test
  fun aQuestAlreadyAcceptedIsAcceptedAndNotBookedAgain() = test {
    fake.createReservation(newPendingReservation("a", VENUE_ID, "Ada", NOW), EXPLORER)
    open()

    assertEquals(AcceptStatus.ACCEPTED, state.statusOf("a"))
    viewModel.accept(quest("a"))
    runCurrent()

    assertEquals(0, gated.calls)
  }

  @Test
  fun aCancelledReservationLeavesItsQuestToAcceptAgain() = test {
    val id =
        fake
            .createReservation(newPendingReservation("a", VENUE_ID, "Ada", NOW), EXPLORER)
            .getOrThrow()
    open()

    fake.updateStatus(id, ReservationStatus.CANCELLED)
    runCurrent()

    assertEquals(AcceptStatus.NONE, state.statusOf("a"))
  }

  @Test
  fun aFailedBookingShowsUntilAcceptingAgainSucceeds() = test {
    open()
    fake.forcedFailure = IOException("offline")

    viewModel.accept(quest("a"))
    runCurrent()

    assertEquals(AcceptStatus.FAILED, state.statusOf("a"))
    assertEquals(emptyList<Reservation>(), stored())

    fake.forcedFailure = null
    viewModel.accept(quest("a"))
    runCurrent()
    assertEquals(AcceptStatus.ACCEPTED, state.statusOf("a"))
  }

  @Test
  fun anUnreadableProfileFailsWithoutBooking() = test {
    open()
    users.nextGetFailure = IOException("offline")

    viewModel.accept(quest("a"))
    runCurrent()

    assertEquals(AcceptStatus.FAILED, state.statusOf("a"))
    assertEquals(0, gated.calls)
  }

  @Test
  fun withoutAProfileAcceptingFailsWithoutBooking() = test {
    val noProfile = NearbyAcceptViewModel(gated, ScriptedUserRepository(), EXPLORER) { NOW }
    runCurrent()

    noProfile.accept(quest("a"))
    runCurrent()

    assertEquals(AcceptStatus.FAILED, noProfile.uiState.value.statusOf("a"))
    assertEquals(0, gated.calls)
  }

  @Test
  fun whileTheReservationsCantBeReadNothingCanBeAccepted() = test {
    reservations =
        object : ReservationRepository by fake {
          override fun observeForExplorer(uid: String): Flow<List<Reservation>> = flow {
            throw IOException("denied")
          }
        }
    open()

    viewModel.accept(quest("a"))
    runCurrent()

    assertFalse(state.available)
    assertEquals(AcceptStatus.NONE, state.statusOf("a"))
    assertEquals(emptyList<Reservation>(), stored())
  }

  private fun quest(id: String) =
      NearbyQuest(
          venueId = VENUE_ID,
          venueName = "Café Lumen",
          avatar = TestAvatar(id),
          questId = id,
          title = "Quest $id",
          reward = null,
          minPartySize = 1,
          exactPartySize = false,
          distanceMeters = null,
      )

  private companion object {
    const val EXPLORER = "explorer"
    const val VENUE_ID = "cafe"
    const val NOW = 1_000_000L
  }
}

/** Delegates to [inner], counting the bookings as [calls] and holding each until [gate] opens. */
private class GatedReservations(private val inner: ReservationRepository) :
    ReservationRepository by inner {
  var gate: CompletableDeferred<Unit>? = null
  var calls = 0
    private set

  override suspend fun createReservation(
      reservation: Reservation,
      creatorUid: String,
  ): Result<String> {
    calls++
    gate?.await()
    return inner.createReservation(reservation, creatorUid)
  }
}
