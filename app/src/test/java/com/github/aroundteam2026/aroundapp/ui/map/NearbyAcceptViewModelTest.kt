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
import kotlinx.coroutines.flow.MutableStateFlow
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
  private val local = LocalWrites(fake)

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

  @Test
  fun aBookingTheServerRefusesAfterTheAppWroteItShowsTryAgain() = test {
    reservations = local
    open()
    viewModel.accept(quest("a"))
    runCurrent()
    assertEquals(AcceptStatus.ACCEPTED, state.statusOf("a"))

    // The server refuses the write: Firestore rolls it back, so it vanishes from the list
    local.refuse("local-1")
    runCurrent()

    assertEquals(AcceptStatus.FAILED, state.statusOf("a"))
    viewModel.accept(quest("a"))
    runCurrent()
    assertEquals(AcceptStatus.ACCEPTED, state.statusOf("a"))
  }

  @Test
  fun aReservationCancelledElsewhereFreesTheQuestWithoutTryAgain() = test {
    reservations = local
    open()
    viewModel.accept(quest("a"))
    runCurrent()

    // Cancelled from the Quests tab: it stays listed, with its new status
    local.cancel("local-1")
    runCurrent()

    assertEquals(AcceptStatus.NONE, state.statusOf("a"))
    assertEquals(emptySet<String>(), state.failedQuestIds)
  }

  @Test
  fun aWriteListedBeforeTheBookingReturnsStaysAccepting() = test {
    reservations = local
    local.gate = CompletableDeferred()
    open()

    viewModel.accept(quest("a"))
    runCurrent()

    assertEquals(1, local.listed.value.size)
    assertEquals(AcceptStatus.ACCEPTING, state.statusOf("a"))
    local.gate!!.complete(Unit)
    runCurrent()
    assertEquals(AcceptStatus.ACCEPTED, state.statusOf("a"))
  }

  @Test
  fun aFailedBookingStillListedIsNotAccepted() = test {
    reservations = local
    local.failure = IOException("refused")
    open()

    viewModel.accept(quest("a"))
    runCurrent()

    // Firestore still lists the write the booking failed for
    assertEquals(1, local.listed.value.size)
    assertEquals(AcceptStatus.FAILED, state.statusOf("a"))
    local.refuse("local-1")
    runCurrent()
    assertEquals(AcceptStatus.FAILED, state.statusOf("a"))
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

/**
 * Lists [listed] as the explorer's reservations and adds each booking to it as pending at once, as
 * Firestore lists a write before the server answers it. Each booking then waits for [gate], if set,
 * and fails with [failure], if set, while staying listed.
 */
private class LocalWrites(private val inner: ReservationRepository) :
    ReservationRepository by inner {
  val listed = MutableStateFlow<List<Reservation>>(emptyList())
  var gate: CompletableDeferred<Unit>? = null
  var failure: Throwable? = null
  private var next = 0

  override fun observeForExplorer(uid: String): Flow<List<Reservation>> = listed

  override suspend fun createReservation(
      reservation: Reservation,
      creatorUid: String,
  ): Result<String> {
    val id = "local-${++next}"
    listed.value += reservation.copy(id = id)
    gate?.await()
    return failure?.let { Result.failure(it) } ?: Result.success(id)
  }

  /** The server refuses the reservation [id]: Firestore rolls it back. */
  fun refuse(id: String) {
    listed.value = listed.value.filterNot { it.id == id }
  }

  /** The explorer cancels the reservation [id]. */
  fun cancel(id: String) {
    listed.value =
        listed.value.map { if (it.id == id) it.copy(status = ReservationStatus.CANCELLED) else it }
  }
}
