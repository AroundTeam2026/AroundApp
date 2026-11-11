// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.dashboard

import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.party.FakePartyRepository
import com.github.aroundteam2026.aroundapp.model.party.PartyRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.reservation.Completion
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeCompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Proof
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

  private val quests =
      FakeQuestRepository(
          initialQuests =
              listOf(
                  testQuest(id = "owl", venueId = VENUE_ID, title = "Find the owl"),
                  testQuest(id = "menu", venueId = VENUE_ID, title = "Order the secret menu"),
                  testQuest(id = "old", venueId = VENUE_ID, status = QuestStatus.ARCHIVED),
              )
      )
  private val party = FakePartyRepository { createdAt }
  // Given the quests, approving checks the party's size, as the rules do
  private val fake = FakeReservationRepository({ createdAt }, party, quests)
  private val gated = GatedReservationRepository(fake)
  private var reservations: ReservationRepository = gated
  private val auth = FakeAuthRepository(VENUE_ID)
  private var venues: VenueRepository =
      FakeVenueRepository(listOf(testVenue(id = VENUE_ID, name = "Café Lumière")))

  private val completions = FakeCompletionRepository { createdAt }

  /** How many party listeners the dashboard opened, per reservation id. */
  private val partyReads = mutableMapOf<String, Int>()

  /** The parties the dashboard reads: [party], counting each listener it opens. */
  private val parties =
      object : PartyRepository by party {
        override fun observeMembers(reservationId: String) =
            party.observeMembers(reservationId).also {
              partyReads[reservationId] = (partyReads[reservationId] ?: 0) + 1
            }
      }

  private val viewModel by lazy {
    VenueDashboardViewModel(auth, reservations, quests, venues, parties, completions)
  }

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
    val reservation = newPendingReservation(questId, venueId, name, slotStart)
    val id = fake.createReservation(reservation, "explorer").getOrThrow()
    repeat(partySize - 1) { party.join(id, "friend-$it").getOrThrow() }
    if (status != ReservationStatus.PENDING) fake.updateStatus(id, status).getOrThrow()
    return id
  }

  private fun ids(rows: List<DashboardReservation>) = rows.map { it.reservationId }

  /**
   * Stores a pending proof of the reservation [reservationId] by [explorerUid], sent at [sentAt].
   * Returns the id of its completion.
   */
  private suspend fun sendProof(
      reservationId: String,
      explorerUid: String = "explorer",
      proof: Proof = TEXT_PROOF,
      sentAt: Long = NOW,
      venueId: String = VENUE_ID,
  ): String {
    createdAt = sentAt
    return completions.submit(reservationId, venueId, explorerUid, proof).getOrThrow()
  }

  private fun proofIds(rows: List<DashboardProof>) = rows.map { it.completionId }

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

  @Test
  fun proofsToReviewAreListedOldestFirstWithTheirReservation() = test {
    val reservation =
        reserve(
            questId = "menu",
            name = "Grace",
            partySize = 3,
            status = ReservationStatus.APPROVED,
        )
    val newer = sendProof(reserve(status = ReservationStatus.APPROVED), sentAt = NOW - HOUR)
    val older =
        sendProof(
            reservation,
            explorerUid = "grace",
            proof = Proof.Photo("gs://proofs/grace.jpg"),
            sentAt = NOW - 2 * HOUR,
        )
    watch()

    assertEquals(listOf(older, newer), proofIds(state.proofsToReview))
    assertEquals(
        DashboardProof(
            completionId = older,
            explorerName = "Grace",
            questTitle = "Order the secret menu",
            partySize = 3,
            proofType = ProofType.PHOTO,
            sentAt = NOW - 2 * HOUR,
        ),
        state.proofsToReview.first(),
    )
    assertEquals(ProofType.TEXT, state.proofsToReview.last().proofType)
  }

  @Test
  fun proofsSentAtTheSameTimeGoSmallestIdFirst() = test {
    val first = reserve(status = ReservationStatus.APPROVED)
    val second = reserve(status = ReservationStatus.APPROVED)
    // Stored first, but its id, its reservation's, is the larger one
    val larger = sendProof(second)
    val smaller = sendProof(first)
    watch()

    assertTrue(smaller < larger)
    assertEquals(listOf(smaller, larger), proofIds(state.proofsToReview))
  }

  @Test
  fun aProofOfACancelledReservationKeepsItsPartySize() = test {
    val reservation = reserve(status = ReservationStatus.APPROVED, partySize = 3)
    sendProof(reservation)
    fake.updateStatus(reservation, ReservationStatus.CANCELLED).getOrThrow()
    watch()

    assertEquals(3, state.proofsToReview.single().partySize)
  }

  @Test
  fun reviewedProofsAndOtherVenuesProofsAreLeftOut() = test {
    val approved = reserve(status = ReservationStatus.APPROVED)
    val rejected = reserve(status = ReservationStatus.APPROVED)
    completions.approve(sendProof(approved)).getOrThrow()
    completions.reject(sendProof(rejected), "Blurry").getOrThrow()
    sendProof("elsewhere", venueId = "other-venue")
    watch()

    assertTrue(state.proofsToReview.isEmpty())
  }

  @Test
  fun aProofOfAnUnknownReservationShowsNoNameOrTitle() = test {
    sendProof("missing")
    watch()

    val row = state.proofsToReview.single()
    assertNull(row.explorerName)
    assertNull(row.questTitle)
    assertEquals(0, row.partySize)
    // Only the proof is listed, and it's enough for the dashboard not to be empty
    assertFalse(state.isEmpty)
  }

  @Test
  fun aNewProofShowsUpLiveAndLeavesOnceReviewed() = test {
    val reservation = reserve(status = ReservationStatus.APPROVED)
    watch()
    assertTrue(state.proofsToReview.isEmpty())

    val id = sendProof(reservation)
    assertEquals(listOf(id), proofIds(state.proofsToReview))

    completions.approve(id).getOrThrow()
    assertTrue(state.proofsToReview.isEmpty())
  }

  @Test
  fun aFailingProofListenerShowsFailed() = test {
    val failing =
        object : CompletionRepository by completions {
          override fun observeForVenue(venueId: String) =
              flow<List<Completion>> { throw IOException("denied") }
        }
    val failingViewModel =
        VenueDashboardViewModel(auth, reservations, quests, venues, party, failing)
    backgroundScope.launch { failingViewModel.uiState.collect {} }

    assertEquals(VenueDashboardStatus.FAILED, failingViewModel.uiState.value.status)
  }

  @Test
  fun newAndReviewedProofsOfShownReservationsDontReadThePartiesAgain() = test {
    val reservation = reserve(status = ReservationStatus.APPROVED, partySize = 2)
    watch()
    val readsBefore = partyReads.toMap()

    val proof = sendProof(reservation)
    assertEquals(listOf(proof), proofIds(state.proofsToReview))
    completions.approve(proof).getOrThrow()
    assertTrue(state.proofsToReview.isEmpty())

    assertEquals(readsBefore, partyReads)
    assertEquals(2, state.upcoming.single().partySize)
  }

  @Test
  fun aNewReservationIsNeverShownBeforeItsPartyIsRead() = test {
    reserve(status = ReservationStatus.APPROVED)
    val shown = mutableListOf<VenueDashboardUiState>()
    backgroundScope.launch { viewModel.uiState.collect { shown += it } }

    reserve(questId = "menu")

    assertEquals(1, state.needsAnswer.single().partySize)
    // Its party of 1 is read before the dashboard shows it, never as an empty party
    assertTrue(shown.flatMap { it.needsAnswer + it.upcoming }.none { it.partySize == 0 })
  }

  /** Stores an active quest of the venue for parties of [minPartySize], or exactly that. */
  private suspend fun sizedQuest(minPartySize: Int, exact: Boolean = false): String =
      quests
          .createQuest(
              testQuest(venueId = VENUE_ID, minPartySize = minPartySize)
                  .copy(exactPartySize = exact)
          )
          .getOrThrow()

  @Test
  fun aPartyOfItsQuestsSizeCanBeApproved() = test {
    val atLeast = reserve(questId = sizedQuest(3), partySize = 4)
    val exactly = reserve(questId = sizedQuest(2, exact = true), partySize = 2)
    watch()

    assertEquals(listOf(atLeast, exactly), ids(state.needsAnswer))
    assertTrue(state.needsAnswer.all { it.approveBlock == null })
  }

  @Test
  fun aPartyBelowItsQuestsSizeSaysHowManyMoreItNeeds() = test {
    reserve(questId = sizedQuest(4), partySize = 1)
    watch()

    assertEquals(ApproveBlock.TooFew(3), state.needsAnswer.single().approveBlock)
  }

  @Test
  fun aPartyAboveAnExactSizeSaysTheSize() = test {
    val questId = sizedQuest(2)
    reserve(questId = questId, partySize = 3)
    watch()
    assertNull(state.needsAnswer.single().approveBlock)

    // The venue makes the size exact while the party is bigger
    quests.updateQuest(quests.getQuest(questId)!!.copy(exactPartySize = true)).getOrThrow()

    assertEquals(ApproveBlock.TooMany(2), state.needsAnswer.single().approveBlock)
  }

  @Test
  fun aReservationOfADeletedQuestCantBeApproved() = test {
    reserve(questId = "deleted")
    watch()

    assertEquals(ApproveBlock.QuestMissing, state.needsAnswer.single().approveBlock)
  }

  @Test
  fun aPartyThatReachesItsSizeCanBeApprovedLive() = test {
    val id = reserve(questId = sizedQuest(2))
    watch()
    assertEquals(ApproveBlock.TooFew(1), state.needsAnswer.single().approveBlock)

    party.join(id, "friend").getOrThrow()

    assertNull(state.needsAnswer.single().approveBlock)
  }

  @Test
  fun approvingAPartyBelowItsSizeSavesNothing() = test {
    val id = reserve(questId = sizedQuest(3), partySize = 2)
    watch()

    viewModel.approve(id)

    assertEquals(0, gated.calls)
    assertTrue(state.savingIds.isEmpty())
    assertFalse(state.updateFailed)
    assertEquals(listOf(id), ids(state.needsAnswer))
  }

  @Test
  fun approvingFindsTheReservationAmongOthers() = test {
    reserve(questId = sizedQuest(3), requestedAt = NOW - 1)
    val approvable = reserve(requestedAt = NOW)
    watch()

    viewModel.approve(approvable)

    assertEquals(1, gated.calls)
    assertEquals(ReservationStatus.APPROVED, gated.lastStatus)
  }

  @Test
  fun approvingAReservationTheDashboardDoesntShowIsLeftToTheRepository() = test {
    watch()

    viewModel.approve("unknown")

    // Nothing on the dashboard blocks it, so the repository answers, here with "missing"
    assertEquals(1, gated.calls)
  }

  @Test
  fun aPendingReservationWithoutItsPartyCountsAsEmpty() {
    val reservation =
        newPendingReservation("owl", VENUE_ID, "Ada", slotStart = NOW).copy(id = "unread")

    val dashboard =
        dashboardOf(
            listOf(reservation),
            partySizes = emptyMap(),
            quests = listOf(testQuest(id = "owl", venueId = VENUE_ID, minPartySize = 2)),
            venue = null,
            completions = emptyList(),
        )

    val row = dashboard.needsAnswer.single()
    assertEquals(0, row.partySize)
    assertEquals(ApproveBlock.TooFew(2), row.approveBlock)
  }

  @Test
  fun aPartyBelowItsSizeCanStillBeDeclined() = test {
    val id = reserve(questId = sizedQuest(3), partySize = 2)
    watch()

    viewModel.decline(id)

    assertEquals(ReservationStatus.REJECTED, gated.lastStatus)
    assertTrue(state.isEmpty)
  }

  @Test
  fun anApprovedPartyHasNoApproveBlockEvenBelowItsSize() = test {
    val id = reserve(questId = sizedQuest(2), partySize = 2, status = ReservationStatus.APPROVED)
    watch()

    // A member may leave an approved party: it stays approved, with nothing to approve
    party.withdraw(id, "friend-0").getOrThrow()

    assertEquals(1, state.upcoming.single().partySize)
    assertNull(state.upcoming.single().approveBlock)
  }

  @Test
  fun approveBlockOfFollowsTheQuestsSize() {
    val atLeastTwo = testQuest(minPartySize = 2)
    val exactlyTwo = atLeastTwo.copy(exactPartySize = true)

    assertEquals(ApproveBlock.QuestMissing, approveBlockOf(null, 5))
    assertEquals(ApproveBlock.TooFew(2), approveBlockOf(atLeastTwo, 0))
    assertNull(approveBlockOf(atLeastTwo, 2))
    assertNull(approveBlockOf(atLeastTwo, 7))
    assertEquals(ApproveBlock.TooFew(1), approveBlockOf(exactlyTwo, 1))
    assertNull(approveBlockOf(exactlyTwo, 2))
    assertEquals(ApproveBlock.TooMany(2), approveBlockOf(exactlyTwo, 3))
  }

  private companion object {
    const val VENUE_ID = "cafe"
    const val HOUR = 3_600_000L
    const val DAY = 24 * HOUR
    const val NOW = 1_800_000_000_000L
    val TEXT_PROOF = Proof.Text("The owl hides behind the bar")
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
