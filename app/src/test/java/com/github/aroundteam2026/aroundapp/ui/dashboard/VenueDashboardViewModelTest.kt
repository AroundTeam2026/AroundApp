// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.dashboard

import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VenueDashboardViewModelTest {
  private val dispatcher = UnconfinedTestDispatcher()

  /** The test's clock: [NOW] plus the virtual time the test has advanced. */
  private val clock = { NOW + dispatcher.scheduler.currentTime }

  /** When the fake stores a reservation; tests move it to order requests. */
  private var createdAt = NOW

  private val fake = FakeReservationRepository { createdAt }
  private val gated = GatedReservationRepository(fake)
  private var reservations: ReservationRepository = gated
  private val auth = FakeAuthRepository(VENUE_ID)
  private val quests =
      FakeQuestRepository(
          initialQuests =
              listOf(
                  testQuest(id = "owl", venueId = VENUE_ID, title = "Find the owl"),
                  testQuest(id = "menu", venueId = VENUE_ID, title = "Order the secret menu"),
              )
      )

  private val viewModel by lazy { VenueDashboardViewModel(auth, reservations, quests, clock, ZONE) }

  private val state
    get() = viewModel.uiState.value

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  /** Keeps the dashboard collected, as the tab does: it only listens while watched. */
  private fun TestScope.watch() {
    backgroundScope.launch { viewModel.uiState.collect {} }
  }

  /**
   * Stores a reservation of [questId] by [name], with a party of [partySize], for a slot at
   * [slotStart], requested at [requestedAt], and moves it to [status]. Returns its id.
   */
  private suspend fun reserve(
      questId: String = "owl",
      status: ReservationStatus = ReservationStatus.PENDING,
      slotStart: Long = NOW,
      requestedAt: Long = NOW,
      name: String = "Ada",
      partySize: Int = 1,
      venueId: String = VENUE_ID,
  ): String {
    createdAt = requestedAt
    val reservation =
        newPendingReservation(questId, venueId, "explorer", name, slotStart)
            .copy(explorerUids = listOf("explorer") + List(partySize - 1) { "friend-$it" })
    val id = fake.createReservation(reservation).getOrThrow()
    if (status != ReservationStatus.PENDING) fake.updateStatus(id, status).getOrThrow()
    return id
  }

  private fun ids(rows: List<DashboardReservation>) = rows.map { it.reservationId }

  @Test
  fun loadsAnEmptyDashboard() = test {
    watch()

    assertEquals(VenueDashboardStatus.LOADED, state.status)
    assertTrue(state.isEmpty)
    assertEquals(0, state.pendingCount)
    assertEquals(0, state.todayCount)
    assertEquals(0, state.completionsCount)
  }

  @Test
  fun waitsForASignedInVenue() = test {
    auth.signOut()
    watch()

    assertEquals(VenueDashboardStatus.LOADING, state.status)
    assertFalse(state.isEmpty)
  }

  @Test
  fun pendingReservationsNeedAnAnswerOldestRequestFirst() = test {
    val newer = reserve(requestedAt = NOW - HOUR)
    val older = reserve(requestedAt = NOW - 2 * HOUR, slotStart = NOW - 2 * DAY)
    watch()

    assertEquals(listOf(older, newer), ids(state.needsAnswer))
    assertEquals(2, state.pendingCount)
    assertTrue(state.upcoming.isEmpty())
  }

  @Test
  fun approvedReservationsFromTodayOnAreUpcomingSoonestFirst() = test {
    val tomorrow = reserve(status = ReservationStatus.APPROVED, slotStart = NOW + DAY)
    val earlierToday = reserve(status = ReservationStatus.APPROVED, slotStart = START_OF_TODAY)
    reserve(status = ReservationStatus.APPROVED, slotStart = START_OF_TODAY - 1)
    watch()

    assertEquals(listOf(earlierToday, tomorrow), ids(state.upcoming))
    assertTrue(state.needsAnswer.isEmpty())
  }

  @Test
  fun todayCountsOnlyApprovedVisitsOfToday() = test {
    reserve(status = ReservationStatus.APPROVED, slotStart = START_OF_TODAY)
    reserve(status = ReservationStatus.APPROVED, slotStart = NOW + HOUR)
    reserve(status = ReservationStatus.APPROVED, slotStart = START_OF_TODAY + DAY)
    reserve(slotStart = NOW) // pending: not a visit yet
    watch()

    assertEquals(2, state.todayCount)
  }

  @Test
  fun rejectedAndCancelledReservationsAreLeftOut() = test {
    reserve(status = ReservationStatus.REJECTED)
    reserve(status = ReservationStatus.CANCELLED)
    watch()

    assertTrue(state.isEmpty)
  }

  @Test
  fun anotherVenuesReservationsAreLeftOut() = test {
    reserve(venueId = "other-venue")
    watch()

    assertTrue(state.isEmpty)
  }

  @Test
  fun aRowShowsTheQuestTheExplorerAndTheParty() = test {
    val id = reserve(questId = "menu", name = "Grace", partySize = 3, slotStart = NOW + HOUR)
    watch()

    assertEquals(
        DashboardReservation(
            reservationId = id,
            questTitle = "Order the secret menu",
            explorerName = "Grace",
            partySize = 3,
            slotStart = NOW + HOUR,
            approved = false,
        ),
        state.needsAnswer.single(),
    )
  }

  @Test
  fun aDeletedQuestAndAnUnnamedReservationShowAsUnknown() = test {
    reserve(questId = "deleted", name = "")
    watch()

    val row = state.needsAnswer.single()
    assertNull(row.questTitle)
    assertNull(row.explorerName)
  }

  @Test
  fun aNewReservationShowsUpLive() = test {
    watch()
    assertTrue(state.isEmpty)

    val id = reserve()

    assertEquals(listOf(id), ids(state.needsAnswer))
  }

  @Test
  fun approvingMovesTheReservationToUpcoming() = test {
    val id = reserve(slotStart = NOW + HOUR)
    watch()

    viewModel.approve(id)

    assertTrue(state.needsAnswer.isEmpty())
    assertEquals(listOf(id), ids(state.upcoming))
    assertTrue(state.upcoming.single().approved)
    assertEquals(ReservationStatus.APPROVED, gated.lastStatus)
  }

  @Test
  fun decliningRemovesTheReservation() = test {
    val id = reserve()
    watch()

    viewModel.decline(id)

    assertTrue(state.isEmpty)
    assertEquals(ReservationStatus.REJECTED, gated.lastStatus)
  }

  @Test
  fun anAnswerBeingSavedDisablesItsButtonsUntilDone() = test {
    val id = reserve()
    val other = reserve()
    watch()
    val gate = CompletableDeferred<Unit>().also { gated.gate = it }

    viewModel.approve(id)
    assertEquals(setOf(id), state.savingIds)

    gate.complete(Unit)
    runCurrent()
    assertTrue(state.savingIds.isEmpty())
    assertEquals(listOf(other), ids(state.needsAnswer))
  }

  @Test
  fun aDoubleTapSavesOnce() = test {
    val id = reserve()
    watch()
    val gate = CompletableDeferred<Unit>().also { gated.gate = it }

    viewModel.approve(id)
    viewModel.decline(id)
    gate.complete(Unit)
    runCurrent()

    assertEquals(1, gated.calls)
    assertEquals(ReservationStatus.APPROVED, gated.lastStatus)
  }

  @Test
  fun aFailedAnswerIsReportedUntilCleared() = test {
    val id = reserve()
    watch()
    fake.forcedFailure = IOException("offline")

    viewModel.approve(id)

    assertTrue(state.updateFailed)
    assertTrue(state.savingIds.isEmpty())
    assertEquals(listOf(id), ids(state.needsAnswer))

    viewModel.clearUpdateError()
    assertFalse(state.updateFailed)
  }

  @Test
  fun aFailedAnswerCanBeRetried() = test {
    val id = reserve()
    watch()
    fake.forcedFailure = IOException("offline")
    viewModel.decline(id)

    fake.forcedFailure = null
    viewModel.clearUpdateError()
    viewModel.decline(id)

    assertTrue(state.isEmpty)
    assertFalse(state.updateFailed)
  }

  @Test
  fun aFailedListenerShowsFailedUntilRetried() = test {
    val id = reserve()
    reservations = FailingOnceRepository(fake)
    watch()
    assertEquals(VenueDashboardStatus.FAILED, state.status)

    viewModel.retry()

    assertEquals(VenueDashboardStatus.LOADED, state.status)
    assertEquals(listOf(id), ids(state.needsAnswer))
  }

  @Test
  fun atMidnightTodaysVisitsLeaveUpcoming() = test {
    reserve(status = ReservationStatus.APPROVED, slotStart = NOW + HOUR)
    val tomorrow = reserve(status = ReservationStatus.APPROVED, slotStart = NOW + DAY)
    watch()
    assertEquals(2, state.upcoming.size)
    assertEquals(1, state.todayCount)

    advanceTimeBy(START_OF_TODAY + DAY - NOW + 1)

    assertEquals(listOf(tomorrow), ids(state.upcoming))
    assertEquals(1, state.todayCount)
  }

  @Test
  fun dayBoundariesFollowTheTimeZone() {
    assertEquals(START_OF_TODAY, startOfDay(NOW, ZONE))
    assertEquals(START_OF_TODAY + DAY, nextStartOfDay(NOW, ZONE))
    assertEquals(START_OF_TODAY, startOfDay(START_OF_TODAY, ZONE))
  }

  private companion object {
    const val VENUE_ID = "cafe"
    const val HOUR = 3_600_000L
    const val DAY = 24 * HOUR

    /** Zurich, which has no daylight saving change on the days the tests use. */
    val ZONE: ZoneId = ZoneId.of("Europe/Zurich")

    /** The test's "now": 14:00 on a Wednesday in Zurich. */
    val NOW: Long = LocalDateTime.of(2026, 10, 28, 14, 0).atZone(ZONE).toInstant().toEpochMilli()

    val START_OF_TODAY: Long = NOW - 14 * HOUR
  }
}

/**
 * Delegates to [inner], counting [updateStatus] calls, remembering the last status asked for, and
 * holding them until [gate] completes.
 */
private class GatedReservationRepository(private val inner: ReservationRepository) :
    ReservationRepository by inner {
  var gate: CompletableDeferred<Unit>? = null
  var calls = 0
    private set

  var lastStatus: ReservationStatus? = null
    private set

  override suspend fun updateStatus(id: String, status: ReservationStatus): Result<Unit> {
    calls++
    lastStatus = status
    gate?.await()
    return inner.updateStatus(id, status)
  }
}

/** Delegates to [inner], but its venue listener fails the first time it is listened to. */
private class FailingOnceRepository(private val inner: ReservationRepository) :
    ReservationRepository by inner {
  private var failed = false

  override fun observeForVenue(venueId: String): Flow<List<Reservation>> =
      if (failed) inner.observeForVenue(venueId)
      else {
        failed = true
        flow { throw IOException("denied") }
      }
}
