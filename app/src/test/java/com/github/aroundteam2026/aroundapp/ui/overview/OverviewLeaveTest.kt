// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.overview

import com.github.aroundteam2026.aroundapp.model.connectivity.FakeConnectivityRepository
import com.github.aroundteam2026.aroundapp.model.party.FakePartyRepository
import com.github.aroundteam2026.aroundapp.model.party.isJoined
import com.github.aroundteam2026.aroundapp.model.quest.ScriptedQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeCompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.user.FakeUserRepository
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import java.io.IOException
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Leaving a party from the overview: a member who joined it rather than created it withdraws
 * through [OverviewViewModel.cancel], and the reservation stays for the others.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OverviewLeaveTest {
  private val dispatcher = StandardTestDispatcher()

  /**
   * The time both fakes read. The creator's membership is written at the reservation's `createdAt`,
   * which is how `createdParty` tells them apart, so the member joins a millisecond later.
   */
  private var time = START
  private val party = FakePartyRepository { time }
  private val reservations = FakeReservationRepository(now = { time }, party = party)

  private fun overviewOf(uid: String) =
      OverviewViewModel(
          uid,
          reservations,
          ScriptedQuestRepository(quest = { id -> testQuest(id = id, venueId = CAFE) }),
          FakeVenueRepository(listOf(testVenue(id = CAFE))),
          party,
          FakeCompletionRepository(),
          FakeUserRepository(),
          FakeConnectivityRepository(),
      )

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  /** A reservation [CREATOR] made, whose party [MEMBER] then joined; returns its id. */
  private suspend fun sharedReservation(): String {
    val id =
        reservations
            .createReservation(newPendingReservation("fox", CAFE, "Creator", START), CREATOR)
            .getOrThrow()
    time++
    party.join(id, MEMBER).getOrThrow()
    return id
  }

  /** Opens the overview of [uid] on its rows. */
  private fun TestScope.open(uid: String): OverviewViewModel = overviewOf(uid).also { runCurrent() }

  private suspend fun stored(id: String): ReservationStatus =
      reservations.observeForExplorer(CREATOR).first().single { it.id == id }.status

  private suspend fun joined(id: String, uid: String): Boolean =
      party.observeMembers(id).first().single { it.uid == uid }.isJoined

  @Test
  fun aMemberWhoCancelsLeavesAndTheReservationStays() = test {
    val id = sharedReservation()
    val overview = open(MEMBER)
    assertTrue(overview.uiState.value.rows.single().leave)

    overview.cancel(id)
    runCurrent()

    assertFalse(joined(id, MEMBER))
    assertTrue(joined(id, CREATOR))
    assertEquals(ReservationStatus.PENDING, stored(id))
    assertEquals(OverviewUiState(status = OverviewStatus.LOADED), overview.uiState.value)
  }

  @Test
  fun theCreatorStillCancelsTheReservation() = test {
    val id = sharedReservation()
    val overview = open(CREATOR)
    assertFalse(overview.uiState.value.rows.single().leave)

    overview.cancel(id)
    runCurrent()

    assertEquals(ReservationStatus.CANCELLED, stored(id))
    assertTrue(joined(id, MEMBER))
  }

  @Test
  fun aFailedLeaveCanBeTriedAgain() = test {
    val id = sharedReservation()
    val overview = open(MEMBER)
    party.forcedFailure = IOException("offline")

    overview.cancel(id)
    runCurrent()

    assertTrue(joined(id, MEMBER))
    assertEquals(setOf(id), overview.uiState.value.failedCancelIds)
    assertEquals(listOf(id), overview.uiState.value.rows.map { it.reservationId })

    party.forcedFailure = null
    overview.cancel(id)
    runCurrent()

    assertFalse(joined(id, MEMBER))
    assertEquals(ReservationStatus.PENDING, stored(id))
    assertEquals(OverviewUiState(status = OverviewStatus.LOADED), overview.uiState.value)
  }

  private companion object {
    const val CREATOR = "creator"
    const val MEMBER = "member"
    const val CAFE = "cafe"
    const val START = 1_800_000_000_000L
  }
}
