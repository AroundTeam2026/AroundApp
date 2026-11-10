// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.net.Uri
import com.github.aroundteam2026.aroundapp.model.TEST_EXPLORER_NAME
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.party.FakePartyRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Completion
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionStatus
import com.github.aroundteam2026.aroundapp.model.reservation.FakeCompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeProofPhotoRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.MAX_TEXT_ANSWER_LENGTH
import com.github.aroundteam2026.aroundapp.model.reservation.Proof
import com.github.aroundteam2026.aroundapp.model.reservation.ProofPhotoRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.completionId
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.ui.SaveState
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The ViewModel of the completion screen, on fakes of every repository it reads or writes. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CompletionViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  private val party = FakePartyRepository { NOW }
  private val reservations = FakeReservationRepository({ NOW }, party)
  private val listener = BreakableReservations(reservations)
  private val quests =
      FakeQuestRepository(
          { NOW },
          listOf(
              testQuest(
                  id = "fox",
                  venueId = CAFE,
                  title = "Find the fox",
                  proofType = ProofType.TEXT,
              ),
              testQuest(
                  id = "photo",
                  venueId = CAFE,
                  title = "Snap the owl",
                  proofType = ProofType.PHOTO,
              ),
              testQuest(id = "short-radius", venueId = CAFE, proofType = ProofType.TEXT)
                  .copy(radiusMeters = 20),
          ),
      )
  private val questRepository = GatedQuests(quests)
  private val fakeCompletions = FakeCompletionRepository { NOW }
  private val completions = GatedCompletions(fakeCompletions)
  private val photos = FakeProofPhotoRepository()
  private var photoRepository: ProofPhotoRepository = photos
  private var venues = FakeVenueRepository(listOf(testVenue(id = CAFE)))
  private val locations = FakeLocationRepository(VENUE_LOCATION)

  private val viewModel by lazy {
    CompletionViewModel(
        reservationId,
        EXPLORER,
        listener,
        questRepository,
        venues,
        locations,
        completions,
        photoRepository,
    )
  }

  private val state
    get() = viewModel.uiState.value

  private lateinit var reservationId: String

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  /** Books [questId] as the explorer and has the venue answer with [status]; returns its id. */
  private suspend fun book(
      questId: String,
      status: ReservationStatus = ReservationStatus.APPROVED,
  ): String {
    val id =
        reservations
            .createReservation(
                newPendingReservation(questId, CAFE, TEST_EXPLORER_NAME, NOW),
                EXPLORER,
            )
            .getOrThrow()
    if (status != ReservationStatus.PENDING) reservations.updateStatus(id, status).getOrThrow()
    return id
  }

  /** Opens the completion of an approved reservation of [questId]. */
  private suspend fun TestScope.open(questId: String) {
    reservationId = book(questId)
    viewModel
    runCurrent()
  }

  private suspend fun stored() = fakeCompletions.observeForExplorer(EXPLORER).first()

  /** Where the fake stores the [upload]th photo of the reservation. */
  private fun location(upload: Int) = "gs://fake-bucket/proofs/$reservationId/photo-$upload"

  @Test
  fun theFormLoadsUntilItsQuestIsRead() = test {
    val gate = CompletableDeferred<Unit>().also { questRepository.gate = it }
    reservationId = book("fox")
    viewModel
    runCurrent()
    assertEquals(CompletionFormStatus.LOADING, state.status)

    gate.complete(Unit)
    runCurrent()

    assertEquals(CompletionFormStatus.READY, state.status)
  }

  @Test
  fun anApprovedReservationShowsItsQuestAndTheProofItAsksFor() = test {
    open("fox")

    assertEquals(CompletionFormStatus.READY, state.status)
    assertEquals("Find the fox", state.questTitle)
    assertEquals(quests.getQuest("fox")!!.requirements, state.requirements)
    assertEquals(ProofType.TEXT, state.proofType)
    assertEquals("", state.answer)
    assertEquals(SaveState.Idle, state.saveState)
  }

  @Test
  fun retakingAPhotoClearsItAndRequiresAnotherProof() = test {
    open("photo")
    viewModel.pickPhoto(PHOTO)
    assertTrue(state.canSubmit)
    viewModel.pickPhoto(null)
    assertEquals(null, state.photo)
    assertFalse(state.canSubmit)
  }

  @Test
  fun aPhotoQuestAsksForAPhoto() = test {
    open("photo")

    assertEquals(ProofType.PHOTO, state.proofType)
    assertEquals(null, state.photo)
  }

  @Test
  fun aReservationTheVenueHasNotApprovedIsUnavailable() = test {
    reservationId = book("fox", ReservationStatus.PENDING)
    viewModel
    runCurrent()

    assertEquals(CompletionFormStatus.UNAVAILABLE, state.status)
  }

  @Test
  fun aRejectedReservationIsUnavailable() = test {
    reservationId = book("fox", ReservationStatus.REJECTED)
    viewModel
    runCurrent()

    assertEquals(CompletionFormStatus.UNAVAILABLE, state.status)
  }

  @Test
  fun anUnknownReservationIsUnavailable() = test {
    reservationId = "nobody-booked-this"
    viewModel
    runCurrent()

    assertEquals(CompletionFormStatus.UNAVAILABLE, state.status)
  }

  @Test
  fun aQuestThatNoLongerExistsIsUnavailable() = test {
    reservationId = book("deleted")
    viewModel
    runCurrent()

    assertEquals(CompletionFormStatus.UNAVAILABLE, state.status)
  }

  @Test
  fun aReservationCancelledWhileTheFormIsOpenBecomesUnavailable() = test {
    open("fox")

    reservations.updateStatus(reservationId, ReservationStatus.CANCELLED).getOrThrow()
    runCurrent()

    assertEquals(CompletionFormStatus.UNAVAILABLE, state.status)
  }

  @Test
  fun aFailingReservationListenerFailsAndRetryingLoadsAgain() = test {
    reservationId = book("fox")
    listener.broken = true
    viewModel
    runCurrent()
    assertEquals(CompletionFormStatus.FAILED, state.status)

    listener.broken = false
    viewModel.onRetry()
    assertEquals(CompletionFormStatus.LOADING, state.status)
    runCurrent()

    assertEquals(CompletionFormStatus.READY, state.status)
  }

  @Test
  fun retryingWithoutAFailureDoesNothing() = test {
    open("fox")
    val listened = listener.collections

    viewModel.onRetry()
    runCurrent()

    assertEquals(listened, listener.collections)
  }

  @Test
  fun aBlankAnswerCannotBeSubmitted() = test {
    open("fox")
    viewModel.updateAnswer("   ")

    assertFalse(state.canSubmit)
    viewModel.submit()
    runCurrent()

    assertEquals(0, completions.calls)
    assertEquals(SaveState.Idle, state.saveState)
  }

  @Test
  fun aTextAnswerIsSubmittedTrimmedAndShowsSubmitted() = test {
    open("fox")
    viewModel.updateAnswer("  The fox is by the till  ")
    assertTrue(state.canSubmit)

    viewModel.submit()
    runCurrent()

    val completion = stored().single()
    assertEquals(Proof.Text("The fox is by the till"), completion.proof)
    assertEquals(reservationId, completion.reservationId)
    assertEquals(CAFE, completion.venueId)
    assertEquals(EXPLORER, completion.explorerUid)
    assertEquals(SaveState.Saved(completionId(reservationId)), state.saveState)
    assertEquals(0, photos.uploads.size)
  }

  @Test
  fun aQuestCanBeSubmittedFromInsideItsRadius() = test {
    open("short-radius")
    locations.preciseLocation = Location(46.52205, 6.6330)
    viewModel.updateAnswer("The fox")

    viewModel.submit()
    runCurrent()

    assertEquals(1, locations.preciseCalls)
    assertEquals(1, completions.calls)
    assertEquals(null, state.locationError)
  }

  @Test
  fun outsideTheQuestRadiusBlocksSubmissionEvenInsideTheVenueArea() = test {
    open("short-radius")
    // About 30 m from the venue: inside its 50 m area, outside this quest's 20 m radius.
    locations.preciseLocation = Location(46.52227, 6.6330)
    viewModel.updateAnswer("The fox")

    viewModel.submit()
    runCurrent()

    assertEquals(CompletionLocationError.TOO_FAR, state.locationError)
    assertEquals(SaveState.Idle, state.saveState)
    assertEquals(0, completions.calls)
    assertEquals("The fox", state.answer)
  }

  @Test
  fun missingPreciseLocationBlocksSubmissionWithoutLosingTheProof() = test {
    open("fox")
    locations.preciseLocation = null
    viewModel.updateAnswer("The fox")

    viewModel.submit()
    runCurrent()

    assertEquals(CompletionLocationError.UNAVAILABLE, state.locationError)
    assertEquals(SaveState.Idle, state.saveState)
    assertEquals(0, completions.calls)
    assertEquals("The fox", state.answer)
  }

  @Test
  fun aLocationProviderFailureLeavesTheProofReadyForRetry() = test {
    open("fox")
    locations.preciseFailure = IOException("location service unavailable")
    viewModel.updateAnswer("The fox")

    viewModel.submit()
    runCurrent()

    assertEquals(CompletionLocationError.UNAVAILABLE, state.locationError)
    assertEquals(SaveState.Idle, state.saveState)
    assertEquals("The fox", state.answer)
    assertEquals(0, completions.calls)

    locations.preciseFailure = null
    viewModel.submit()
    runCurrent()
    assertEquals(1, completions.calls)
  }

  @Test
  fun aMissingVenueMarkerCannotAuthorizeSubmission() = test {
    venues = FakeVenueRepository(listOf(testVenue(id = CAFE, location = null)))
    open("fox")
    viewModel.updateAnswer("The fox")

    viewModel.submit()
    runCurrent()

    assertEquals(CompletionLocationError.UNAVAILABLE, state.locationError)
    assertEquals(0, locations.preciseCalls)
    assertEquals(0, completions.calls)
  }

  @Test
  fun retryChecksTheCurrentLocationAgainAndClearsTheOldError() = test {
    open("fox")
    viewModel.updateAnswer("The fox")
    locations.preciseLocation = Location(46.5230, 6.6330)
    viewModel.submit()
    runCurrent()
    assertEquals(CompletionLocationError.TOO_FAR, state.locationError)

    locations.preciseLocation = VENUE_LOCATION
    viewModel.submit()
    runCurrent()

    assertEquals(2, locations.preciseCalls)
    assertEquals(null, state.locationError)
    assertEquals(1, completions.calls)
  }

  @Test
  fun aSecondTapWhileLocatingDoesNotStartAnotherRequest() = test {
    open("fox")
    viewModel.updateAnswer("The fox")
    val gate = CompletableDeferred<Unit>().also { locations.preciseGate = it }

    viewModel.submit()
    viewModel.submit()
    runCurrent()
    assertEquals(SaveState.Saving, state.saveState)
    assertEquals(1, locations.preciseCalls)
    assertEquals(0, completions.calls)

    gate.complete(Unit)
    runCurrent()
    assertEquals(1, completions.calls)
  }

  @Test
  fun approvalWithdrawnWhileLocatingPreventsTheWrite() = test {
    open("fox")
    viewModel.updateAnswer("The fox")
    val gate = CompletableDeferred<Unit>().also { locations.preciseGate = it }
    viewModel.submit()
    runCurrent()

    reservations.updateStatus(reservationId, ReservationStatus.CANCELLED).getOrThrow()
    runCurrent()
    gate.complete(Unit)
    runCurrent()

    assertEquals(CompletionFormStatus.UNAVAILABLE, state.status)
    assertEquals(0, completions.calls)
  }

  @Test
  fun reservationListenerFailureWhileLocatingUnlocksTheFormAfterRetry() = test {
    open("fox")
    viewModel.updateAnswer("The fox")
    val gate = CompletableDeferred<Unit>().also { locations.preciseGate = it }
    viewModel.submit()
    runCurrent()

    listener.broken = true
    book("photo") // A new reservation emission makes the active listener fail.
    runCurrent()
    assertEquals(CompletionFormStatus.FAILED, state.status)

    gate.complete(Unit)
    runCurrent()
    assertEquals(SaveState.Idle, state.saveState)
    assertEquals(0, completions.calls)

    listener.broken = false
    viewModel.onRetry()
    runCurrent()
    assertEquals(CompletionFormStatus.READY, state.status)
    viewModel.submit()
    runCurrent()
    assertEquals(1, completions.calls)
  }

  @Test
  fun completionSavedDuringAnUnavailableFixRemainsSaved() = test {
    open("fox")
    viewModel.updateAnswer("The fox")
    locations.preciseLocation = null
    val gate = CompletableDeferred<Unit>().also { locations.preciseGate = it }
    viewModel.submit()
    runCurrent()

    val id =
        fakeCompletions.submit(reservationId, CAFE, EXPLORER, Proof.Text("Earlier")).getOrThrow()
    runCurrent()
    gate.complete(Unit)
    runCurrent()

    assertEquals(SaveState.Saved(id), state.saveState)
    assertEquals(0, completions.calls)
  }

  @Test
  fun completionSavedDuringASuccessfulFixIsNotSubmittedAgain() = test {
    open("fox")
    viewModel.updateAnswer("The fox")
    val gate = CompletableDeferred<Unit>().also { locations.preciseGate = it }
    viewModel.submit()
    runCurrent()

    val id =
        fakeCompletions.submit(reservationId, CAFE, EXPLORER, Proof.Text("Earlier")).getOrThrow()
    runCurrent()
    gate.complete(Unit)
    runCurrent()

    assertEquals(SaveState.Saved(id), state.saveState)
    assertEquals(0, completions.calls)
  }

  @Test
  fun anOutOfRangePhotoIsNeverUploaded() = test {
    open("photo")
    viewModel.pickPhoto(PHOTO)
    locations.preciseLocation = Location(46.5230, 6.6330)

    viewModel.submit()
    runCurrent()

    assertEquals(CompletionLocationError.TOO_FAR, state.locationError)
    assertTrue(photos.uploads.isEmpty())
    assertEquals(0, completions.calls)
  }

  @Test
  fun aTextQuestIgnoresAPickedPhoto() = test {
    open("fox")
    viewModel.pickPhoto(PHOTO)

    assertFalse(state.canSubmit)
  }

  @Test
  fun aPhotoQuestCannotBeSubmittedWithoutAPhoto() = test {
    open("photo")
    viewModel.updateAnswer("a written answer")

    assertFalse(state.canSubmit)
    viewModel.submit()
    runCurrent()

    assertEquals(0, completions.calls)
  }

  @Test
  fun aPhotoIsUploadedThenSubmittedWithItsLocation() = test {
    open("photo")
    viewModel.pickPhoto(PHOTO)
    assertTrue(state.canSubmit)

    viewModel.submit()
    runCurrent()

    assertEquals(listOf(PHOTO), photos.uploads.values.toList())
    assertEquals(Proof.Photo(location(1)), stored().single().proof)
    assertEquals(SaveState.Saved(completionId(reservationId)), state.saveState)
  }

  @Test
  fun savingShowsWhileTheWriteIsPendingThenSubmitted() = test {
    open("fox")
    viewModel.updateAnswer("By the till")
    val gate = CompletableDeferred<Unit>().also { completions.gate = it }

    viewModel.submit()
    runCurrent()
    assertEquals(SaveState.Saving, state.saveState)
    assertFalse(state.canSubmit)

    gate.complete(Unit)
    runCurrent()
    assertEquals(SaveState.Saved(completionId(reservationId)), state.saveState)
  }

  @Test
  fun aDoubleTapSubmitsOnce() = test {
    open("fox")
    viewModel.updateAnswer("By the till")
    val gate = CompletableDeferred<Unit>().also { completions.gate = it }

    viewModel.submit()
    viewModel.submit()
    runCurrent()
    gate.complete(Unit)
    runCurrent()

    assertEquals(1, completions.calls)
    assertEquals(1, stored().size)
  }

  @Test
  fun aDoubleTapUploadsAPhotoOnce() = test {
    open("photo")
    viewModel.pickPhoto(PHOTO)
    val gate = CompletableDeferred<Unit>().also { completions.gate = it }

    viewModel.submit()
    viewModel.submit()
    runCurrent()
    gate.complete(Unit)
    runCurrent()

    assertEquals(1, photos.uploads.size)
    assertEquals(1, completions.calls)
  }

  @Test
  fun theFormCannotBeEditedWhileItIsSaving() = test {
    open("fox")
    viewModel.updateAnswer("By the till")
    completions.gate = CompletableDeferred()

    viewModel.submit()
    runCurrent()
    viewModel.updateAnswer("something else")

    assertEquals("By the till", state.answer)
  }

  @Test
  fun aFailedSubmitKeepsTheAnswerAndCanBeRetried() = test {
    open("fox")
    viewModel.updateAnswer("By the till")
    fakeCompletions.forcedFailure = IOException("offline")

    viewModel.submit()
    runCurrent()

    assertEquals(SaveState.Failed, state.saveState)
    assertEquals("By the till", state.answer)
    assertTrue(state.canSubmit)
    assertTrue(stored().isEmpty())

    fakeCompletions.forcedFailure = null
    viewModel.submit()
    runCurrent()

    assertEquals(SaveState.Saved(completionId(reservationId)), state.saveState)
    assertEquals(1, stored().size)
  }

  @Test
  fun aFailedUploadSubmitsNothingAndTheRetryUploadsAgain() = test {
    open("photo")
    viewModel.pickPhoto(PHOTO)
    photos.forcedFailure = IOException("offline")

    viewModel.submit()
    runCurrent()

    assertEquals(SaveState.Failed, state.saveState)
    assertEquals(PHOTO, state.photo)
    assertEquals(0, completions.calls)

    photos.forcedFailure = null
    viewModel.submit()
    runCurrent()

    assertEquals(1, photos.uploads.size)
    assertEquals(Proof.Photo(location(1)), stored().single().proof)
  }

  @Test
  fun aSubmitThatFailsAfterTheUploadReusesTheUploadOnRetry() = test {
    open("photo")
    viewModel.pickPhoto(PHOTO)
    fakeCompletions.forcedFailure = IOException("offline")
    viewModel.submit()
    runCurrent()
    assertEquals(SaveState.Failed, state.saveState)

    fakeCompletions.forcedFailure = null
    viewModel.submit()
    runCurrent()

    assertEquals(1, photos.uploads.size)
    assertEquals(Proof.Photo(location(1)), stored().single().proof)
  }

  @Test
  fun aNewPhotoAfterAFailureIsUploadedInsteadOfTheOldOne() = test {
    open("photo")
    viewModel.pickPhoto(PHOTO)
    fakeCompletions.forcedFailure = IOException("offline")
    viewModel.submit()
    runCurrent()

    fakeCompletions.forcedFailure = null
    viewModel.pickPhoto(OTHER_PHOTO)
    viewModel.submit()
    runCurrent()

    assertEquals(listOf(PHOTO, OTHER_PHOTO), photos.uploads.values.toList())
    assertEquals(Proof.Photo(location(2)), stored().single().proof)
  }

  @Test
  fun aCompletionAlreadySubmittedShowsSubmittedWhenTheFormOpens() = test {
    reservationId = book("fox")
    fakeCompletions.submit(reservationId, CAFE, EXPLORER, Proof.Text("By the till")).getOrThrow()
    viewModel
    runCurrent()

    assertEquals(SaveState.Saved(completionId(reservationId)), state.saveState)
    assertFalse(state.canSubmit)
    viewModel.submit()
    runCurrent()
    assertEquals(0, completions.calls)
  }

  @Test
  fun aRejectedCompletionStillCountsAsSubmitted() = test {
    reservationId = book("fox")
    val id =
        fakeCompletions
            .submit(reservationId, CAFE, EXPLORER, Proof.Text("By the till"))
            .getOrThrow()
    fakeCompletions.reject(id, "Not the right fox").getOrThrow()
    viewModel
    runCurrent()

    assertEquals(SaveState.Saved(id), state.saveState)
    assertEquals(CompletionStatus.REJECTED, stored().single().status)
  }

  @Test
  fun aCompletionOfAnotherReservationDoesNotCount() = test {
    val other = book("fox")
    fakeCompletions.submit(other, CAFE, EXPLORER, Proof.Text("By the till")).getOrThrow()
    open("photo")

    assertEquals(SaveState.Idle, state.saveState)
  }

  @Test
  fun aCompletionThatAppearsWhileTheFormIsOpenShowsSubmitted() = test {
    open("fox")
    assertEquals(SaveState.Idle, state.saveState)

    fakeCompletions.submit(reservationId, CAFE, EXPLORER, Proof.Text("By the till")).getOrThrow()
    runCurrent()

    assertEquals(SaveState.Saved(completionId(reservationId)), state.saveState)
  }

  @Test
  fun aPartyMembersCompletionOfTheSameReservationShowsSubmitted() = test {
    reservationId = book("fox")
    fakeCompletions.submit(reservationId, CAFE, "friend", Proof.Text("By the till")).getOrThrow()
    viewModel
    runCurrent()

    assertEquals(SaveState.Saved(completionId(reservationId)), state.saveState)
    assertFalse(state.canSubmit)
  }

  @Test
  fun aSubmitRefusedAsAlreadyStoredDoesNotUndoSubmitted() = test {
    reservationId = book("fox")
    // Completed earlier, but the listener's first answer is still on its way
    fakeCompletions.submit(reservationId, CAFE, EXPLORER, Proof.Text("By the till")).getOrThrow()
    val listenerGate = CompletableDeferred<Unit>().also { completions.listenerGate = it }
    val submitGate = CompletableDeferred<Unit>().also { completions.gate = it }
    viewModel
    runCurrent()
    viewModel.updateAnswer("By the till")
    viewModel.submit()
    runCurrent()
    assertEquals(SaveState.Saving, state.saveState)

    listenerGate.complete(Unit)
    runCurrent()
    assertEquals(SaveState.Saved(completionId(reservationId)), state.saveState)

    submitGate.complete(Unit) // the submit now fails: it is already stored
    runCurrent()

    assertEquals(SaveState.Saved(completionId(reservationId)), state.saveState)
  }

  @Test
  fun aFailingCompletionListenerLeavesTheFormUsable() = test {
    completions.listenerFails = true
    open("fox")
    assertEquals(CompletionFormStatus.READY, state.status)

    viewModel.updateAnswer("By the till")
    viewModel.submit()
    runCurrent()

    assertEquals(SaveState.Saved(completionId(reservationId)), state.saveState)
  }

  @Test
  fun aTrimmedAnswerAtTheLimitIsAllowedAndStoredWhole() = test {
    open("fox")
    val answer = "x".repeat(MAX_TEXT_ANSWER_LENGTH)
    viewModel.updateAnswer("  $answer  ")
    viewModel.submit()
    runCurrent()

    assertEquals(Proof.Text(answer), stored().single().proof)
    assertTrue(state.saveState is SaveState.Saved)
  }

  @Test
  fun oversizedUploadedLocationFailsWithoutCrashingOrSubmitting() = test {
    photoRepository =
        object : ProofPhotoRepository by photos {
          override suspend fun upload(reservationId: String, photo: Uri) =
              Result.success("x".repeat(2001))
        }
    open("photo")
    viewModel.pickPhoto(PHOTO)
    viewModel.submit()
    runCurrent()

    assertEquals(SaveState.Failed, state.saveState)
    assertEquals(PHOTO, state.photo)
    assertTrue(state.isEditable)
    assertEquals(0, completions.calls)
    assertTrue(stored().isEmpty())
  }

  private companion object {
    const val CAFE = "cafe"
    const val EXPLORER = "explorer"
    const val NOW = 1_800_000_000_000L
    val VENUE_LOCATION = Location(46.5220, 6.6330)
    val PHOTO: Uri = Uri.parse("content://media/photos/1")
    val OTHER_PHOTO: Uri = Uri.parse("content://media/photos/2")
  }
}

