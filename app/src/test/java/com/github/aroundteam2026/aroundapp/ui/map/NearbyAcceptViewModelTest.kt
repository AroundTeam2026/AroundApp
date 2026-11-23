// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import com.github.aroundteam2026.aroundapp.model.TEST_EXPLORER_NAME
import com.github.aroundteam2026.aroundapp.model.connectivity.FakeConnectivityRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeRefusedWritesStore
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.RecordedWrite
import com.github.aroundteam2026.aroundapp.model.reservation.RefusedWrites
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.WriteState
import com.github.aroundteam2026.aroundapp.model.reservation.fakeRefusedWrites
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testUsers
import com.github.aroundteam2026.aroundapp.model.user.ScriptedUserRepository
import com.github.aroundteam2026.aroundapp.ui.OFFLINE_HINT_DELAY_MS
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
import kotlinx.coroutines.test.advanceTimeBy
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
  private val connectivity = FakeConnectivityRepository()

  /** What the phone remembers of its writes; it survives the [viewModel] being made again. */
  private val remembered = FakeRefusedWritesStore()

  private val viewModel by lazy { newViewModel() }

  /** A ViewModel as after the app was killed and opened again: only [remembered] is left. */
  private fun newViewModel(writes: RefusedWrites = RefusedWrites(remembered)) =
      NearbyAcceptViewModel(
          reservations,
          users,
          EXPLORER,
          connectivity,
          clock = { NOW },
          refusedWrites = writes,
      )

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

  // Booking needs a connection, as it does from a venue's page: the quest shows the failure
  @Test
  fun acceptingFailsAtOnceWhenTheDeviceDisconnects() = test {
    open()

    connectivity.online.value = false
    runCurrent()
    viewModel.accept(quest("a"))
    runCurrent()

    assertEquals(emptyList<Reservation>(), stored())
    assertEquals(AcceptStatus.FAILED, state.statusOf("a"))
  }

  @Test
  fun acceptingWorksAgainBackOnline() = test {
    open()
    connectivity.online.value = false
    runCurrent()
    viewModel.accept(quest("a"))
    runCurrent()

    connectivity.online.value = true
    runCurrent()
    viewModel.accept(quest("a"))
    runCurrent()

    assertEquals("a", stored().single().questId)
    assertEquals(AcceptStatus.ACCEPTED, state.statusOf("a"))
  }

  // As the offline banner: a disconnect counts once it lasts, a reconnection at once
  @Test
  fun acceptingCantBeTappedWhileOfflineAndCanAgainBackOnline() = test {
    open()

    connectivity.online.value = false
    advanceTimeBy(OFFLINE_HINT_DELAY_MS)
    runCurrent()
    assertTrue(state.offline)
    assertFalse(state.canAccept)

    connectivity.online.value = true
    runCurrent()
    assertFalse(state.offline)
    assertTrue(state.canAccept)
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
    val noProfile =
        NearbyAcceptViewModel(
            gated,
            ScriptedUserRepository(),
            EXPLORER,
            connectivity,
            refusedWrites = fakeRefusedWrites(),
        ) {
          NOW
        }
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

  // The write was queued, the app was killed, and the server refuses it once it is back online
  @Test
  fun aBookingTheServerRefusesAfterARestartShowsTryAgain() = test {
    reservations = local
    open()
    viewModel.accept(quest("a"))
    runCurrent()

    val restarted = newViewModel()
    runCurrent()
    // Firestore still lists the queued write
    assertEquals(AcceptStatus.ACCEPTED, restarted.uiState.value.statusOf("a"))
    local.refuse("local-1")
    runCurrent()

    assertEquals(AcceptStatus.FAILED, restarted.uiState.value.statusOf("a"))
  }

  // The venue page shares the RefusedWrites, and the refusal is reported only to who hears it first
  @Test
  fun aRefusalTheVenuePageHeardFirstStillShowsTryAgain() = test {
    val shared = RefusedWrites(remembered)
    reservations = local
    val map = newViewModel(shared)
    runCurrent()
    map.accept(quest("a"))
    runCurrent()

    val page = shared.hearing()
    page.refusedIn(local.listed.value)
    local.refuse("local-1")
    page.refusedIn(emptyList())
    runCurrent()

    assertEquals(AcceptStatus.FAILED, map.uiState.value.statusOf("a"))
  }

  // The app opens on another tab, where the server refuses the queued write; the map opens after
  @Test
  fun aWriteTheServerRefusedBeforeTheMapOpenedShowsTryAgain() = test {
    remembered.put(RecordedWrite("local-0", "a", WriteState.SHOWN))
    reservations = local
    open()

    assertEquals(AcceptStatus.FAILED, viewModel.uiState.value.statusOf("a"))
  }

  @Test
  fun aRefusalFromBeforeARestartStillShowsTryAgainAndCanBeRetried() = test {
    remembered.put(RecordedWrite("local-0", "a", WriteState.REFUSED))
    reservations = local
    open()

    assertEquals(AcceptStatus.FAILED, state.statusOf("a"))
    viewModel.accept(quest("a"))
    runCurrent()
    assertEquals(AcceptStatus.ACCEPTED, state.statusOf("a"))
    assertEquals(AcceptStatus.NONE, newViewModel().uiState.value.statusOf("a"))
  }

  // The quest was refused before the restart, then booked elsewhere and cancelled
  @Test
  fun aRefusedQuestBookedElsewhereAndCancelledDoesntShowTryAgain() = test {
    remembered.put(RecordedWrite("local-0", "a", WriteState.REFUSED))
    reservations = local
    local.listed.value =
        listOf(newPendingReservation("a", VENUE_ID, "Ada", NOW).copy(id = "elsewhere"))
    open()
    assertEquals(AcceptStatus.ACCEPTED, state.statusOf("a"))

    local.cancel("elsewhere")
    runCurrent()

    assertEquals(AcceptStatus.NONE, state.statusOf("a"))
    assertEquals(emptySet<String>(), state.failedQuestIds)
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

  @Test
  fun aCancelledReservationOfTheQuestDoesntKeepAFailedWriteFromCounting() = test {
    reservations = local
    val cancelled =
        newPendingReservation("a", VENUE_ID, "Ada", NOW)
            .copy(id = "old", status = ReservationStatus.CANCELLED)
    local.listed.value = listOf(cancelled)
    local.failure = IOException("refused")
    open()
    viewModel.accept(quest("a"))
    runCurrent()
    assertEquals(AcceptStatus.FAILED, state.statusOf("a"))

    // Firestore drops the failed write, then lists a booking made elsewhere, say on the venue page
    local.refuse("local-1")
    runCurrent()
    local.listed.value += newPendingReservation("a", VENUE_ID, "Ada", NOW).copy(id = "elsewhere")
    runCurrent()

    assertEquals(AcceptStatus.ACCEPTED, state.statusOf("a"))
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
