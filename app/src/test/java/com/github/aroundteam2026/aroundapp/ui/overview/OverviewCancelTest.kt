// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.overview

import com.github.aroundteam2026.aroundapp.model.TEST_EXPLORER_NAME
import com.github.aroundteam2026.aroundapp.model.party.FakePartyRepository
import com.github.aroundteam2026.aroundapp.model.quest.ScriptedQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
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
import kotlinx.coroutines.flow.first
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

/**
 * Cancelling a reservation from the overview: [OverviewViewModel.cancel] and the state it drives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OverviewCancelTest {
  private val dispatcher = StandardTestDispatcher()

  private val party = FakePartyRepository { START }
  private val fake = FakeReservationRepository({ START }, party)
  private val reservations = GatedStatusUpdates(fake)

  private val viewModel by lazy {
    OverviewViewModel(
        EXPLORER,
        reservations,
        ScriptedQuestRepository(quest = { id -> testQuest(id = id, venueId = CAFE) }),
        FakeVenueRepository(listOf(testVenue(id = CAFE))),
        party,
    )
  }

  private val state
    get() = viewModel.uiState.value

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  /** Books [questId] for the explorer; returns the reservation's id. */
  private suspend fun book(questId: String): String =
      fake
          .createReservation(
              newPendingReservation(questId, CAFE, EXPLORER, TEST_EXPLORER_NAME, START),
              EXPLORER,
          )
          .getOrThrow()

  /** Books a quest and opens the overview on it; returns the reservation's id. */
  private suspend fun TestScope.bookAndOpen(): String {
    val id = book("fox")
    viewModel
    runCurrent()
    return id
  }

  private suspend fun stored(id: String): ReservationStatus =
      fake.observeForExplorer(EXPLORER).first().single { it.id == id }.status

  private fun shownIds() = state.rows.map { it.reservationId }

  @Test
  fun cancelCancelsTheReservationAndItsRowLeaves() = test {
    val id = bookAndOpen()

    viewModel.cancel(id)
    runCurrent()

    assertEquals(ReservationStatus.CANCELLED, stored(id))
    assertEquals(OverviewUiState(status = OverviewStatus.LOADED), state)
  }

  @Test
  fun anApprovedReservationCanBeCancelledToo() = test {
    val id = book("fox")
    fake.updateStatus(id, ReservationStatus.APPROVED)
    viewModel
    runCurrent()

    viewModel.cancel(id)
    runCurrent()

    assertEquals(ReservationStatus.CANCELLED, stored(id))
  }

  @Test
  fun theRowShowsCancellingWhileTheWriteIsPending() = test {
    val id = bookAndOpen()
    reservations.gate = CompletableDeferred()

    viewModel.cancel(id)
    runCurrent()

    assertEquals(setOf(id), state.cancellingIds)
    assertEquals(listOf(id), shownIds())
  }

  @Test
  fun aDoubleTapCancelsOnce() = test {
    val id = bookAndOpen()
    val gate = CompletableDeferred<Unit>().also { reservations.gate = it }

    viewModel.cancel(id)
    viewModel.cancel(id)
    runCurrent()
    gate.complete(Unit)
    runCurrent()

    assertEquals(1, reservations.updates)
  }

  @Test
  fun cancellingOneRowLeavesTheOthersFreeToCancel() = test {
    val fox = book("fox")
    val cake = book("cake")
    viewModel
    runCurrent()
    reservations.gate = CompletableDeferred()

    viewModel.cancel(fox)
    viewModel.cancel(cake)
    runCurrent()

    assertEquals(setOf(fox, cake), state.cancellingIds)
  }

  @Test
  fun aFailureKeepsTheRowAndMarksItFailed() = test {
    val id = bookAndOpen()
    fake.forcedFailure = IOException("offline")

    viewModel.cancel(id)
    runCurrent()

    assertEquals(ReservationStatus.PENDING, stored(id))
    assertEquals(listOf(id), shownIds())
    assertEquals(emptySet<String>(), state.cancellingIds)
    assertEquals(setOf(id), state.failedCancelIds)
  }

  @Test
  fun aFailedCancelCanBeRetried() = test {
    val id = bookAndOpen()
    fake.forcedFailure = IOException("offline")
    viewModel.cancel(id)
    runCurrent()

    fake.forcedFailure = null
    viewModel.cancel(id)
    runCurrent()

    assertEquals(ReservationStatus.CANCELLED, stored(id))
    assertEquals(OverviewUiState(status = OverviewStatus.LOADED), state)
  }

  @Test
  fun retryingShowsCancellingInsteadOfTheOldFailure() = test {
    val id = bookAndOpen()
    fake.forcedFailure = IOException("offline")
    viewModel.cancel(id)
    runCurrent()
    fake.forcedFailure = null
    reservations.gate = CompletableDeferred()

    viewModel.cancel(id)
    runCurrent()

    assertEquals(setOf(id), state.cancellingIds)
    assertEquals(emptySet<String>(), state.failedCancelIds)
  }

  @Test
  fun aFailedMarkLeavesWithItsRow() = test {
    val id = bookAndOpen()
    fake.forcedFailure = IOException("offline")
    viewModel.cancel(id)
    runCurrent()

    fake.forcedFailure = null
    fake.updateStatus(id, ReservationStatus.REJECTED) // the venue answers meanwhile
    runCurrent()

    assertEquals(OverviewUiState(status = OverviewStatus.LOADED), state)
  }

  @Test
  fun aCancelThatFailsAfterItsRowLeftMarksNothing() = test {
    val id = bookAndOpen()
    val gate = CompletableDeferred<Unit>().also { reservations.gate = it }
    viewModel.cancel(id)
    runCurrent()

    fake.updateStatus(id, ReservationStatus.REJECTED) // the venue answers first
    runCurrent()
    gate.complete(Unit) // so the cancel fails: a rejected reservation can't be cancelled
    runCurrent()

    assertEquals(OverviewUiState(status = OverviewStatus.LOADED), state)
  }

  @Test
  fun aReservationThatIsNotShownIsNotCancelled() = test {
    val id = book("fox")
    fake.updateStatus(id, ReservationStatus.REJECTED)
    viewModel
    runCurrent()

    viewModel.cancel(id)
    viewModel.cancel("unknown")
    runCurrent()

    assertEquals(0, reservations.updates)
  }

  @Test
  fun eachRowsCancelStatusFollowsTheMarks() {
    val marked = OverviewUiState(cancellingIds = setOf("a"), failedCancelIds = setOf("b"))

    assertEquals(CancelStatus.CANCELLING, marked.cancelStatusOf("a"))
    assertEquals(CancelStatus.FAILED, marked.cancelStatusOf("b"))
    assertEquals(CancelStatus.NONE, marked.cancelStatusOf("c"))
  }

  private companion object {
    const val EXPLORER = "explorer"
    const val CAFE = "cafe"
    const val START = 1_800_000_000_000L
  }
}

/** Delegates to [inner], counting [updateStatus] calls and holding them until [gate] ends. */
private class GatedStatusUpdates(private val inner: ReservationRepository) :
    ReservationRepository by inner {
  var gate: CompletableDeferred<Unit>? = null
  var updates = 0
    private set

  override suspend fun updateStatus(id: String, status: ReservationStatus): Result<Unit> {
    updates++
    gate?.await()
    return inner.updateStatus(id, status)
  }
}
