// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import com.github.aroundteam2026.aroundapp.model.TEST_EXPLORER_NAME
import com.github.aroundteam2026.aroundapp.model.party.FakePartyRepository
import com.github.aroundteam2026.aroundapp.model.party.PartyMember
import com.github.aroundteam2026.aroundapp.model.party.PartyRepository
import com.github.aroundteam2026.aroundapp.model.quest.ScriptedQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Completion
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionStatus
import com.github.aroundteam2026.aroundapp.model.reservation.FakeCompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Proof
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testUsers
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.user.FakeUserRepository
import com.github.aroundteam2026.aroundapp.model.user.ScriptedUserRepository
import com.github.aroundteam2026.aroundapp.model.user.UserRepository
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.ui.quest.CancelStatus
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Accepting a quest on the venue page: [VenuePageViewModel.accept] and the state it drives. */
@OptIn(ExperimentalCoroutinesApi::class)
class VenuePageAcceptTest {
  private val dispatcher = StandardTestDispatcher()
  private val clock = { START + dispatcher.scheduler.currentTime }

  private val party = FakePartyRepository { START }
  private val fake = FakeReservationRepository({ START }, party)
  private val reservations = GatedReservationRepository(fake)
  private val quests =
      listOf(
          testQuest(id = "a", venueId = VENUE_ID, minPartySize = 1),
          testQuest(id = "b", venueId = VENUE_ID, minPartySize = 3),
      )

  private var reservationRepository: ReservationRepository = reservations
  private val users = ScriptedUserRepository(testUsers(EXPLORER))
  private var userRepository: UserRepository = users
  private var partyRepository: PartyRepository = party
  private val completions = FakeCompletionRepository { START }
  private var completionRepository: CompletionRepository = completions

