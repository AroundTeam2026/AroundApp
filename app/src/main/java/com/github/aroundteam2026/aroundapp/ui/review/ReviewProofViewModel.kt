// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.review

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.connectivity.ConnectivityRepository
import com.github.aroundteam2026.aroundapp.model.connectivity.ConnectivityRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.party.PartyRepository
import com.github.aroundteam2026.aroundapp.model.party.PartyRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.party.observeJoinedCounts
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.Completion
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionStatus
import com.github.aroundteam2026.aroundapp.model.reservation.MAX_REJECT_REASON_LENGTH
import com.github.aroundteam2026.aroundapp.model.reservation.Proof
import com.github.aroundteam2026.aroundapp.model.reservation.ProofPhotoRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ProofPhotoRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepositoryProvider
import com.github.aroundteam2026.aroundapp.ui.dashboard.AnswerSaver
import com.github.aroundteam2026.aroundapp.ui.dashboard.savingAmong
import com.github.aroundteam2026.aroundapp.ui.observeOffline
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the proof [completionId] that the signed-in venue reviews: who sent it, for which quest,
 * the proof itself, and the venue's answer.
 *
 * It follows the venue's completions for this one, as the repository has no listener for a single
 * completion, and reads the reservation it belongs to (the explorer's name), the reservation's
 * party, the quest (title, requirements and reward) and, for a photo, the photo through
 * [proofPhotoRepository], decoded by [decode]. Once the proof is no longer pending, the state is
 * [ReviewProofUiState.reviewed], with the reason of a rejection; once it is answered while shown,
 * here or on another phone, the state is also [ReviewProofUiState.answered]. A proof the venue
 * already rejected opens with only [approve] left, for a venue that changes its mind. A proof that
 * can't be found, or its listener failing, shows [ReviewProofStatus.FAILED] until [retry]. The
 * reservation, party or quest failing only leaves out what they tell, and a photo that can't be
 * downloaded or decoded shows [ProofPhoto.Failed] until [retry].
 *
 * [approve] and [reject] save through an [AnswerSaver]: a double tap saves once, and a failure is
 * reported until [clearUpdateError].
 *
 * Approving and rejecting are Firestore transactions, which fail offline: while
 * [connectivityRepository] says the device is offline, [ReviewProofUiState.offline] is set and
 * [approve] and [reject] do nothing.
 */
class ReviewProofViewModel(
    private val completionId: String,
    authRepository: AuthRepository,
    private val completionRepository: CompletionRepository,
    private val reservationRepository: ReservationRepository,
    private val partyRepository: PartyRepository,
    private val questRepository: QuestRepository,
    private val proofPhotoRepository: ProofPhotoRepository,
    connectivityRepository: ConnectivityRepository,
    private val decode: suspend (ByteArray) -> ImageBitmap? = { decodePhoto(it) },
) : ViewModel() {
  /** Bumped by [retry]: each new value restarts the listeners and the photo's download. */
  private val retries = MutableStateFlow(0)

  /** Whether [retry] restarted the listeners after a failure and they haven't answered yet. */
  private val reloading = MutableStateFlow(false)

  /**
   * The photo once downloaded, with its location, so that the listeners restarting, e.g. after the
   * app was in the background, don't download it again.
   */
  private var downloaded: Pair<String, ProofPhoto.Loaded>? = null

  /** Saves the venue's answer to the proof. */
  private val answers = AnswerSaver(viewModelScope, TAG)

  /** Whether the venue's answer is being written, until the repository returns. */
  private val writing = MutableStateFlow(false)

  /**
   * Where the proof stood when last shown, and whether it was answered while shown. Held here, not
   * in the flow, so it outlives the listeners stopping while the screen is in the background.
   */
  private val shown = MutableStateFlow(Shown())

  /**
   * Whether the device is offline, followed for as long as the ViewModel lives rather than only
   * while the screen watches [uiState], so it is current when the screen comes back.
   */
  private val offline = MutableStateFlow(false)

  init {
    viewModelScope.launch {
      connectivityRepository.observeOffline(TAG).collect { offline.value = it }
    }
  }

  /** What the screen shows now; the screen observes it. */
  @OptIn(ExperimentalCoroutinesApi::class)
  val uiState: StateFlow<ReviewProofUiState> =
      combine(
              combine(authRepository.currentUserId, retries) { venueId, _ -> venueId }
                  .flatMapLatest { venueId ->
                    // Signed out, e.g. the session ended: there is no venue to read the proof as
                    if (venueId == null)
                        flowOf(ReviewProofUiState(status = ReviewProofStatus.FAILED))
                    else listen(venueId)
                  }
                  .onEach { reloading.value = false }
                  .withAnswered(),
              answers.state,
              reloading,
              writing,
              offline,
          ) { listened, answers, reloading, writing, offline ->
            // Reloading keeps answered, as the screen is leaving once it is
            val shown =
                if (reloading) ReviewProofUiState(answered = listened.answered) else listened
            val answerable =
                if (shown.status == ReviewProofStatus.LOADED && shown.answerable)
                    setOf(completionId)
                else emptySet()
            // While the answer is being written, saving even if the proof fails to load meanwhile:
            // the screen keeps Back disabled, as leaving would cancel the write
            shown.copy(
                saving = writing || answers.savingAmong(answerable).isNotEmpty(),
                updateFailed = answers.failed,
                offline = offline,
            )
          }
          .stateIn(
              viewModelScope,
              SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
              ReviewProofUiState(),
          )

  /** Approves the proof, which gives the party the quest's reward. Does nothing offline. */
  fun approve() {
    // The state can be stale while the screen resumes, so offline is read where it is followed
    if (offline.value || !uiState.value.canApprove) return
    answers.answer(completionId, "proof $completionId with an approval") {
      whileWriting { completionRepository.approve(completionId) }
    }
  }

  /**
   * Rejects the proof for [reason], which the explorer sees. Does nothing for a blank reason, or
   * one over [MAX_REJECT_REASON_LENGTH] characters, which the repository would refuse, and offline.
   */
  fun reject(reason: String) {
    val trimmed = reason.trim()
    if (
        trimmed.isEmpty() ||
            trimmed.length > MAX_REJECT_REASON_LENGTH ||
            offline.value ||
            !uiState.value.canReject
    ) {
      return
    }
    answers.answer(completionId, "proof $completionId with a rejection") {
      whileWriting { completionRepository.reject(completionId, trimmed) }
    }
  }

  /** Runs [write], marking the answer as being written until it returns. */
  private suspend fun whileWriting(write: suspend () -> Result<Unit>): Result<Unit> {
    writing.value = true
    try {
      return write()
    } finally {
      writing.value = false
    }
  }

  /** Clears [ReviewProofUiState.updateFailed], once the screen has shown it. */
  fun clearUpdateError() = answers.clearFailure()

  /**
   * Listens again, and downloads the photo again, after a failure; does nothing otherwise. After a
   * failed proof, the state is [ReviewProofStatus.LOADING] until the listeners answer.
   */
  fun retry() {
    val current = uiState.value
    when {
      current.status == ReviewProofStatus.FAILED -> reloading.value = true
      current.photo != ProofPhoto.Failed -> return
    }
    retries.update { it + 1 }
  }

  /**
   * The proof [completionId] of the venue [venueId], with what the screen shows about it. A failing
   * listener gives a failed state.
   *
   * The reservation, its party, the quest and the photo are read again only if the proof's
   * reservation or photo change, not when its status does.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun listen(venueId: String): Flow<ReviewProofUiState> = channelFlow {
    // One listener, shared by the state and the details to read
    val completion =
        completionRepository
            .observeForVenue(venueId)
            .map { all -> all.firstOrNull { it.id == completionId } }
            .shareIn(this, SharingStarted.Eagerly, replay = 1)
    val details =
        completion
            .map { it?.let { found -> found.reservationId to found.proof } }
            .distinctUntilChanged()
            .flatMapLatest { key ->
              if (key == null) flowOf(null) else detailsOf(venueId, key.first, key.second)
            }
    combine(completion, details) { found, read ->
          when {
            found == null -> ReviewProofUiState(status = ReviewProofStatus.FAILED)
            // Still reading the details of this proof
            read == null || read.reservationId != found.reservationId -> null
            else -> stateOf(found, read)
          }
        }
        .filterNotNull()
        .collect { send(it) }
  }
      // The repositories' flows throw if the backend stops a listener
      .catch {
        Log.w(TAG, "Listening to the proof $completionId failed", it)
        emit(ReviewProofUiState(status = ReviewProofStatus.FAILED))
      }

  /**
   * What the screen shows about a proof of the reservation [reservationId] that is [proof], read
   * from the venue [venueId]'s reservations and quests, the party, and the photo. The proof is fine
   * when one of them fails, so the screen still shows it, without what that one tells.
   */
  private fun detailsOf(venueId: String, reservationId: String, proof: Proof): Flow<Details> =
      combine(
          reservationRepository
              .observeForVenue(venueId)
              .map { all -> all.firstOrNull { it.id == reservationId } }
              .orOnFailure(null, "the reservation $reservationId"),
          partyRepository
              .observeJoinedCounts(listOf(reservationId))
              .orOnFailure(emptyMap(), "the party of $reservationId"),
          questRepository.observeQuestsByVenue(venueId).orOnFailure(emptyList(), "the quests"),
          photoOf(proof),
      ) { reservation, partySizes, quests, photo ->
        Details(
            reservationId = reservationId,
            reservation = reservation,
            partySize = partySizes[reservationId] ?: 0,
            quest = reservation?.let { found -> quests.firstOrNull { it.id == found.questId } },
            photo = photo,
        )
      }

  /** This flow, then [fallback] if it fails, e.g. when the backend stops [what]'s listener. */
  private fun <T> Flow<T>.orOnFailure(fallback: T, what: String): Flow<T> = catch {
    Log.w(TAG, "Listening to $what of the proof $completionId failed", it)
    emit(fallback)
  }

  /**
   * The photo of [proof] once downloaded, after [ProofPhoto.Loading]; null for a written answer.
   */
  private fun photoOf(proof: Proof): Flow<ProofPhoto?> =
      when (proof) {
        is Proof.Text -> flowOf(null)
        is Proof.Photo ->
            flow<ProofPhoto?> {
              val kept = downloaded?.takeIf { (location, _) -> location == proof.location }
              if (kept != null) {
                emit(kept.second)
                return@flow
              }
              emit(ProofPhoto.Loading)
              emit(
                  proofPhotoRepository
                      .download(proof.location)
                      .fold<ProofPhoto, ByteArray>(
                          { bytes ->
                            // A photo that can't be shown can't be approved, so it fails like a
                            // download and the screen offers to try again
                            decode(bytes)?.let { image ->
                              ProofPhoto.Loaded(image).also { downloaded = proof.location to it }
                            }
                                ?: ProofPhoto.Failed.also {
                                  Log.w(TAG, "The photo of $completionId isn't an image")
                                }
                          },
                          {
                            Log.w(TAG, "Downloading the photo of $completionId failed", it)
                            ProofPhoto.Failed
                          },
                      )
              )
            }
      }

  /** What the screen shows of [completion], with what was [read] about it. */
  private fun stateOf(completion: Completion, read: Details) =
      ReviewProofUiState(
          status = ReviewProofStatus.LOADED,
          explorerName = read.reservation?.explorerName?.ifBlank { null },
          partySize = read.partySize,
          sentAt = completion.submittedAt,
          questTitle = read.quest?.title,
          requirements = read.quest?.requirements,
          reward = read.quest?.reward,
          proof = completion.proof,
          photo = read.photo,
          proofStatus = completion.status,
          rejectReason = completion.rejectReason,
      )

  /** What was read about the proof of the reservation [reservationId], besides the proof itself. */
  private data class Details(
      val reservationId: String,
      val reservation: Reservation?,
      val partySize: Int,
      val quest: Quest?,
      val photo: ProofPhoto?,
  )

  /**
   * Where the proof stood when last shown, null until it is shown, and whether it was answered
   * while shown, which stays so, as the screen is leaving.
   */
  private data class Shown(val status: CompletionStatus? = null, val answered: Boolean = false) {
    /**
     * This, once [state] is emitted: a shown proof whose status changes to an answer is answered.
     */
    fun next(state: ReviewProofUiState): Shown {
      if (state.status != ReviewProofStatus.LOADED) return this
      val status = state.proofStatus
      val changed = this.status != null && status != this.status
      return Shown(status, answered || (changed && status != CompletionStatus.PENDING))
    }
  }

  /**
   * These states, each [ReviewProofUiState.answered] once the proof was answered while shown, here
   * or on another phone, even while the listeners were stopped. A proof opened once rejected, to
   * look at it, isn't answered until its status changes.
   */
  private fun Flow<ReviewProofUiState>.withAnswered() = map { state ->
    state.copy(answered = shown.updateAndGet { it.next(state) }.answered)
  }

  companion object {
    private const val TAG = "ReviewProofViewModel"

    /** How long the listeners outlive the screen, so a quick rotation doesn't restart them. */
    private const val STOP_TIMEOUT_MS = 5_000L

    /**
     * Builds the [ReviewProofViewModel] of the proof [completionId] with the app's repositories;
     * [photos] builds the photo repository from the app's context, so a test can swap it.
     */
    fun factory(
        completionId: String,
        photos: (Context) -> ProofPhotoRepository = ProofPhotoRepositoryProvider::repository,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val application = this[APPLICATION_KEY]!!
        ReviewProofViewModel(
            completionId,
            AuthRepositoryProvider.repository,
            CompletionRepositoryProvider.repository,
            ReservationRepositoryProvider.repository,
            PartyRepositoryProvider.repository,
            QuestRepositoryProvider.repository,
            photos(application),
            ConnectivityRepositoryProvider.repository(application),
        )
      }
    }
  }
}

/**
 * [bytes] as an image, decoded on [dispatcher], off the main thread, or null if they aren't one.
 */
internal suspend fun decodePhoto(
    bytes: ByteArray,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): ImageBitmap? =
    withContext(dispatcher) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
