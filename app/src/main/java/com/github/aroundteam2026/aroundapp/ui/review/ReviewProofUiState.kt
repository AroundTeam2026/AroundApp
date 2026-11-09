// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.review

import androidx.compose.ui.graphics.ImageBitmap
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.reservation.Proof

/**
 * What the Review proof screen shows: one proof sent to the venue, with who sent it, for which
 * quest, and whether the venue's answer is being saved.
 *
 * @property status Whether the proof is still loading, shown, or couldn't be loaded.
 * @property explorerName Display name of the explorer who made the reservation the proof belongs
 *   to, or null if unknown. Only that explorer's name is stored, so a party member's proof shows it
 *   too.
 * @property partySize How many explorers the reservation's party has; 0 if it can't be read.
 * @property sentAt When the proof was sent, in epoch milliseconds.
 * @property questTitle Title of the quest, or null if the venue deleted it, or the quest or the
 *   reservation can't be read.
 * @property requirements What the quest asks the explorers to do, or null like [questTitle].
 * @property reward What approving gives the party, or null if the quest has no reward or is
 *   unknown.
 * @property proof What the explorer sent: a written answer, or a photo shown from [photo].
 * @property photo The photo of a [Proof.Photo], downloaded for the screen; null for a written
 *   answer.
 * @property reviewed Whether the proof was approved or rejected, here or on another phone: the
 *   screen then goes back to the dashboard.
 * @property saving Whether an approval or a rejection is being saved: both buttons are disabled
 *   meanwhile, so the venue can't answer twice.
 * @property updateFailed Whether saving the last answer failed: the screen shows it once, then
 *   calls [ReviewProofViewModel.clearUpdateError].
 */
data class ReviewProofUiState(
    val status: ReviewProofStatus = ReviewProofStatus.LOADING,
    val explorerName: String? = null,
    val partySize: Int = 0,
    val sentAt: Long = 0L,
    val questTitle: String? = null,
    val requirements: String? = null,
    val reward: Reward? = null,
    val proof: Proof? = null,
    val photo: ProofPhoto? = null,
    val reviewed: Boolean = false,
    val saving: Boolean = false,
    val updateFailed: Boolean = false,
) {
  /** Whether Reject can be tapped: the proof is shown, pending, and not saving. */
  val canReject: Boolean
    get() = status == ReviewProofStatus.LOADED && !reviewed && !saving

  /**
   * Whether Approve can be tapped: as [canReject], and a photo proof's photo is shown. An approval
   * is final and gives the reward, so the venue must have seen what it approves.
   */
  val canApprove: Boolean
    get() = canReject && (proof !is Proof.Photo || photo is ProofPhoto.Loaded)
}

/** Where the Review proof screen stands. */
enum class ReviewProofStatus {
  /** The proof is being read. */
  LOADING,
  /** The proof is shown. */
  LOADED,
  /** The proof couldn't be found or read; the screen offers to try again. */
  FAILED,
}

/** The photo of a photo proof, as the screen shows it. */
sealed interface ProofPhoto {
  /** The photo is being downloaded. */
  data object Loading : ProofPhoto

  /**
   * The photo was downloaded and decoded, so the screen can show it.
   *
   * Compared by identity, not by content: a new download is a new photo for the screen, and
   * comparing the pixels on every state change would be wasted work.
   *
   * @property image The decoded photo.
   */
  class Loaded(val image: ImageBitmap) : ProofPhoto

  /**
   * The photo couldn't be downloaded or isn't an image; [ReviewProofViewModel.retry] tries again.
   */
  data object Failed : ProofPhoto
}
