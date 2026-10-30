// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.dashboard

import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

  /** When the fake stores a reservation; tests move it to order requests. */
  private var createdAt = NOW

  private val fake = FakeReservationRepository({ createdAt })
  private val gated = GatedReservationRepository(fake)
  private var reservations: ReservationRepository = gated
  private val auth = FakeAuthRepository(VENUE_ID)
  private val quests =
      FakeQuestRepository(
          initialQuests =
              listOf(
                  testQuest(id = "owl", venueId = VENUE_ID, title = "Find the owl"),
                  testQuest(id = "menu", venueId = VENUE_ID, title = "Order the secret menu"),
                  testQuest(id = "old", venueId = VENUE_ID, status = QuestStatus.ARCHIVED),
              )
      )
  private var venues: VenueRepository =
      FakeVenueRepository(listOf(testVenue(id = VENUE_ID, name = "Café Lumière")))

  private val viewModel by lazy { VenueDashboardViewModel(auth, reservations, quests, venues) }

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
   * Stores a reservation of [questId] by [name], with a party of [partySize], with its slot at
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
    val id = fake.createReservation(reservation, "explorer").getOrThrow()
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
    assertEquals(2, state.activeQuestsCount)
  }

  @Test
  fun waitsForASignedInVenue() = test {
    auth.signOut()
    watch()

    assertEquals(VenueDashboardStatus.LOADING, state.status)
    assertFalse(state.isEmpty)
  }

  @Test
  fun theHeaderShowsTheVenue() = test {
    watch()

    assertEquals("Café Lumière", state.venueName)
    assertEquals(VenueAvatar.Initials("CL"), state.avatar)
  }

  @Test
  fun aVenueThatCantBeReadLeavesTheHeaderEmpty() = test {
    venues = FakeVenueRepository()
    watch()

    assertEquals(VenueDashboardStatus.LOADED, state.status)
    assertEquals("", state.venueName)
    assertNull(state.avatar)
  }

  @Test
  fun onlyActiveQuestsAreCounted() = test {
    watch()

    quests.createQuest(testQuest(venueId = VENUE_ID, status = QuestStatus.DRAFT)).getOrThrow()
    quests.createQuest(testQuest(venueId = "other-venue")).getOrThrow()
    assertEquals(2, state.activeQuestsCount)

    quests.createQuest(testQuest(venueId = VENUE_ID)).getOrThrow()
    assertEquals(3, state.activeQuestsCount)
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
  fun everyApprovedReservationIsUpcomingOldestRequestFirstWhateverItsSlot() = test {
    val newer = reserve(status = ReservationStatus.APPROVED, requestedAt = NOW - HOUR)
    // Reservations have no visit time yet, so a slot in the past doesn't hide it
    val older =
        reserve(
            status = ReservationStatus.APPROVED,
            requestedAt = NOW - 2 * DAY,
            slotStart = NOW - 2 * DAY,
        )
    watch()

    assertEquals(listOf(older, newer), ids(state.upcoming))
    assertTrue(state.upcoming.all { it.approved })
    assertTrue(state.needsAnswer.isEmpty())
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
    val id = reserve(questId = "menu", name = "Grace", partySize = 3, requestedAt = NOW - HOUR)
    watch()

    assertEquals(
        DashboardReservation(
            reservationId = id,
            questTitle = "Order the secret menu",
            explorerName = "Grace",
            partySize = 3,
            requestedAt = NOW - HOUR,
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
    val id = reserve()
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
  fun anAnswerSavedButNotShownYetStaysSaving() = test {
    val id = reserve()
    reservations = SlowListenerRepository(fake)
    watch()

    viewModel.approve(id)
    assertEquals(setOf(id), state.savingIds)
    // A second tap before the listener shows the answer would fail: it saves nothing
    viewModel.approve(id)
    viewModel.decline(id)

    fake.updateStatus(id, ReservationStatus.APPROVED).getOrThrow()
    assertTrue(state.savingIds.isEmpty())
    assertEquals(listOf(id), ids(state.upcoming))
    assertFalse(state.updateFailed)
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

  private companion object {
    const val VENUE_ID = "cafe"
    const val HOUR = 3_600_000L
    const val DAY = 24 * HOUR
    const val NOW = 1_800_000_000_000L
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

/**
 * Delegates to [inner], but its saves succeed without changing it, as when the backend has the
 * answer and the listener hasn't shown it yet; the test then changes [inner] itself.
 */
private class SlowListenerRepository(private val inner: ReservationRepository) :
    ReservationRepository by inner {
  override suspend fun updateStatus(id: String, status: ReservationStatus): Result<Unit> =
      Result.success(Unit)
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
