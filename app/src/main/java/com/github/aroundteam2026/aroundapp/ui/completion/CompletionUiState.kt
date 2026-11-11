// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.net.Uri
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.reservation.MAX_TEXT_ANSWER_LENGTH
import com.github.aroundteam2026.aroundapp.ui.SaveState

/**
 * What the completion screen shows: the quest and the proof it asks for, what the explorer has
 * filled in so far, and where submitting stands.
 *
 * @property status Whether the quest is loading, ready to complete, or why it can't be.
 * @property questTitle The quest's title, shown above the form; empty until [status] is
 *   [CompletionFormStatus.READY].
 * @property proofType The kind of proof the quest asks for; null until it is known.
 * @property answer The written answer typed so far, for a [ProofType.TEXT] quest.
 * @property photo The photo picked so far, for a [ProofType.PHOTO] quest, or null when none is.
 * @property saveState Where submitting stands: [SaveState.Saved] once the proof is submitted, also
 *   when it was submitted earlier.
 * @property locationError Why the last location check blocked submission, or null otherwise.
 * @property sentByTeammate Whether another member of the party sent its proof: the form is locked,
 *   and [saveState] doesn't become [SaveState.Saved], which is kept for a proof this explorer sent.
 */
data class CompletionUiState(
    val status: CompletionFormStatus = CompletionFormStatus.LOADING,
    val questTitle: String = "",
    val proofType: ProofType? = null,
    val answer: String = "",
    val photo: Uri? = null,
    val saveState: SaveState = SaveState.Idle,
    val requirements: String = "",
    val locationError: CompletionLocationError? = null,
    val sentByTeammate: Boolean = false,
) {
  /**
   * Whether the form can still be changed: the quest is ready, nothing is saving or saved, and no
   * teammate sent the party's proof.
   */
  val isEditable: Boolean
    get() =
        status == CompletionFormStatus.READY &&
            !sentByTeammate &&
            (saveState == SaveState.Idle || saveState == SaveState.Failed)

  /** Whether the answer exceeds the stored-answer limit after trimming, as on submission. */
  val answerTooLong: Boolean
    get() = answer.trim().length > MAX_TEXT_ANSWER_LENGTH

  /** Whether the proof the quest asks for is filled in and the form can be submitted. */
  val canSubmit: Boolean
    get() =
        isEditable &&
            when (proofType) {
              ProofType.TEXT -> answer.isNotBlank() && !answerTooLong
              ProofType.PHOTO -> photo != null
              null -> false
            }
}

/** Why a completion could not be submitted from the explorer's current location. */
enum class CompletionLocationError {
  /** The explorer is outside the quest's unlock radius. */
  TOO_FAR,
  /** A precise device or venue location could not be obtained. */
  UNAVAILABLE,
}

/** Where the completion screen stands. */
enum class CompletionFormStatus {
  /** The reservation and its quest are being fetched. */
  LOADING,
  /** The reservation is approved and the form can be filled in. */
  READY,
  /** There is nothing to complete: the reservation isn't approved (any more), or isn't there. */
  UNAVAILABLE,
  /** The reservation or its quest could not be fetched; trying again may work. */
  FAILED,
}