/** Delegates to [inner], and can have the explorer's reservations fail when collected. */
private class BreakableReservations(private val inner: ReservationRepository) :
    ReservationRepository by inner {
  var broken = false

  /** How many times the explorer's reservations were collected. */
  var collections = 0
    private set

  override fun observeForExplorer(uid: String): Flow<List<Reservation>> = flow {
    collections++
    inner.observeForExplorer(uid).collect {
      if (broken) throw IOException("offline")
      emit(it)
    }
  }
}

/**
 * Delegates to [inner], counting [submit] calls and holding each one until [gate] completes. Its
 * listener of a reservation's completions can be held until [listenerGate] completes, or made to
 * fail, as a backend stopping it would.
 */
private class GatedCompletions(private val inner: CompletionRepository) :
    CompletionRepository by inner {
  var gate: CompletableDeferred<Unit>? = null
  var listenerGate: CompletableDeferred<Unit>? = null
  var listenerFails = false
  var calls = 0
    private set

  override suspend fun submit(
      reservationId: String,
      venueId: String,
      explorerUid: String,
      proof: Proof,
  ): Result<String> {
    calls++
    gate?.await()
    return inner.submit(reservationId, venueId, explorerUid, proof)
  }

  override fun observeForReservation(reservationId: String): Flow<List<Completion>> = flow {
    listenerGate?.await()
    if (listenerFails) throw IOException("denied")
    emitAll(inner.observeForReservation(reservationId))
  }
}

/** Delegates to [inner], holding every quest read until [gate] completes. */
private class GatedQuests(private val inner: QuestRepository) : QuestRepository by inner {
  var gate: CompletableDeferred<Unit>? = null

  override suspend fun getQuest(questId: String): Quest? {
    gate?.await()
    return inner.getQuest(questId)
  }
}
