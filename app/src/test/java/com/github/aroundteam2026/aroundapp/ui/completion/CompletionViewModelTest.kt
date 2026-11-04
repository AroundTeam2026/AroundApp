// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.net.Uri
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.reservation.Completion
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionStatus
import com.github.aroundteam2026.aroundapp.model.reservation.FakeCompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Proof
import com.github.aroundteam2026.aroundapp.model.reservation.ProofPhotoRepository
import com.github.aroundteam2026.aroundapp.model.reservation.completionId
import com.github.aroundteam2026.aroundapp.ui.SaveState
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewRow
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewRowStatus
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewStatus
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewUiState
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * The ViewModel of the completion screen, which reads the overview's state for the reservation and
 * its quest, and writes through fakes of the completion and photo repositories.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CompletionViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  /** The overview's state, which tests change as the overview would. */
  private val overview = MutableStateFlow(OverviewUiState())
  private var overviewRetries = 0
  private val fakeCompletions = FakeCompletionRepository { NOW }
  private val completions = GatedCompletions(fakeCompletions)
  private val photos = ScriptedProofPhotos()

  /** Whether the completions' listener fails when collected. */
  private var completionsBreak = false

  private val viewModel by lazy {
    CompletionViewModel(
        RESERVATION,
        EXPLORER,
        overview,
        { overviewRetries++ },
        if (completionsBreak) BrokenCompletions(completions) else completions,
        photos,
    )
  }

  private val state
    get() = viewModel.uiState.value

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  /** The overview's row of the reservation [RESERVATION], approved unless [status] says not. */
  private fun row(
      proofType: ProofType,
      status: OverviewRowStatus = OverviewRowStatus.APPROVED,
      reservationId: String = RESERVATION,
  ) =
      OverviewRow(
          reservationId = reservationId,
          questId = "quest",
          venueId = CAFE,
          questTitle = "Find the fox",
          proofType = proofType,
          reward = null,
          venueName = "Café Lumen",
          address = null,
          avatar = VenueAvatar.Initials("CL"),
          slotStart = NOW,
          partySize = 1,
          status = status,
      )

  /** An overview that has loaded [rows]. */
  private fun loaded(vararg rows: OverviewRow) =
      OverviewUiState(status = OverviewStatus.LOADED, rows = rows.toList())

  /** Opens the completion of the approved reservation, whose quest asks for [proofType]. */
  private fun TestScope.open(proofType: ProofType) {
    overview.value = loaded(row(proofType))
    viewModel
    runCurrent()
  }

  private suspend fun stored() = fakeCompletions.observeForExplorer(EXPLORER).first()

  @Test fun startsLoading() = test { assertEquals(CompletionUiState(), state) }

  @Test
  fun anApprovedReservationShowsItsQuestAndTheProofItAsksFor() = test {
    open(ProofType.TEXT)

    assertEquals(CompletionFormStatus.READY, state.status)
    assertEquals("Find the fox", state.questTitle)
    assertEquals(ProofType.TEXT, state.proofType)
    assertEquals("", state.answer)
    assertEquals(SaveState.Idle, state.saveState)
  }

  @Test
  fun aPhotoQuestAsksForAPhoto() = test {
    open(ProofType.PHOTO)

    assertEquals(ProofType.PHOTO, state.proofType)
    assertEquals(null, state.photo)
  }

  @Test
  fun theFormLoadsWhileTheOverviewDoes() = test {
    viewModel
    runCurrent()

    assertEquals(CompletionFormStatus.LOADING, state.status)
  }

  @Test
  fun aReservationTheVenueHasNotApprovedIsUnavailable() = test {
    overview.value = loaded(row(ProofType.TEXT, OverviewRowStatus.WAITING))
    viewModel
    runCurrent()

    assertEquals(CompletionFormStatus.UNAVAILABLE, state.status)
  }

  @Test
  fun aReservationTheOverviewDoesNotShowIsUnavailable() = test {
    overview.value = loaded(row(ProofType.TEXT, reservationId = "another"))
    viewModel
    runCurrent()

    assertEquals(CompletionFormStatus.UNAVAILABLE, state.status)
  }

  @Test
  fun aReservationThatLeavesTheOverviewWhileTheFormIsOpenBecomesUnavailable() = test {
    open(ProofType.TEXT)

    overview.value = loaded()
    runCurrent()

    assertEquals(CompletionFormStatus.UNAVAILABLE, state.status)
  }

  @Test
  fun aFailedOverviewFailsTheFormAndRetryingAsksItToLoadAgain() = test {
    overview.value = OverviewUiState(status = OverviewStatus.FAILED)
    viewModel
    runCurrent()
    assertEquals(CompletionFormStatus.FAILED, state.status)

    viewModel.onRetry()
    assertEquals(1, overviewRetries)

    overview.value = loaded(row(ProofType.TEXT))
    runCurrent()
    assertEquals(CompletionFormStatus.READY, state.status)
  }

  @Test
  fun retryingWithoutAFailureDoesNothing() = test {
    open(ProofType.TEXT)

    viewModel.onRetry()

    assertEquals(0, overviewRetries)
  }

  @Test
  fun whatWasTypedSurvivesTheOverviewLoadingAgain() = test {
    open(ProofType.TEXT)
    viewModel.updateAnswer("By the till")

    overview.value = OverviewUiState()
    runCurrent()
    assertEquals(CompletionFormStatus.LOADING, state.status)
    overview.value = loaded(row(ProofType.TEXT))
    runCurrent()

    assertEquals(CompletionFormStatus.READY, state.status)
    assertEquals("By the till", state.answer)
  }

  @Test
  fun aBlankAnswerCannotBeSubmitted() = test {
    open(ProofType.TEXT)
    viewModel.updateAnswer("   ")

    assertFalse(state.canSubmit)
    viewModel.submit()
    runCurrent()

    assertEquals(0, completions.calls)
    assertEquals(SaveState.Idle, state.saveState)
  }

  @Test
  fun aTextAnswerIsSubmittedTrimmedAndShowsSubmitted() = test {
    open(ProofType.TEXT)
    viewModel.updateAnswer("  The fox is by the till  ")
    assertTrue(state.canSubmit)

    viewModel.submit()
    runCurrent()

    val completion = stored().single()
    assertEquals(Proof.Text("The fox is by the till"), completion.proof)
    assertEquals(RESERVATION, completion.reservationId)
    assertEquals(CAFE, completion.venueId)
    assertEquals(EXPLORER, completion.explorerUid)
    assertEquals(SaveState.Saved(completionId(RESERVATION, EXPLORER)), state.saveState)
    assertEquals(0, photos.calls.size)
  }

  @Test
  fun aTextQuestIgnoresAPickedPhoto() = test {
    open(ProofType.TEXT)
    viewModel.pickPhoto(PHOTO)

    assertFalse(state.canSubmit)
  }

  @Test
  fun aPhotoQuestCannotBeSubmittedWithoutAPhoto() = test {
    open(ProofType.PHOTO)
    viewModel.updateAnswer("a written answer")

    assertFalse(state.canSubmit)
    viewModel.submit()
    runCurrent()

    assertEquals(0, completions.calls)
  }

  @Test
  fun aPhotoIsUploadedThenSubmittedWithItsUrl() = test {
    open(ProofType.PHOTO)
    viewModel.pickPhoto(PHOTO)
    assertTrue(state.canSubmit)

    viewModel.submit()
    runCurrent()

    assertEquals(listOf(RESERVATION to PHOTO), photos.calls)
    assertEquals(Proof.Photo(photos.urlOf(1)), stored().single().proof)
    assertEquals(SaveState.Saved(completionId(RESERVATION, EXPLORER)), state.saveState)
  }

  @Test
  fun savingShowsWhileTheWriteIsPendingThenSubmitted() = test {
    open(ProofType.TEXT)
    viewModel.updateAnswer("By the till")
    val gate = CompletableDeferred<Unit>().also { completions.gate = it }

    viewModel.submit()
    runCurrent()
    assertEquals(SaveState.Saving, state.saveState)
    assertFalse(state.canSubmit)

    gate.complete(Unit)
    runCurrent()
    assertEquals(SaveState.Saved(completionId(RESERVATION, EXPLORER)), state.saveState)
  }

  @Test
  fun aDoubleTapSubmitsOnce() = test {
    open(ProofType.TEXT)
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
    open(ProofType.PHOTO)
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
    open(ProofType.TEXT)
    viewModel.updateAnswer("By the till")
    completions.gate = CompletableDeferred()

    viewModel.submit()
    runCurrent()
    viewModel.updateAnswer("something else")

    assertEquals("By the till", state.answer)
  }

  @Test
  fun aFailedSubmitKeepsTheAnswerAndCanBeRetried() = test {
    open(ProofType.TEXT)
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

    assertEquals(SaveState.Saved(completionId(RESERVATION, EXPLORER)), state.saveState)
    assertEquals(1, stored().size)
  }

  @Test
  fun aFailedUploadSubmitsNothingAndTheRetryUploadsAgain() = test {
    open(ProofType.PHOTO)
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
    open(ProofType.PHOTO)
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
    open(ProofType.PHOTO)
    viewModel.pickPhoto(PHOTO)
    fakeCompletions.forcedFailure = IOException("offline")
    viewModel.submit()
    runCurrent()

    fakeCompletions.forcedFailure = null
    viewModel.pickPhoto(OTHER_PHOTO)
    viewModel.submit()
    runCurrent()

    assertEquals(listOf(RESERVATION to PHOTO, RESERVATION to OTHER_PHOTO), photos.calls)
    assertEquals(Proof.Photo(photos.urlOf(2)), stored().single().proof)
  }

  @Test
  fun aCompletionAlreadySubmittedShowsSubmittedWhenTheFormOpens() = test {
    fakeCompletions.submit(RESERVATION, CAFE, EXPLORER, Proof.Text("By the till")).getOrThrow()
    open(ProofType.TEXT)

    assertEquals(SaveState.Saved(completionId(RESERVATION, EXPLORER)), state.saveState)
    assertFalse(state.canSubmit)
    viewModel.submit()
    runCurrent()
    assertEquals(0, completions.calls)
  }

  @Test
  fun aRejectedCompletionStillCountsAsSubmitted() = test {
    val id =
        fakeCompletions.submit(RESERVATION, CAFE, EXPLORER, Proof.Text("By the till")).getOrThrow()
    fakeCompletions.reject(id, "Not the right fox").getOrThrow()
    open(ProofType.TEXT)

    assertEquals(SaveState.Saved(id), state.saveState)
    assertEquals(CompletionStatus.REJECTED, stored().single().status)
  }

  @Test
  fun aCompletionOfAnotherReservationDoesNotCount() = test {
    fakeCompletions.submit("another", CAFE, EXPLORER, Proof.Text("By the till")).getOrThrow()
    open(ProofType.PHOTO)

    assertEquals(SaveState.Idle, state.saveState)
  }

  @Test
  fun aCompletionThatAppearsWhileTheFormIsOpenShowsSubmitted() = test {
    open(ProofType.TEXT)
    assertEquals(SaveState.Idle, state.saveState)

    fakeCompletions.submit(RESERVATION, CAFE, EXPLORER, Proof.Text("By the till")).getOrThrow()
    runCurrent()

    assertEquals(SaveState.Saved(completionId(RESERVATION, EXPLORER)), state.saveState)
  }

  @Test
  fun aFailingCompletionListenerLeavesTheFormUsable() = test {
    completionsBreak = true
    open(ProofType.TEXT)
    assertEquals(CompletionFormStatus.READY, state.status)

    viewModel.updateAnswer("By the till")
    viewModel.submit()
    runCurrent()

    assertEquals(SaveState.Saved(completionId(RESERVATION, EXPLORER)), state.saveState)
  }

  private companion object {
    const val RESERVATION = "reservation-1"
    const val CAFE = "cafe"
    const val EXPLORER = "explorer"
    const val NOW = 1_800_000_000_000L
    val PHOTO: Uri = Uri.parse("content://media/photos/1")
    val OTHER_PHOTO: Uri = Uri.parse("content://media/photos/2")
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
