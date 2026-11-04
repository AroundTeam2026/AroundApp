// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.aroundteam2026.aroundapp.model.common.distanceTo
import com.github.aroundteam2026.aroundapp.model.location.LocationRepository
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Proof
import com.github.aroundteam2026.aroundapp.model.reservation.ProofPhotoRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.completionId
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.ui.SaveState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the completion of the reservation [reservationId] by the explorer [explorerUid]: its quest
 * and the proof the quest asks for, what the explorer filled in, and submitting it.
 *
 * It listens to the explorer's reservations, so the form is [CompletionFormStatus.READY] only while
 * the reservation is approved, and to the completions of the reservation, the party's included, for
 * the explorer's own, so a reservation that was already completed shows as [SaveState.Saved] and
 * can't be submitted again, also when the answer to an earlier submit was lost. If listening to the
 * completions fails, the form stays usable: the repository refuses a second completion anyway. If
 * loading the reservation fails, the form is [CompletionFormStatus.FAILED] until [onRetry].
 *
 * A text proof is submitted as it is. A photo is uploaded through [proofPhotoRepository] first; a
 * photo that was uploaded is not uploaded again when only the submit failed, until another photo is
 * picked.
 */
class CompletionViewModel(
    private val reservationId: String,
    private val explorerUid: String,
    private val reservationRepository: ReservationRepository,
    private val questRepository: QuestRepository,
    private val venueRepository: VenueRepository,
    private val locationRepository: LocationRepository,
    private val completionRepository: CompletionRepository,
    private val proofPhotoRepository: ProofPhotoRepository,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow(CompletionUiState())
  /** What the completion screen shows now; the screen observes it. */
  val uiState: StateFlow<CompletionUiState> = mutableUiState.asStateFlow()

  private var listening: Job? = null

  /** The venue of the reservation, once it is known to be approved. */
  private var venueId: String? = null
  private var questRadiusMeters: Int? = null

  /** The photo that was uploaded last, with the Storage location it was stored at. */
  private var uploaded: Pair<Uri, String>? = null

  init {
    listen()
  }

  /** Called when the explorer types [answer] for a text proof; ignored once the form is locked. */
  fun updateAnswer(answer: String) {
    mutableUiState.update { if (it.isEditable) it.copy(answer = answer) else it }
  }

  /** Called when the explorer takes or picks [photo]; ignored once the form is locked. */
  fun pickPhoto(photo: Uri) {
    mutableUiState.update { if (it.isEditable) it.copy(photo = photo) else it }
  }

  /**
   * Called when the explorer submits the proof. Does nothing unless the proof is filled in and the
   * form is not already saving or saved, so a double tap submits once. A failure, at the upload or
   * at the submit, keeps what the explorer filled in and shows [SaveState.Failed]; calling this
   * again retries. A precise location is checked before any photo upload or completion write.
   */
  fun submit() {
    val form = mutableUiState.value
    val venueId = venueId
    val questRadius = questRadiusMeters
    if (!form.canSubmit || venueId == null || questRadius == null) return
    mutableUiState.update { it.copy(saveState = SaveState.Saving, locationError = null) }
    viewModelScope.launch {
      val locationError = locationError(venueId, questRadius)
      if (locationError != null) {
        mutableUiState.update {
          if (it.saveState is SaveState.Saved) it
          else it.copy(saveState = SaveState.Idle, locationError = locationError)
        }
        return@launch
      }
      // Approval may be withdrawn while the device is finding its position.
      if (mutableUiState.value.saveState is SaveState.Saved) return@launch
      if (mutableUiState.value.status != CompletionFormStatus.READY) {
        mutableUiState.update {
          if (it.saveState is SaveState.Saved) it else it.copy(saveState = SaveState.Idle)
        }
        return@launch
      }
      val proof = proofOf(form)
      val saveState =
          proof
              ?.let { completionRepository.submit(reservationId, venueId, explorerUid, it) }
              ?.fold(
                  { SaveState.Saved(it) },
                  {
                    Log.w(TAG, "Submitting the completion of $reservationId failed", it)
                    SaveState.Failed
                  },
              ) ?: SaveState.Failed
      // The listener may have shown the completion as saved meanwhile, e.g. when the answer to an
      // earlier submit was lost: this submit then fails as already stored, and a retry could only
      // fail again, so what the listener showed stays
      mutableUiState.update {
        if (it.saveState is SaveState.Saved) it else it.copy(saveState = saveState)
      }
    }
  }

  /** Checks the venue's current marker and a fresh precise device fix on every submit attempt. */
  private suspend fun locationError(venueId: String, radiusMeters: Int): CompletionLocationError? =
      try {
        val venueLocation = venueRepository.getVenue(venueId)?.location
        val deviceLocation = venueLocation?.let { locationRepository.currentPreciseLocation() }
        when {
          venueLocation == null || deviceLocation == null -> CompletionLocationError.UNAVAILABLE
          deviceLocation.distanceTo(venueLocation) > radiusMeters -> CompletionLocationError.TOO_FAR
          else -> null
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "Checking location for $reservationId failed", e)
        CompletionLocationError.UNAVAILABLE
      }

  /** Called when the explorer asks to try again after a failure to load; does nothing otherwise. */
  fun onRetry() {
    if (mutableUiState.value.status != CompletionFormStatus.FAILED) return
    mutableUiState.update { it.copy(status = CompletionFormStatus.LOADING) }
    listen()
  }

  /**
   * The proof for [form], uploading its photo first; null if there is none or the upload failed.
   */
  private suspend fun proofOf(form: CompletionUiState): Proof? =
      when (form.proofType) {
        ProofType.TEXT -> Proof.Text(form.answer.trim())
        ProofType.PHOTO -> form.photo?.let { photoLocation(it) }?.let { Proof.Photo(it) }
        null -> null
      }

  /** Where [photo] is stored: where it was uploaded before, or else uploaded now. */
  private suspend fun photoLocation(photo: Uri): String? {
    uploaded
        ?.takeIf { (uploadedPhoto, _) -> uploadedPhoto == photo }
        ?.let { (_, location) ->
          return location
        }
    return proofPhotoRepository
        .upload(reservationId, photo)
        .onFailure { Log.w(TAG, "Uploading the proof photo of $reservationId failed", it) }
        .getOrNull()
        ?.also { uploaded = photo to it }
  }

  /**
   * Listens to the reservation and, apart from it, to the explorer's completions, until the
   * ViewModel is cleared or listening to the reservation fails. The quest doesn't change, so it is
   * read once per listening.
   */
  private fun listen() {
    listening?.cancel()
    val quests = mutableMapOf<String, Quest?>()
    listening = viewModelScope.launch {
      launch { listenToCompletions() }
      reservationRepository
          .observeForExplorer(explorerUid)
          .map { reservations ->
            val approved = reservations.approvedReservation(reservationId)
            approved?.let { reservation ->
              if (reservation.questId !in quests) {
                quests[reservation.questId] = questRepository.getQuest(reservation.questId)
              }
              quests[reservation.questId]?.let { quest -> reservation to quest }
            }
          }
          .catch {
            Log.w(TAG, "Loading the reservation $reservationId failed", it)
            mutableUiState.update { state -> state.copy(status = CompletionFormStatus.FAILED) }
          }
          .collect { booked ->
            venueId = booked?.first?.venueId
            questRadiusMeters = booked?.second?.radiusMeters
            mutableUiState.update { state ->
              if (booked == null) {
                state.copy(status = CompletionFormStatus.UNAVAILABLE)
              } else {
                val (_, quest) = booked
                state.copy(
                    status = CompletionFormStatus.READY,
                    questTitle = quest.title,
                    proofType = quest.proofType,
                )
              }
            }
          }
    }
  }

  /**
   * Shows the reservation as completed once the explorer has a completion of it, whatever its
   * status.
   */
  private suspend fun listenToCompletions() {
    val id = completionId(reservationId, explorerUid)
    try {
      completionRepository.observeForReservation(reservationId).collect { completions ->
        if (completions.any { it.id == id }) {
          mutableUiState.update { it.copy(saveState = SaveState.Saved(id)) }
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Listening to the completions of $reservationId failed", e)
    }
  }

  private companion object {
    const val TAG = "CompletionViewModel"
  }
}

/**
 * The reservation [id] among these, if it is approved, the only status a quest can be completed in.
 */
private fun List<Reservation>.approvedReservation(id: String): Reservation? = find {
  it.id == id
}
    ?.takeIf { it.status == ReservationStatus.APPROVED }
