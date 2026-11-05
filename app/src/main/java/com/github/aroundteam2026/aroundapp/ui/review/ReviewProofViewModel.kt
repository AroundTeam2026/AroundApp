// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.review

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepositoryProvider
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
import com.github.aroundteam2026.aroundapp.model.reservation.Proof
import com.github.aroundteam2026.aroundapp.model.reservation.ProofPhotoRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepositoryProvider
import com.github.aroundteam2026.aroundapp.ui.dashboard.AnswerSaver
import com.github.aroundteam2026.aroundapp.ui.dashboard.savingAmong
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
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Holds the proof [completionId] that the signed-in venue reviews: who sent it, for which quest,
 * the proof itself, and the venue's answer.
 *
 * It follows the venue's completions for this one, as the repository has no listener for a single
 * completion, and reads the reservation it belongs to (the explorer's name), the reservation's
 * party, the quest (title, requirements and reward) and, for a photo, the photo through
 * [proofPhotoRepository]. Once the proof is no longer pending, here or on another phone, the state
 * is [ReviewProofUiState.reviewed]. A proof that can't be found, or a failing listener, shows
 * [ReviewProofStatus.FAILED] until [retry]; a photo that can't be downloaded shows
 * [ProofPhoto.Failed], and [retry] downloads it again.
 *
 * [approve] and [reject] save through an [AnswerSaver]: a double tap saves once, and a failure is
 * reported until [clearUpdateError].
 */
class ReviewProofViewModel(
    private val completionId: String,
    authRepository: AuthRepository,
    private val completionRepository: CompletionRepository,
    private val reservationRepository: ReservationRepository,
    private val partyRepository: PartyRepository,
    private val questRepository: QuestRepository,
    private val proofPhotoRepository: ProofPhotoRepository,
) : ViewModel() {
  /** Bumped by [retry]: each new value restarts the listeners and the photo's download. */
  private val retries = MutableStateFlow(0)

  /** Saves the venue's answer to the proof. */
  private val answers = AnswerSaver(viewModelScope, TAG)

  /** What the screen shows now; the screen observes it. */
  @OptIn(ExperimentalCoroutinesApi::class)
  val uiState: StateFlow<ReviewProofUiState> =
      combine(
              combine(authRepository.currentUserId, retries) { venueId, _ -> venueId }
                  .flatMapLatest { venueId ->
                    if (venueId == null) flowOf(ReviewProofUiState()) else listen(venueId)
                  },
              answers.state,
          ) { listened, answers ->
            val pending =
                if (listened.status == ReviewProofStatus.LOADED && !listened.reviewed) {
                  setOf(completionId)
                } else {
                  emptySet()
                }
            listened.copy(
                saving = answers.savingAmong(pending).isNotEmpty(),
                updateFailed = answers.failed,
            )
          }
          .stateIn(
              viewModelScope,
              SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
              ReviewProofUiState(),
          )

  /** Approves the proof, which gives the party the quest's reward. */
  fun approve() {
    if (!uiState.value.canAnswer) return
    answers.answer(completionId, "proof $completionId with an approval") {
      completionRepository.approve(completionId)
    }
  }

  /**
   * Rejects the proof for [reason], which the explorer sees. Does nothing for a blank reason, which
   * the repository would refuse.
   */
  fun reject(reason: String) {
    val trimmed = reason.trim()
    if (trimmed.isEmpty() || !uiState.value.canAnswer) return
    answers.answer(completionId, "proof $completionId with a rejection") {
      completionRepository.reject(completionId, trimmed)
    }
  }

  /** Clears [ReviewProofUiState.updateFailed], once the screen has shown it. */
  fun clearUpdateError() = answers.clearFailure()

  /** Listens again, and downloads the photo again, after a failure. */
  fun retry() {
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
   * from the venue [venueId]'s reservations and quests, the party, and the photo.
   */
  private fun detailsOf(venueId: String, reservationId: String, proof: Proof): Flow<Details> =
      combine(
          reservationRepository.observeForVenue(venueId).map { all ->
            all.firstOrNull { it.id == reservationId }
          },
          partyRepository.observeJoinedCounts(listOf(reservationId)),
          questRepository.observeQuestsByVenue(venueId),
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

  /**
   * The photo of [proof] once downloaded, after [ProofPhoto.Loading]; null for a written answer.
   */
  private fun photoOf(proof: Proof): Flow<ProofPhoto?> =
      when (proof) {
        is Proof.Text -> flowOf(null)
        is Proof.Photo ->
            flow<ProofPhoto?> {
              emit(ProofPhoto.Loading)
              emit(
                  proofPhotoRepository
                      .download(proof.location)
                      .fold<ProofPhoto, ByteArray>(
                          { ProofPhoto.Loaded(it) },
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
          reviewed = completion.status != CompletionStatus.PENDING,
      )

  /** What was read about the proof of the reservation [reservationId], besides the proof itself. */
  private data class Details(
      val reservationId: String,
      val reservation: Reservation?,
      val partySize: Int,
      val quest: Quest?,
      val photo: ProofPhoto?,
  )

  companion object {
    private const val TAG = "ReviewProofViewModel"

    /** How long the listeners outlive the screen, so a quick rotation doesn't restart them. */
    private const val STOP_TIMEOUT_MS = 5_000L

    /**
     * Builds the [ReviewProofViewModel] of the proof [completionId] with the app's repositories,
     * and [proofPhotoRepository] for the photo: the app has no Cloud Storage one to provide yet.
     */
    fun factory(
        completionId: String,
        proofPhotoRepository: ProofPhotoRepository,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        ReviewProofViewModel(
            completionId,
            AuthRepositoryProvider.repository,
            CompletionRepositoryProvider.repository,
            ReservationRepositoryProvider.repository,
            PartyRepositoryProvider.repository,
            QuestRepositoryProvider.repository,
            proofPhotoRepository,
        )
      }
    }
  }
}
