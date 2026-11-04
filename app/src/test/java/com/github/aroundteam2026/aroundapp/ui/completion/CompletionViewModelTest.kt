// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.net.Uri
import com.github.aroundteam2026.aroundapp.model.TEST_EXPLORER_NAME
import com.github.aroundteam2026.aroundapp.model.party.FakePartyRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.reservation.Completion
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionStatus
import com.github.aroundteam2026.aroundapp.model.reservation.FakeCompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Proof
import com.github.aroundteam2026.aroundapp.model.reservation.ProofPhotoRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.completionId
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
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
          ),
      )
  private val fakeCompletions = FakeCompletionRepository { NOW }
  private val completions = GatedCompletions(fakeCompletions)
  private val photos = ScriptedProofPhotos()

  /** Whether the completions' listener fails when collected. */
  private var completionsBreak = false

  private val viewModel by lazy {
    CompletionViewModel(
        reservationId,
        EXPLORER,
        listener,
        quests,
        if (completionsBreak) BrokenCompletions(completions) else completions,
        photos,
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

  @Test
  fun startsLoading() = test {
    reservationId = book("fox")

    assertEquals(CompletionUiState(), state)
  }

  @Test
  fun anApprovedReservationShowsItsQuestAndTheProofItAsksFor() = test {
    open("fox")

    assertEquals(CompletionFormStatus.READY, state.status)
    assertEquals("Find the fox", state.questTitle)
    assertEquals(ProofType.TEXT, state.proofType)
    assertEquals("", state.answer)
    assertEquals(SaveState.Idle, state.saveState)
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
    assertEquals(SaveState.Saved(completionId(reservationId, EXPLORER)), state.saveState)
    assertEquals(0, photos.calls.size)
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
  fun aPhotoIsUploadedThenSubmittedWithItsUrl() = test {
    open("photo")
    viewModel.pickPhoto(PHOTO)
    assertTrue(state.canSubmit)

    viewModel.submit()
    runCurrent()

    assertEquals(listOf(reservationId to PHOTO), photos.calls)
    assertEquals(Proof.Photo(photos.urlOf(1)), stored().single().proof)
    assertEquals(SaveState.Saved(completionId(reservationId, EXPLORER)), state.saveState)
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
    assertEquals(SaveState.Saved(completionId(reservationId, EXPLORER)), state.saveState)
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

    assertEquals(1, photos.calls.size)
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

    assertEquals(SaveState.Saved(completionId(reservationId, EXPLORER)), state.saveState)
    assertEquals(1, stored().size)
  }

  @Test
  fun aFailedUploadSubmitsNothingAndTheRetryUploadsAgain() = test {
    open("photo")
    viewModel.pickPhoto(PHOTO)
    photos.failure = IOException("offline")

    viewModel.submit()
    runCurrent()

    assertEquals(SaveState.Failed, state.saveState)
    assertEquals(PHOTO, state.photo)
    assertEquals(0, completions.calls)

    photos.failure = null
    viewModel.submit()
    runCurrent()

    assertEquals(2, photos.calls.size)
    assertEquals(Proof.Photo(photos.urlOf(2)), stored().single().proof)
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

    assertEquals(1, photos.calls.size)
    assertEquals(Proof.Photo(photos.urlOf(1)), stored().single().proof)
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

    assertEquals(listOf(reservationId to PHOTO, reservationId to OTHER_PHOTO), photos.calls)
    assertEquals(Proof.Photo(photos.urlOf(2)), stored().single().proof)
  }

  @Test
  fun aCompletionAlreadySubmittedShowsSubmittedWhenTheFormOpens() = test {
    reservationId = book("fox")
    fakeCompletions.submit(reservationId, CAFE, EXPLORER, Proof.Text("By the till")).getOrThrow()
    viewModel
    runCurrent()

    assertEquals(SaveState.Saved(completionId(reservationId, EXPLORER)), state.saveState)
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

    assertEquals(SaveState.Saved(completionId(reservationId, EXPLORER)), state.saveState)
  }

  @Test
  fun aFailingCompletionListenerLeavesTheFormUsable() = test {
    completionsBreak = true
    open("fox")
    assertEquals(CompletionFormStatus.READY, state.status)

    viewModel.updateAnswer("By the till")
    viewModel.submit()
    runCurrent()

    assertEquals(SaveState.Saved(completionId(reservationId, EXPLORER)), state.saveState)
  }

  private companion object {
    const val CAFE = "cafe"
    const val EXPLORER = "explorer"
    const val NOW = 1_800_000_000_000L
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
    if (broken) throw IOException("offline")
    emitAll(inner.observeForExplorer(uid))
  }
}

/** Delegates to [inner], counting [submit] calls and holding each one until [gate] completes. */
private class GatedCompletions(private val inner: CompletionRepository) :
    CompletionRepository by inner {
  var gate: CompletableDeferred<Unit>? = null
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
}

/** Delegates to [inner], but the explorer's completions fail when collected. */
private class BrokenCompletions(private val inner: CompletionRepository) :
    CompletionRepository by inner {
  override fun observeForExplorer(uid: String) =
      flow<List<Completion>> { throw IOException("denied") }
}

/** Records every upload, answering with a URL numbered by the upload, or [failure] when set. */
private class ScriptedProofPhotos : ProofPhotoRepository {
  val calls = mutableListOf<Pair<String, Uri>>()
  var failure: Throwable? = null

  fun urlOf(upload: Int) = "https://storage.test/proofs/photo-$upload.jpg"

  override suspend fun upload(reservationId: String, photo: Uri): Result<String> {
    calls += reservationId to photo
    failure?.let {
      return Result.failure(it)
    }
    return Result.success(urlOf(calls.size))
  }
}
