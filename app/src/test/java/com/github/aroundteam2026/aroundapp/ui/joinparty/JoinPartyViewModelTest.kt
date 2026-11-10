// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.joinparty

import com.github.aroundteam2026.aroundapp.model.connectivity.FakeConnectivityRepository
import com.github.aroundteam2026.aroundapp.model.party.PartyMemberStatus
import com.github.aroundteam2026.aroundapp.model.party.PartyRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewRowStatus
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests [JoinPartyViewModel] with the fake repositories: a party code is looked up as it is typed,
 * and the explorer joins the party it leads to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JoinPartyViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  /** The party code the next reservation gets. */
  private var nextCode = CODE

  private val questRepository =
      FakeQuestRepository(
          initialQuests =
              listOf(
                  testQuest(id = "fox", venueId = CAFE, title = "Fox"),
                  testQuest(id = "duo", venueId = CAFE, minPartySize = 2)
                      .copy(exactPartySize = true),
              )
      )
  private val reservations =
      FakeReservationRepository(now = { NOW }, quests = questRepository, newCode = { nextCode })
  private val lookups = ScriptedLookups(reservations)
  private val parties = ScriptedParty(reservations.party)
  private val connectivity = FakeConnectivityRepository()

  private val viewModel by lazy {
    JoinPartyViewModel(
        EXPLORER,
        lookups,
        questRepository,
        FakeVenueRepository(listOf(testVenue(id = CAFE, name = "Café Lumen"))),
        parties,
        connectivity,
    )
  }

  private val state
    get() = viewModel.uiState.value

  private val refusal
    get() = (state.lookup as JoinPartyUiState.Lookup.Refused).error

  private val found
    get() = (state.lookup as JoinPartyUiState.Lookup.Found).party

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  /** Books the quest [questId] as [creator], whose party code is [code]; returns its id. */
  private suspend fun book(
      questId: String = "fox",
      creator: String = FRIEND,
      code: String = CODE,
  ): String {
    nextCode = code
    return reservations
        .createReservation(newPendingReservation(questId, CAFE, "Ada", NOW), creator)
        .getOrThrow()
  }

  /** Types [typed] and lets the lookup it starts finish. */
  private fun TestScope.type(typed: String = TYPED) {
    viewModel.updateCode(typed)
    runCurrent()
  }

  /** Taps the button, and lets what it starts finish. */
  private fun TestScope.submit() {
    viewModel.join()
    runCurrent()
  }

  @Test
  fun startsEmptyAndTapsWithNothingTypedDoNothing() = test {
    assertEquals(JoinPartyUiState(), state)
    assertFalse(state.canSubmit)

    submit()

    assertEquals(0, lookups.lookups)
    assertEquals(JoinPartyUiState(), state)
  }

  @Test
  fun aShortCodeIsOnlyLookedUpWhenSubmitted() = test {
    book()

    type("7kq m4")
    assertEquals(0, lookups.lookups)
    assertEquals(JoinPartyUiState(code = "7kq m4"), state)
    assertTrue(state.canSubmit)

    submit()
    assertEquals(JoinPartyUiState.Error.NotFound, refusal)
  }

  @Test
  fun aFullCodeFindsItsPartyWithTheQuestVenueAndJoinedMembers() = test {
    val id = book()
    reservations.party.join(id, "bob").getOrThrow()
    reservations.party.join(id, "cy").getOrThrow()
    reservations.party.withdraw(id, "cy").getOrThrow()
    lookups.gate = CompletableDeferred()

    type()
    assertEquals(JoinPartyUiState.Lookup.Looking, state.lookup)
    lookups.gate?.complete(Unit)
    runCurrent()

    assertEquals(id, found.reservationId)
    assertEquals("Fox", found.questTitle)
    assertEquals("Café Lumen", found.venueName)
    assertEquals(2, found.partySize)
    assertEquals(OverviewRowStatus.WAITING, found.status)
  }

  @Test
  fun anUnknownOrMalformedCodeIsNotFoundAndEditingItLooksTheNewOneUp() = test {
    book()

    for (typed in listOf("ABC 234", "7KQ M0O")) {
      type(typed)
      assertEquals(typed, JoinPartyUiState.Error.NotFound, refusal)
    }

    type()
    assertTrue(state.lookup is JoinPartyUiState.Lookup.Found)
  }

  @Test
  fun aRejectedCancelledOrOrphanedPartyHasEndedAndAnApprovedOneIsClosed() = test {
    reservations.updateStatus(book(code = CODE), ReservationStatus.REJECTED).getOrThrow()
    reservations.updateStatus(book(code = OTHER_CODE), ReservationStatus.CANCELLED).getOrThrow()
    book("ghost", code = THIRD_CODE)
    reservations.updateStatus(book(code = FOURTH_CODE), ReservationStatus.APPROVED).getOrThrow()

    for (code in listOf(CODE, OTHER_CODE, THIRD_CODE)) {
      type(code)
      assertEquals(code, JoinPartyUiState.Error.Ended, refusal)
    }
    type(FOURTH_CODE)
    assertEquals(JoinPartyUiState.Error.Closed, refusal)
  }

  @Test
  fun aPartyAtItsQuestsSizeIsFullAndSaysHowManyJoined() = test {
    reservations.party.join(book("duo"), "bob").getOrThrow()

    type()

    assertEquals(JoinPartyUiState.Error.Full(joined = 2, capacity = 2), refusal)
  }

  @Test
  fun aFailedLookupSaysOfflineOrFailedAndSubmittingTriesAgain() = test {
    book()
    reservations.forcedFailure = IllegalStateException("backend")
    connectivity.online.value = false

    type()
    assertEquals(JoinPartyUiState.Error.Offline, refusal)
    connectivity.online.value = true
    submit()
    assertEquals(JoinPartyUiState.Error.Failed, refusal)

    reservations.forcedFailure = null
    submit()
    assertTrue(state.lookup is JoinPartyUiState.Lookup.Found)
  }

  @Test
  fun anAnswerToAnEditedCodeIsIgnored() = test {
    book()
    lookups.gate = CompletableDeferred()
    type()

    type("7kq")
    lookups.gate?.complete(Unit)
    runCurrent()

    assertEquals(JoinPartyUiState(code = "7kq"), state)
  }

  @Test
  fun joiningMakesTheExplorerAMemberAndLocksTheCode() = test {
    val id = book()
    type()

    submit()
    type("ABC 234")

    assertEquals(JoinPartyUiState.Join.JOINED, state.join)
    assertEquals(TYPED, state.code)
    val member = reservations.party.observeMembers(id).first().single { it.uid == EXPLORER }
    assertEquals(PartyMemberStatus.JOINED, member.status)
    assertTrue(reservations.observeForExplorer(EXPLORER).first().any { it.id == id })
  }

  @Test
  fun joiningShowsJoiningAndATapWhileJoiningJoinsOnce() = test {
    book()
    type()
    parties.gate = CompletableDeferred()

    viewModel.join()
    viewModel.join()
    runCurrent()
    assertEquals(JoinPartyUiState.Join.JOINING, state.join)
    assertFalse(state.canSubmit)

    parties.gate?.complete(Unit)
    runCurrent()

    assertEquals(1, parties.joins)
    assertEquals(JoinPartyUiState.Join.JOINED, state.join)
  }

  @Test
  fun aPartyThatFilledUpOrEndedMeanwhileIsReportedWhenJoining() = test {
    val duo = book("duo", code = CODE)
    val fox = book(code = OTHER_CODE)
    type(CODE)
    reservations.party.join(duo, "bob").getOrThrow()

    submit()
    assertEquals(JoinPartyUiState.Error.Full(joined = 2, capacity = 2), refusal)
    assertEquals(JoinPartyUiState.Join.IDLE, state.join)

    type(OTHER_CODE)
    reservations.updateStatus(fox, ReservationStatus.REJECTED).getOrThrow()
    parties.refusing = true
    submit()
    assertEquals(JoinPartyUiState.Error.Ended, refusal)
  }

  @Test
  fun theCreatorJoiningTheirOwnPartyIsToldTheyAreAlreadyIn() = test {
    book(creator = EXPLORER)
    type()

    submit()

    assertEquals(JoinPartyUiState.Error.AlreadyJoined, refusal)
    assertEquals(JoinPartyUiState.Join.IDLE, state.join)
  }

  @Test
  fun aRefusedJoinOfAnOpenPartyIsAFailureOrOfflineAndCanBeTriedAgain() = test {
    val id = book()
    type()
    parties.refusing = true

    submit()
    assertEquals(JoinPartyUiState.Error.Failed, refusal)
    connectivity.online.value = false
    submit()
    submit()
    assertEquals(JoinPartyUiState.Error.Offline, refusal)

    parties.refusing = false
    submit()
    submit()
    assertEquals(JoinPartyUiState.Join.JOINED, state.join)
    assertEquals(id, reservations.observeForExplorer(EXPLORER).first().single().id)
  }

  /** Counts the lookups of a party code, and answers each only once [gate], if set, opens. */
  private class ScriptedLookups(private val delegate: ReservationRepository) :
      ReservationRepository by delegate {
    var lookups = 0
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun findReservationIdByCode(code: String): Result<String> {
      lookups++
      gate?.await()
      return delegate.findReservationIdByCode(code)
    }
  }

  /**
   * Counts the joins, completes each only once [gate], if set, opens, and refuses them all while
   * [refusing], as the rules do for a party that can't be joined.
   */
  private class ScriptedParty(private val delegate: PartyRepository) : PartyRepository by delegate {
    var joins = 0
    var gate: CompletableDeferred<Unit>? = null
    var refusing = false

    override suspend fun join(reservationId: String, uid: String): Result<Unit> {
      joins++
      gate?.await()
      if (refusing) return Result.failure(IllegalStateException("The rules refused the join"))
      return delegate.join(reservationId, uid)
    }
  }

  private companion object {
    const val NOW = 42_000L
    const val CAFE = "cafe"
    const val EXPLORER = "explorer"
    const val FRIEND = "friend"

    /** Party codes, as stored, of the reservations a test books. */
    const val CODE = "7KQM42"
    const val OTHER_CODE = "ABC234"
    const val THIRD_CODE = "DEF567"
    const val FOURTH_CODE = "GHJ789"

    /** [CODE] as an explorer may type it. */
    const val TYPED = "7kq m42"
  }
}
