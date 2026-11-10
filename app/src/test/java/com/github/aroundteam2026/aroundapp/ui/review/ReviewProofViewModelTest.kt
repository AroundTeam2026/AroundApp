// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.review

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.party.FakePartyRepository
import com.github.aroundteam2026.aroundapp.model.party.PartyMember
import com.github.aroundteam2026.aroundapp.model.party.PartyRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.reservation.Completion
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionStatus
import com.github.aroundteam2026.aroundapp.model.reservation.FakeCompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeProofPhotoRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.MAX_REJECT_REASON_LENGTH
import com.github.aroundteam2026.aroundapp.model.reservation.Proof
import com.github.aroundteam2026.aroundapp.model.reservation.ProofPhotoRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.completionId
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
// Native graphics, so the photos the fake decoder returns can be real ImageBitmaps
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReviewProofViewModelTest {
  private val dispatcher = UnconfinedTestDispatcher()

  private val party = FakePartyRepository { NOW }
  private val reservations = FakeReservationRepository({ NOW }, party)
  private val completions = FakeCompletionRepository { SENT_AT }
  private val quests =
      FakeQuestRepository(
          initialQuests =
              listOf(
                  testQuest(
                      id = "fox",
                      venueId = VENUE_ID,
                      title = "Find the hidden fox",
                      reward = REWARD,
                  )
              )
      )
  private val photos = FakeProofPhotoRepository { PHOTO_BYTES }

  // The repositories the ViewModel uses: the fakes above unless a test wraps them
  private var reviewed: CompletionRepository = completions
  private var readReservations: ReservationRepository = reservations
  private var readParty: PartyRepository = party
  private var readQuests: QuestRepository = quests
  private var downloaded: ProofPhotoRepository = photos

  /** What the fake photo decodes to; the real decoder is tested with the screen, on images. */
  private val image by lazy { ImageBitmap(1, 1) }
  private val decoded = mutableListOf<ByteArray>()
  private var decoding: suspend (ByteArray) -> ImageBitmap? = { bytes ->
    image.also { decoded += bytes }
  }

  /** The proof the ViewModel reviews; set by [sendProof] or by the test. */
  private var id = completionId(RESERVATION_ID_UNKNOWN)

  /** Who is signed in when the ViewModel starts: the venue, unless a test signs it out. */
  private var signedIn: String? = VENUE_ID

  private val viewModel by lazy {
    ReviewProofViewModel(
        id,
        FakeAuthRepository(signedIn),
        reviewed,
        readReservations,
        readParty,
        readQuests,
        downloaded,
        decoding,
    )
  }

  private val state
    get() = viewModel.uiState.value

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  /** Keeps the state collected, as the screen does: it only listens while watched. */
  private fun TestScope.watch() {
    backgroundScope.launch { viewModel.uiState.collect {} }
  }

  /** Stores an approved reservation of the fox quest by Yusuf with a party of [partySize]. */
  private suspend fun reserve(partySize: Int = 1): String {
    val reservation = newPendingReservation("fox", VENUE_ID, "Yusuf S.", slotStart = NOW)
    val reservationId = reservations.createReservation(reservation, EXPLORER_UID).getOrThrow()
    repeat(partySize - 1) { party.join(reservationId, "friend-$it").getOrThrow() }
    reservations.updateStatus(reservationId, ReservationStatus.APPROVED).getOrThrow()
    return reservationId
  }

  /** Sends [proof] for [reservationId] and makes it the proof the ViewModel reviews. */
  private suspend fun sendProof(reservationId: String, proof: Proof = TEXT_PROOF) {
    id = completions.submit(reservationId, VENUE_ID, EXPLORER_UID, proof).getOrThrow()
  }

  /** The completion the ViewModel reviews, as stored. */
  private suspend fun stored(): Completion =
      completions.observeForVenue(VENUE_ID).first().single { it.id == id }

  @Test
  fun showsTheProofWithItsExplorerPartyAndQuest() = test {
    sendProof(reserve(partySize = 3))
    watch()

    assertEquals(ReviewProofStatus.LOADED, state.status)
    assertEquals("Yusuf S.", state.explorerName)
    assertEquals(3, state.partySize)
    assertEquals(SENT_AT, state.sentAt)
    assertEquals("Find the hidden fox", state.questTitle)
    assertEquals("Requirements", state.requirements)
    assertEquals(REWARD, state.reward)
    assertEquals(TEXT_PROOF, state.proof)
    assertNull(state.photo)
    assertFalse(state.reviewed)
    assertTrue(state.canApprove)
  }

  @Test
  fun aPhotoProofShowsItsDownloadedPhoto() = test {
    val reservationId = reserve()
    val location = photos.upload(reservationId, PHOTO).getOrThrow()
    sendProof(reservationId, Proof.Photo(location))
    watch()

    val photo = state.photo
    assertTrue(photo is ProofPhoto.Loaded)
    assertSame(image, (photo as ProofPhoto.Loaded).image)
    assertArrayEquals(PHOTO_BYTES, decoded.single())
  }

  // An approval is final, so a photo nobody can see fails like a download, with a retry
  @Test
  fun aPhotoThatIsNotAnImageFailsUntilRetried() = test {
    val reservationId = reserve()
    val location = photos.upload(reservationId, PHOTO).getOrThrow()
    sendProof(reservationId, Proof.Photo(location))
    var readable = false
    decoding = { if (readable) image else null }
    watch()

    assertEquals(ProofPhoto.Failed, state.photo)
    assertTrue(state.canReject)
    assertFalse(state.canApprove)

    readable = true
    viewModel.retry()

    assertTrue(state.canApprove)
  }

  @Test
  fun aPhotoThatCantBeDownloadedFailsUntilRetried() = test {
    val reservationId = reserve()
    val location = photos.upload(reservationId, PHOTO).getOrThrow()
    sendProof(reservationId, Proof.Photo(location))
    photos.forcedFailure = IOException("offline")
    watch()

    assertEquals(ProofPhoto.Failed, state.photo)
    // The proof itself still shows and can be rejected, but not approved unseen
    assertTrue(state.canReject)
    assertFalse(state.canApprove)

    photos.forcedFailure = null
    viewModel.retry()

    assertTrue(state.photo is ProofPhoto.Loaded)
    assertTrue(state.canApprove)
  }

  // The listeners stop 5 s after the screen does, e.g. while the app is in the background
  @Test
  fun aDownloadedPhotoIsKeptWhenTheListenersRestart() = test {
    val reservationId = reserve()
    val location = photos.upload(reservationId, PHOTO).getOrThrow()
    sendProof(reservationId, Proof.Photo(location))
    var downloads = 0
    downloaded =
        object : ProofPhotoRepository by photos {
          override suspend fun download(location: String): Result<ByteArray> {
            downloads++
            return photos.download(location)
          }
        }
    var listens = 0
    reviewed =
        object : CompletionRepository by completions {
          override fun observeForVenue(venueId: String): Flow<List<Completion>> {
            listens++
            return completions.observeForVenue(venueId)
          }
        }
    val watching = backgroundScope.launch { viewModel.uiState.collect {} }
    val photo = state.photo
    assertTrue(photo is ProofPhoto.Loaded)

    watching.cancel()
    advanceTimeBy(STOPPED_LONG_ENOUGH_MS)
    watch()

    // The listeners did restart, but the photo wasn't downloaded again
    assertEquals(2, listens)
    assertEquals(1, downloads)
    assertSame(photo, state.photo)
  }

  @Test
  fun aPhotoStillDownloadingCantBeApproved() = test {
    val reservationId = reserve()
    val location = photos.upload(reservationId, PHOTO).getOrThrow()
    sendProof(reservationId, Proof.Photo(location))
    val gate = CompletableDeferred<Unit>()
    downloaded =
        object : ProofPhotoRepository by photos {
          override suspend fun download(location: String): Result<ByteArray> {
            gate.await()
            return photos.download(location)
          }
        }
    watch()

    assertEquals(ProofPhoto.Loading, state.photo)
    assertTrue(state.canReject)
    assertFalse(state.canApprove)

    viewModel.approve()

    assertEquals(CompletionStatus.PENDING, stored().status)

    gate.complete(Unit)
    advanceUntilIdle()

    assertTrue(state.canApprove)
  }

  @Test
  fun approvingSavesOnceAndEndsReviewed() = test {
    sendProof(reserve())
    val gate = CompletableDeferred<Unit>()
    var approvals = 0
    reviewed =
        object : CompletionRepository by completions {
          override suspend fun approve(id: String): Result<Unit> {
            approvals++
            gate.await()
            return completions.approve(id)
          }
        }
    watch()

    viewModel.approve()
    viewModel.approve()

    assertEquals(1, approvals)
    assertTrue(state.saving)
    assertFalse(state.canReject)

    gate.complete(Unit)
    advanceUntilIdle()

    assertEquals(CompletionStatus.APPROVED, stored().status)
    assertTrue(state.reviewed)
    assertFalse(state.saving)
  }

  @Test
  fun rejectingSendsTheTrimmedReason() = test {
    sendProof(reserve())
    watch()

    viewModel.reject("  Photo is blurry  ")

    assertEquals(CompletionStatus.REJECTED, stored().status)
    assertEquals("Photo is blurry", stored().rejectReason)
    assertTrue(state.reviewed)
  }

  @Test
  fun aBlankReasonRejectsNothing() = test {
    sendProof(reserve())
    watch()

    viewModel.reject("   ")

    assertEquals(CompletionStatus.PENDING, stored().status)
    assertFalse(state.saving)
  }

  // The rules would refuse it on every try, with only a generic error
  @Test
  fun aReasonOverTheLimitRejectsNothing() = test {
    sendProof(reserve())
    watch()

    viewModel.reject("x".repeat(MAX_REJECT_REASON_LENGTH + 1))

    assertEquals(CompletionStatus.PENDING, stored().status)

    viewModel.reject("x".repeat(MAX_REJECT_REASON_LENGTH))

    assertEquals(CompletionStatus.REJECTED, stored().status)
  }

  // The screen keeps Back disabled while saving, as leaving would cancel the write
  @Test
  fun anAnswerInFlightKeepsSavingWhileTheProofFailsToLoad() = test {
    sendProof(reserve())
    val saved = CompletableDeferred<Unit>()
    val breakListener = MutableSharedFlow<Unit>()
    reviewed =
        object : CompletionRepository by completions {
          override fun observeForVenue(venueId: String): Flow<List<Completion>> =
              merge(
                  completions.observeForVenue(venueId),
                  breakListener.map<Unit, List<Completion>> { throw IOException("denied") },
              )

          override suspend fun approve(id: String): Result<Unit> {
            saved.await()
            return completions.approve(id)
          }
        }
    watch()
    viewModel.approve()

    breakListener.emit(Unit)

    assertEquals(ReviewProofStatus.FAILED, state.status)
    assertTrue(state.saving)

    saved.complete(Unit)
    advanceUntilIdle()

    assertFalse(state.saving)
  }

  @Test
  fun aFailedAnswerIsReportedUntilCleared() = test {
    sendProof(reserve())
    watch()
    completions.forcedFailure = IOException("offline")

    viewModel.approve()

    assertTrue(state.updateFailed)
    assertFalse(state.saving)
    assertTrue(state.canApprove)
    assertEquals(CompletionStatus.PENDING, stored().status)

    viewModel.clearUpdateError()

    assertFalse(state.updateFailed)
  }

  @Test
  fun aProofReviewedElsewhereIsReviewedAndCantBeAnswered() = test {
    sendProof(reserve())
    watch()

    completions.reject(id, "Not the right drink").getOrThrow()

    assertTrue(state.reviewed)
    assertFalse(state.canReject)

    viewModel.approve()

    assertEquals(CompletionStatus.REJECTED, stored().status)
  }

  @Test
  fun aProofThatCantBeFoundFailsUntilItShowsUp() = test {
    val reservationId = reserve()
    id = completionId(reservationId)
    watch()

    assertEquals(ReviewProofStatus.FAILED, state.status)

    sendProof(reservationId)

    assertEquals(ReviewProofStatus.LOADED, state.status)
  }

  @Test
  fun aProofOfAnUnknownReservationShowsNoNameQuestOrParty() = test {
    sendProof(RESERVATION_ID_UNKNOWN)
    watch()

    assertEquals(ReviewProofStatus.LOADED, state.status)
    assertNull(state.explorerName)
    assertNull(state.questTitle)
    assertNull(state.reward)
    assertEquals(0, state.partySize)
  }

  @Test
  fun aFailingReservationListenerStillShowsTheProof() = test {
    sendProof(reserve())
    readReservations =
        object : ReservationRepository by reservations {
          override fun observeForVenue(venueId: String): Flow<List<Reservation>> = flow {
            throw IOException("denied")
          }
        }
    watch()

    assertEquals(ReviewProofStatus.LOADED, state.status)
    assertEquals(TEXT_PROOF, state.proof)
    assertNull(state.explorerName)
    assertNull(state.questTitle)
    assertTrue(state.canApprove)
  }

  @Test
  fun aFailingPartyListenerStillShowsTheProof() = test {
    sendProof(reserve(partySize = 3))
    readParty =
        object : PartyRepository by party {
          override fun observeMembers(reservationId: String): Flow<List<PartyMember>> = flow {
            throw IOException("denied")
          }
        }
    watch()

    assertEquals(ReviewProofStatus.LOADED, state.status)
    assertEquals(0, state.partySize)
    assertEquals("Yusuf S.", state.explorerName)
  }

  @Test
  fun aFailingQuestListenerStillShowsTheProof() = test {
    sendProof(reserve())
    readQuests =
        object : QuestRepository by quests {
          override fun observeQuestsByVenue(venueId: String): Flow<List<Quest>> = flow {
            throw IOException("denied")
          }
        }
    watch()

    assertEquals(ReviewProofStatus.LOADED, state.status)
    assertNull(state.questTitle)
    assertNull(state.reward)
    assertEquals("Yusuf S.", state.explorerName)
  }

  @Test
  fun aFailingListenerFailsUntilRetried() = test {
    sendProof(reserve())
    var failing = true
    reviewed =
        object : CompletionRepository by completions {
          override fun observeForVenue(venueId: String): Flow<List<Completion>> =
              if (failing) flow { throw IOException("denied") }
              else completions.observeForVenue(venueId)
        }
    watch()

    assertEquals(ReviewProofStatus.FAILED, state.status)

    failing = false
    viewModel.retry()

    assertEquals(ReviewProofStatus.LOADED, state.status)
  }

  @Test
  fun retryingShowsLoadingUntilTheListenersAnswer() = test {
    sendProof(reserve())
    val gate = CompletableDeferred<Unit>()
    var listens = 0
    reviewed =
        object : CompletionRepository by completions {
          override fun observeForVenue(venueId: String): Flow<List<Completion>> {
            listens++
            return if (listens == 1) flow { throw IOException("denied") }
            else
                flow {
                  gate.await()
                  emitAll(completions.observeForVenue(venueId))
                }
          }
        }
    watch()
    assertEquals(ReviewProofStatus.FAILED, state.status)

    viewModel.retry()

    assertEquals(ReviewProofStatus.LOADING, state.status)

    // A second tap doesn't restart the attempt in progress
    viewModel.retry()

    assertEquals(2, listens)

    gate.complete(Unit)
    advanceUntilIdle()

    assertEquals(ReviewProofStatus.LOADED, state.status)
  }

  // E.g. the session ended while the proof was on screen: retrying can't help
  @Test
  fun noSignedInVenueFails() = test {
    sendProof(reserve())
    signedIn = null
    watch()

    assertEquals(ReviewProofStatus.FAILED, state.status)
  }

  private companion object {
    const val VENUE_ID = "cafe"
    const val EXPLORER_UID = "yusuf"
    const val RESERVATION_ID_UNKNOWN = "gone"
    const val NOW = 1_800_000_000_000L
    const val SENT_AT = NOW + 60_000L
    /** Longer than the ViewModel keeps its listeners once nobody watches. */
    const val STOPPED_LONG_ENOUGH_MS = 6_000L
    val TEXT_PROOF = Proof.Text("The fox hides behind the bar")
    val REWARD = Reward.FreeItem("Croissant", specifics = null)
    val PHOTO_BYTES = byteArrayOf(1, 2, 3)
    val PHOTO: Uri = Uri.parse("content://media/photos/1")
  }
}
