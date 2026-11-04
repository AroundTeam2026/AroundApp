// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.reservation.CompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Proof
import com.github.aroundteam2026.aroundapp.model.reservation.ProofPhotoRepository
import com.github.aroundteam2026.aroundapp.model.reservation.completionId
import com.github.aroundteam2026.aroundapp.ui.SaveState
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewRow
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewRowStatus
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewStatus
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the completion of the reservation [reservationId] by the explorer [explorerUid]: its quest
 * and the proof the quest asks for, what the explorer filled in, and submitting it.
 *
 * The quest comes from the overview, which the screen is opened from and which already loads each
 * approved reservation with its quest: the form follows [overview], and is
 * [CompletionFormStatus.READY] only while the overview shows the reservation as approved. If the
 * overview fails, so does the form, and [onRetry] calls [retryOverview]. What the explorer filled
 * in is kept through all of it.
 *
 * It also listens to the reservation's completions, so a reservation the explorer already completed
 * shows as [SaveState.Saved] and can't be submitted again, also when the answer to an earlier
 * submit was lost. If that listener fails, the form stays usable: the repository refuses a second
 * completion anyway.
 *
 * A text proof is submitted as it is. A photo is uploaded through [proofPhotoRepository] first; a
 * photo that was uploaded is not uploaded again when only the submit failed, until another photo is
 * picked.
 *
 * @param overview The overview's state, e.g. the `OverviewViewModel`'s `uiState` of the Quests tab.
 * @param retryOverview Asks the overview to load again after it failed, e.g.
 *   `OverviewViewModel.retry`.
 */
class CompletionViewModel(
    private val reservationId: String,
    private val explorerUid: String,
    private val overview: StateFlow<OverviewUiState>,
    private val retryOverview: () -> Unit,
    private val completionRepository: CompletionRepository,
    private val proofPhotoRepository: ProofPhotoRepository,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow(CompletionUiState())
  /** What the completion screen shows now; the screen observes it. */
  val uiState: StateFlow<CompletionUiState> = mutableUiState.asStateFlow()

  /** The venue of the reservation, while the overview shows it as approved. */
  private var venueId: String? = null

  /** The photo that was uploaded last, with the Storage location it was stored at. */
  private var uploaded: Pair<Uri, String>? = null

  init {
    viewModelScope.launch { overview.collect { showing(it) } }
    viewModelScope.launch { listenToCompletions() }
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
   * again retries.
   */
  fun submit() {
    val form = mutableUiState.value
    val venueId = venueId
    if (!form.canSubmit || venueId == null) return
    mutableUiState.update { it.copy(saveState = SaveState.Saving) }
    viewModelScope.launch {
      val saveState =
          proofOf(form)
              ?.let { completionRepository.submit(reservationId, venueId, explorerUid, it) }
              ?.fold(
                  { SaveState.Saved(it) },
                  {
                    Log.w(TAG, "Submitting the completion of $reservationId failed", it)
                    SaveState.Failed
                  },
              ) ?: SaveState.Failed
      // The listener may already have shown the completion as saved, which this agrees with
      mutableUiState.update { it.copy(saveState = saveState) }
    }
  }

  /** Called when the explorer asks to try again after a failure to load; does nothing otherwise. */
  fun onRetry() {
    if (mutableUiState.value.status == CompletionFormStatus.FAILED) retryOverview()
  }

  /** Shows what [overviewState] says about the reservation, keeping what the explorer filled in. */
  private fun showing(overviewState: OverviewUiState) {
    val row = overviewState.rows.find { it.reservationId == reservationId }?.takeIf { it.approved }
    venueId = row?.venueId
    mutableUiState.update {
      when {
        overviewState.status == OverviewStatus.LOADING ->
            it.copy(status = CompletionFormStatus.LOADING)
        overviewState.status == OverviewStatus.FAILED ->
            it.copy(status = CompletionFormStatus.FAILED)
        row == null -> it.copy(status = CompletionFormStatus.UNAVAILABLE)
        else ->
            it.copy(
                status = CompletionFormStatus.READY,
                questTitle = row.questTitle,
                proofType = row.proofType,
            )
      }
    }
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
   * Shows the reservation as completed once the explorer has a completion of it, whatever its
   * status. It listens to the completions of this reservation, the party's included, and looks for
   * the explorer's own, so what it reads doesn't grow with everything the explorer ever completed.
   */
  private suspend fun listenToCompletions() {
    val id = completionId(reservationId, explorerUid)
    completionRepository
        .observeForReservation(reservationId)
        .catch { Log.w(TAG, "Listening to the completions of $reservationId failed", it) }
        .collect { completions ->
          if (completions.any { it.id == id }) {
            mutableUiState.update { it.copy(saveState = SaveState.Saved(id)) }
          }
        }
  }

  private companion object {
    const val TAG = "CompletionViewModel"
  }
}

/**
 * Whether the venue approved the reservation this row shows, the status a quest can be completed
 * in.
 */
private val OverviewRow.approved: Boolean
  get() = status == OverviewRowStatus.APPROVED