  private val viewModel by lazy {
    VenuePageViewModel(
        VENUE_ID,
        FakeVenueRepository(listOf(testVenue(id = VENUE_ID))),
        ScriptedQuestRepository(activeQuestsByVenue = { flowOf(quests) }),
        reservationRepository,
        userRepository,
        partyRepository,
        completionRepository,
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

  /** Stores the explorer's reservation of [questId], which [friends] join; returns its id. */
  private suspend fun storeFor(questId: String, friends: List<String> = emptyList()): String {
    val id =
        fake
            .createReservation(newPendingReservation(questId, VENUE_ID, NAME, START), EXPLORER)
            .getOrThrow()
    friends.forEach { party.join(id, it).getOrThrow() }
    return id
  }

  @Test
  fun aQuestAcceptedFromItsDetailsStaysPickedOnceTheyClose() = test {
    openAndPick("a")
    viewModel.onQuestDetailsOpened("b")

    viewModel.accept()
    runCurrent()
    viewModel.onQuestDetailsClosed()

    assertEquals("b", state.selectedQuestId)
  }

  @Test
  fun acceptCreatesOnePendingReservationForThePickedQuest() = test {
    openAndPick("a")
    advanceTimeBy(5_000)

    viewModel.accept()
    runCurrent()

    val reservation = stored().single()
    assertEquals("a", reservation.questId)
    assertEquals(VENUE_ID, reservation.venueId)
    assertEquals(listOf(EXPLORER), party.observeMembers(reservation.id).first().map { it.uid })
    assertEquals(START + 5_000, reservation.slotStart)
    assertEquals(ReservationStatus.PENDING, reservation.status)
  }

  @Test
  fun acceptNamesTheReservationAsTheExplorersProfile() = test {
    openAndPick("a")

    viewModel.accept()
    runCurrent()

    assertEquals(TEST_EXPLORER_NAME, stored().single().explorerName)
  }

  @Test
  fun aProfileThatCantBeReadFailsWithoutReservingAndRetrySucceeds() = test {
    openAndPick("a")
    users.nextGetFailure = IOException("offline")

    viewModel.accept()
    runCurrent()
    assertEquals(AcceptStatus.FAILED, state.acceptStatus)
    assertEquals(0, reservations.calls)

    viewModel.accept()
    runCurrent()
    assertEquals(AcceptStatus.ACCEPTED, state.acceptStatus)
    assertEquals(TEST_EXPLORER_NAME, stored().single().explorerName)
  }

  @Test
  fun anExplorerWithoutAProfileCannotReserve() = test {
    userRepository = FakeUserRepository()
    openAndPick("a")

    viewModel.accept()
    runCurrent()

    assertEquals(AcceptStatus.FAILED, state.acceptStatus)
    assertEquals(0, reservations.calls)
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
  fun anAcceptedQuestKnowsItsReservation() = test {
    val id = storeFor("a")
    openAndPick("a")

    assertEquals(id, state.cancellableReservationId)
  }

  // The venue answers while the page is open: its approval makes the quest completable
  @Test
  fun aQuestCanBeCompletedOnceTheVenueApprovesItsReservation() = test {
    val id = storeFor("a")
    openAndPick("a")
    assertNull(state.completableReservationId)

    fake.updateStatus(id, ReservationStatus.APPROVED)
    runCurrent()

    assertEquals(id, state.completableReservationId)
  }

  /** Stores an approved reservation for [questId], with the proof its explorer sent for it. */
  private suspend fun storeWithProof(questId: String): String {
    val id = storeFor(questId)
    fake.updateStatus(id, ReservationStatus.APPROVED)
    return completions.submit(id, VENUE_ID, EXPLORER, Proof.Photo("gs://proofs/fox")).getOrThrow()
  }

  // The bug of #394: the details offered "Submit proof" again once the proof was sent
  @Test
  fun aSentProofShowsAndEndsCompleting() = test {
    val id = storeFor("a")
    fake.updateStatus(id, ReservationStatus.APPROVED)
    openAndPick("a")
    assertNull(state.proof)
    assertEquals(id, state.completableReservationId)

    completions.submit(id, VENUE_ID, EXPLORER, Proof.Photo("gs://proofs/fox"))
    runCurrent()

    assertEquals(CompletionStatus.PENDING, state.proof?.status)
    assertNull(state.completableReservationId)
  }

  @Test
  fun theVenuesApprovalOfTheProofShows() = test {
    val proof = storeWithProof("a")
    openAndPick("a")

    completions.approve(proof)
    runCurrent()

    assertEquals(CompletionStatus.APPROVED, state.proof?.status)
    assertNull(state.completableReservationId)
  }

  @Test
  fun theVenuesRejectionOfTheProofShowsWithItsReason() = test {
    val proof = storeWithProof("a")
    openAndPick("a")

    completions.reject(proof, "Too dark")
    runCurrent()

    assertEquals(CompletionStatus.REJECTED, state.proof?.status)
    assertEquals("Too dark", state.proof?.rejectReason)
    assertNull(state.completableReservationId)
  }

  @Test
  fun aProofForAnotherQuestDoesntShowOnThePickedOne() = test {
    storeWithProof("a")
    openAndPick("b")

    assertNull(state.proof)
  }

  @Test
  fun anotherExplorersProofDoesntShow() = test {
    val id = storeFor("a")
    fake.updateStatus(id, ReservationStatus.APPROVED)
    completions.submit(id, VENUE_ID, "friend", Proof.Photo("gs://proofs/fox"))
    openAndPick("a")

    assertNull(state.proof)
    assertEquals(id, state.completableReservationId)
  }

  // Failing to read the proofs mustn't take the page or accepting with it, nor offer a second proof
  @Test
  fun aBrokenProofsListenerOnlyStopsCompleting() = test {
    val id = storeFor("a")
    fake.updateStatus(id, ReservationStatus.APPROVED)
    completionRepository = FailingCompletionsRepository(completions, shownFirst = null)
    openAndPick("a")

    assertEquals(VenuePageStatus.LOADED, state.status)
    assertTrue(state.acceptAvailable)
    assertFalse(state.proofsAvailable)
    assertEquals(id, state.cancellableReservationId)
    assertNull(state.completableReservationId)
  }

  @Test
  fun aProofShownBeforeTheListenerBrokeStaysShown() = test {
    val proof = storeWithProof("a")
    val sent = completions.observeForExplorer(EXPLORER).first()
    assertEquals(proof, sent.single().id)
    completionRepository = FailingCompletionsRepository(completions, shownFirst = sent)
    openAndPick("a")
    assertEquals(CompletionStatus.PENDING, state.proof?.status)
    assertTrue(state.proofsAvailable)

    advanceTimeBy(FAILS_AFTER_MS + 1)

    assertFalse(state.proofsAvailable)
    assertEquals(CompletionStatus.PENDING, state.proof?.status)
  }

  @Test
  fun aReservationWithAProofIsNotCancelled() = test {
    storeWithProof("a")
    openAndPick("a")

    viewModel.cancel()
    runCurrent()

    assertEquals(ReservationStatus.APPROVED, stored().single().status)
    assertEquals(CancelStatus.NONE, state.cancelStatus)
  }

  @Test
  fun cancelEndsTheReservationAndReopensTheQuest() = test {
    val id = storeFor("a")
    openAndPick("a")

    viewModel.cancel()
    assertEquals(CancelStatus.CANCELLING, state.cancelStatus)
    runCurrent()

    assertEquals(ReservationStatus.CANCELLED, stored().single { it.id == id }.status)
    assertEquals(AcceptStatus.NONE, state.acceptStatus)
    assertEquals(CancelStatus.NONE, state.cancelStatus)
    assertNull(state.cancellableReservationId)
    assertTrue(state.failedQuestIds.isEmpty())
  }

  @Test
  fun aQuestAcceptedOnThisPageCanBeCancelledWithoutCountingAsRefused() = test {
    openAndPick("a")
    viewModel.accept()
    runCurrent()

    viewModel.cancel()
    runCurrent()

    assertEquals(AcceptStatus.NONE, state.acceptStatus)
  }

  @Test
  fun aDoubleTapCancelsOnce() = test {
    val gated = CancelFailingRepository(fake)
    reservationRepository = gated
    storeFor("a")
    openAndPick("a")

    viewModel.cancel()
    viewModel.cancel()
    runCurrent()

    assertEquals(1, gated.cancels)
  }

  @Test
  fun aFailedCancelKeepsTheReservationAndCanBeRetried() = test {
    val failing = CancelFailingRepository(fake, failures = 1)
    reservationRepository = failing
    val id = storeFor("a")
    openAndPick("a")

    viewModel.cancel()
    runCurrent()
    assertEquals(CancelStatus.FAILED, state.cancelStatus)
    assertEquals(AcceptStatus.ACCEPTED, state.acceptStatus)
    assertEquals(ReservationStatus.PENDING, stored().single { it.id == id }.status)

    viewModel.cancel()
    assertEquals(CancelStatus.CANCELLING, state.cancelStatus)
    runCurrent()
    assertEquals(CancelStatus.NONE, state.cancelStatus)
    assertEquals(ReservationStatus.CANCELLED, stored().single { it.id == id }.status)
  }

  @Test
  fun cancellingOneOfTwoReservationsLetsTheOtherBeCancelled() = test {
    storeFor("a", friends = listOf("f1"))
    storeFor("a")
    openAndPick("a")

    viewModel.cancel()
    runCurrent()

    assertEquals(CancelStatus.NONE, state.cancelStatus)

    viewModel.cancel()
    runCurrent()

    assertEquals(
        listOf(ReservationStatus.CANCELLED, ReservationStatus.CANCELLED),
        stored().map { it.status },
    )
  }

  @Test
  fun aFailedCancelOfAReservationThatLeftMeanwhileMarksNothing() = test {
    val failing = CancelFailingRepository(fake, failures = 1)
    reservationRepository = failing
    val id = storeFor("a")
    openAndPick("a")
    failing.beforeAnswering = { fake.updateStatus(id, ReservationStatus.REJECTED) }

    viewModel.cancel()
    runCurrent()

    assertEquals(CancelStatus.NONE, state.cancelStatus)
    assertTrue(state.cancellingIds.isEmpty())
  }

  @Test
  fun cancelWithoutAReservationDoesNothing() = test {
    val counting = CancelFailingRepository(fake)
    reservationRepository = counting
    openAndPick("a")

    viewModel.cancel()
    runCurrent()

    assertEquals(0, counting.cancels)
  }

  @Test
  fun ofSeveralReservationsTheOneWithTheBiggestPartyIsCancelled() {
    val small = reservation("small")
    val big = reservation("big")

    assertEquals(
        mapOf("a" to "big"),
        acceptedReservationsOf(listOf(small, big), mapOf("small" to 1, "big" to 3), VENUE_ID),
    )
  }

  @Test
  fun onlyActiveReservationsAtTheVenueCanBeCancelled() {
    val rejected = reservation("r").copy(status = ReservationStatus.REJECTED)
    val elsewhere = reservation("e").copy(venueId = "other")

    assertEquals(
        emptyMap<String, String>(),
        acceptedReservationsOf(listOf(rejected, elsewhere), emptyMap(), VENUE_ID),
    )
  }

  private fun reservation(id: String) =
      newPendingReservation("a", VENUE_ID, NAME, START).copy(id = id)

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
    fake.createReservation(newPendingReservation("a", "other-venue", NAME, START), EXPLORER)

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
    storeFor("b", friends = listOf("friend"))
    openAndPick("b")
    assertEquals(1, state.morePeopleNeeded)

    storeFor("a", friends = listOf("friend", "other"))
    viewModel.onQuestSelected("a")
    assertEquals(0, state.morePeopleNeeded) // never below zero
  }

  @Test
  fun aFriendWhoWithdrawsRaisesWhatTheQuestNeeds() = test {
    val id = storeFor("b", friends = listOf("friend"))
    openAndPick("b")
    assertEquals(1, state.morePeopleNeeded)

    party.withdraw(id, "friend").getOrThrow()
    runCurrent()

    assertEquals(2, state.morePeopleNeeded)
  }

  @Test
  fun aQuestTheExplorerWithdrewFromIsNoLongerAccepted() = test {
    val id = storeFor("a", friends = listOf("friend"))
    openAndPick("a")
    assertEquals(AcceptStatus.ACCEPTED, state.acceptStatus)

    party.withdraw(id, EXPLORER).getOrThrow()
    runCurrent()

    assertEquals(AcceptStatus.NONE, state.acceptStatus)
  }

  /** The party sizes are always all known; if one were missing, the explorer still counts. */
  @Test
  fun aPartyWithNoKnownSizeCountsJustTheExplorer() {
    val reservation = newPendingReservation("a", VENUE_ID, NAME, START).copy(id = "r")

    assertEquals(mapOf("a" to 1), acceptedPartiesOf(listOf(reservation), emptyMap(), VENUE_ID))
  }

  @Test
  fun aSecondQuestCanBeAcceptedWhileTheFirstIsStillBeingAccepted() = test {
    openAndPick("a")
    val gate = CompletableDeferred<Unit>().also { reservations.gate = it }
    viewModel.accept()
    viewModel.onQuestSelected("b")

    viewModel.accept()
    runCurrent()
    assertEquals(AcceptStatus.ACCEPTING, state.acceptStatus)

    gate.complete(Unit)
    runCurrent()
    assertEquals(setOf("a", "b"), stored().map { it.questId }.toSet())
  }

  @Test
  fun oneQuestFailingDoesNotEndTheOthersAccepting() = test {
    openAndPick("a")
    val gate = CompletableDeferred<Unit>().also { reservations.gate = it }
    reservations.failOnCall = 1 // fails at once, while the second call waits for the gate
    viewModel.accept()
    viewModel.onQuestSelected("b")
    viewModel.accept()
    runCurrent()

    assertEquals(AcceptStatus.ACCEPTING, state.acceptStatus)
    viewModel.onQuestSelected("a")
    assertEquals(AcceptStatus.FAILED, state.acceptStatus)

    gate.complete(Unit)
    runCurrent()
    viewModel.onQuestSelected("b")
    assertEquals(AcceptStatus.ACCEPTED, state.acceptStatus)
  }

  @Test
  fun aWriteTheServerRefusesShowsFailedAndCanBeRetried() = test {
    val server = ScriptedReservationRepository().also { reservationRepository = it }
    openAndPick("a")
    viewModel.accept()
    runCurrent()
    assertEquals(AcceptStatus.ACCEPTED, state.acceptStatus) // the local copy of the write

    server.list.value = emptyList() // the rules refuse it, so the copy goes away
    runCurrent()
    assertEquals(AcceptStatus.FAILED, state.acceptStatus)
    assertEquals("a", state.selectedQuestId)

    viewModel.accept()
    runCurrent()
    assertEquals(AcceptStatus.ACCEPTED, state.acceptStatus)
    assertEquals("server-2", server.list.value.single().id)
  }

  @Test
  fun aWriteWaitingForTheServerShowsAcceptingNotAccepted() = test {
    val server = ScriptedReservationRepository().also { reservationRepository = it }
    val answer = CompletableDeferred<Result<Unit>>().also { server.answer = it }
    openAndPick("a")

    viewModel.accept()
    runCurrent()
    assertEquals(1, server.list.value.size) // Firestore already lists the local copy
    assertEquals(AcceptStatus.ACCEPTING, state.acceptStatus)

    answer.complete(Result.success(Unit))
    runCurrent()
    assertEquals(AcceptStatus.ACCEPTED, state.acceptStatus)
  }

  @Test
  fun aWriteRefusedWhileWaitingNeverShowsAccepted() = test {
    val server = ScriptedReservationRepository().also { reservationRepository = it }
    val answer = CompletableDeferred<Result<Unit>>().also { server.answer = it }
    openAndPick("a")
    viewModel.accept()
    runCurrent()

    // The refusal arrives before Firestore drops its local copy
    answer.complete(Result.failure(IOException("denied")))
    runCurrent()
    assertEquals(AcceptStatus.FAILED, state.acceptStatus)

    server.list.value = emptyList()
    runCurrent()
    assertEquals(AcceptStatus.FAILED, state.acceptStatus)
  }

  @Test
  fun aWriteRefusedAfterItsCopyIsDroppedShowsFailed() = test {
    val server = ScriptedReservationRepository().also { reservationRepository = it }
    val answer = CompletableDeferred<Result<Unit>>().also { server.answer = it }
    openAndPick("a")
    viewModel.accept()
    runCurrent()

    server.list.value = emptyList()
    runCurrent()
    assertEquals(AcceptStatus.ACCEPTING, state.acceptStatus)

    answer.complete(Result.failure(IOException("denied")))
    runCurrent()
    assertEquals(AcceptStatus.FAILED, state.acceptStatus)
  }

  @Test
  fun aQuestRefusedWhileWaitingCanBeAcceptedAgain() = test {
    val server = ScriptedReservationRepository().also { reservationRepository = it }
    server.answer = CompletableDeferred(Result.failure<Unit>(IOException("denied")))
    openAndPick("a")
    viewModel.accept()
    runCurrent()
    assertEquals(AcceptStatus.FAILED, state.acceptStatus)
    server.list.value = emptyList() // Firestore drops the refused copy

    server.answer = null
    viewModel.accept()
    runCurrent()

    assertEquals(AcceptStatus.ACCEPTED, state.acceptStatus)
  }

  @Test
  fun aReservationTheVenueAnswersIsNotARefusal() = test {
    openAndPick("a")
    viewModel.accept()
    runCurrent()

    fake.updateStatus(stored().single().id, ReservationStatus.REJECTED)
    runCurrent()

    assertEquals(AcceptStatus.NONE, state.acceptStatus)
  }

  @Test
  fun aReservationNotShownYetIsNotARefusal() = test {
    val server =
        ScriptedReservationRepository()
            .apply { showsWrites = false }
            .also { reservationRepository = it }
    openAndPick("a")
    viewModel.accept()
    runCurrent()

    // The listener emits again, still without the new reservation
    server.list.value = listOf(newPendingReservation("b", VENUE_ID, NAME, START).copy(id = "x"))
    runCurrent()

    assertEquals(AcceptStatus.ACCEPTING, state.acceptStatus)
    assertTrue(state.failedQuestIds.isEmpty())
  }

  @Test
  fun aFailingReservationListenerKeepsThePageAndStopsAccepting() = test {
    reservationRepository = FailingListenerRepository(fake, shownFirst = null)
    openAndPick("a")

    assertEquals(VenuePageStatus.LOADED, state.status)
    assertEquals(listOf("a", "b"), state.quests.map { it.questId })
    assertFalse(state.acceptAvailable)

    viewModel.accept()
    runCurrent()
    assertTrue(stored().isEmpty())
    assertEquals(AcceptStatus.NONE, state.acceptStatus)
  }

  @Test
  fun aFailingPartyListenerKeepsThePageAndStopsAccepting() = test {
    storeFor("a")
    partyRepository =
        object : PartyRepository by party {
          override fun observeMembers(reservationId: String): Flow<List<PartyMember>> = flow {
            throw IOException("denied")
          }
        }
    openAndPick("b")

    assertEquals(VenuePageStatus.LOADED, state.status)
    assertFalse(state.acceptAvailable)
  }

  @Test
  fun onlyThisVenuesAcceptedPartiesAreListenedTo() = test {
    val elsewhere =
        fake
            .createReservation(
                newPendingReservation("a", "other-venue", NAME, START),
                EXPLORER,
            )
            .getOrThrow()
    val here = storeFor("a")
    val listened = mutableSetOf<String>()
    partyRepository =
        object : PartyRepository by party {
          override fun observeMembers(reservationId: String): Flow<List<PartyMember>> {
            listened += reservationId
            return if (reservationId == elsewhere) flow { throw IOException("denied") }
            else party.observeMembers(reservationId)
          }
        }
    openAndPick("b")

    assertEquals(setOf(here), listened)
    assertTrue(state.acceptAvailable)
  }

  @Test
  fun aListenerThatFailsWhileAcceptingStopsShowingAccepting() = test {
    val server =
        ScriptedReservationRepository()
            .apply { showsWrites = false }
            .also { reservationRepository = it }
    openAndPick("a")
    viewModel.accept()
    runCurrent()
    assertEquals(AcceptStatus.ACCEPTING, state.acceptStatus)

    server.broken = true
    server.list.value = listOf(newPendingReservation("b", VENUE_ID, NAME, START).copy(id = "x"))
    runCurrent()

    assertEquals(AcceptStatus.NONE, state.acceptStatus)
    assertFalse(state.acceptAvailable)
  }

  @Test
  fun aListenerThatFailsLaterKeepsWhatItShowed() = test {
    val accepted = newPendingReservation("a", VENUE_ID, NAME, START).copy(id = "r")
    reservationRepository = FailingListenerRepository(fake, shownFirst = listOf(accepted))
    openAndPick("a")
    assertEquals(AcceptStatus.ACCEPTED, state.acceptStatus)

    advanceTimeBy(FAILS_AFTER_MS + 1)

    assertEquals(AcceptStatus.ACCEPTED, state.acceptStatus)
    assertFalse(state.acceptAvailable)
  }

  private companion object {
    const val VENUE_ID = "cafe"
    const val EXPLORER = "explorer"
    const val NAME = TEST_EXPLORER_NAME
    const val START = 1_800_000_000_000L
  }
}

/** How long a [FailingListenerRepository] listener shows its first list before it fails. */
private const val FAILS_AFTER_MS = 1_000L

/** Delegates to [inner], counting [createReservation] calls and holding them until [gate] ends. */
private class GatedReservationRepository(private val inner: ReservationRepository) :
    ReservationRepository by inner {
  var gate: CompletableDeferred<Unit>? = null
  /** The 1-based number of the [createReservation] call that fails at once, or null for none. */
  var failOnCall: Int? = null
  var calls = 0
    private set

  override suspend fun createReservation(
      reservation: Reservation,
      creatorUid: String,
  ): Result<String> {
    val call = ++calls
    if (call == failOnCall) return Result.failure(IOException("offline"))
    gate?.await()
    return inner.createReservation(reservation, creatorUid)
  }
}

/**
 * Delegates to [inner], counting the status changes as [cancels] and failing the first [failures]
 * of them, after running [beforeAnswering].
 */
private class CancelFailingRepository(
    private val inner: ReservationRepository,
    private var failures: Int = 0,
) : ReservationRepository by inner {
  var cancels = 0
  var beforeAnswering: suspend () -> Unit = {}

  override suspend fun updateStatus(id: String, status: ReservationStatus): Result<Unit> {
    cancels++
    beforeAnswering()
    if (failures-- > 0) return Result.failure(IOException("offline"))
    return inner.updateStatus(id, status)
  }
}

/**
 * A repository whose reservations are [list], which a test sets to play what the server does after
 * a write: [createReservation] adds a pending copy at once, as Firestore's local copy does, unless
 * [showsWrites] is off, then returns at once or, while [answer] is set, once the server answers.
 */
private class ScriptedReservationRepository : ReservationRepository by FakeReservationRepository() {
  val list = MutableStateFlow<List<Reservation>>(emptyList())
  var showsWrites = true
  /** While set, [createReservation] waits for this answer and fails if the server refuses. */
  var answer: CompletableDeferred<Result<Unit>>? = null
  /** While set, the listener fails the next time the list changes. */
  var broken = false
  private var next = 1

  override suspend fun createReservation(
      reservation: Reservation,
      creatorUid: String,
  ): Result<String> {
    val id = "server-${next++}"
    if (showsWrites) list.value += reservation.copy(id = id)
    answer?.await()?.onFailure {
      return Result.failure(it)
    }
    return Result.success(id)
  }

  override fun observeForExplorer(uid: String): Flow<List<Reservation>> = list.map {
    if (broken) throw IOException("denied") else it
  }
}

/**
 * The explorer listener of the failing repositories below: shows [shownFirst], if any, then fails
 * [FAILS_AFTER_MS] later, in virtual time.
 */
private fun <T> failingAfter(shownFirst: List<T>?): Flow<List<T>> = flow {
  shownFirst?.let {
    emit(it)
    delay(FAILS_AFTER_MS)
  }
  throw IOException("denied")
}

/** Delegates to [inner], but its explorer listener is [failingAfter] [shownFirst]. */
private class FailingCompletionsRepository(
    private val inner: CompletionRepository,
    private val shownFirst: List<Completion>?,
) : CompletionRepository by inner {
  override fun observeForExplorer(uid: String): Flow<List<Completion>> = failingAfter(shownFirst)
}

/** Delegates to [inner], but its explorer listener is [failingAfter] [shownFirst]. */
private class FailingListenerRepository(
    private val inner: ReservationRepository,
    private val shownFirst: List<Reservation>?,
) : ReservationRepository by inner {
  override fun observeForExplorer(uid: String): Flow<List<Reservation>> = failingAfter(shownFirst)
}
