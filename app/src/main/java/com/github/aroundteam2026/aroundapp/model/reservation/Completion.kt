// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import com.github.aroundteam2026.aroundapp.model.quest.ProofType

/**
 * An explorer's proof that they completed a quest, which the venue approves or rejects. The quest
 * is the one the reservation [reservationId] names, so it isn't repeated here.
 *
 * @property id Document id, assigned by the repository.
 * @property reservationId Id of the approved reservation this proof belongs to.
 * @property venueId Id of the venue that owns the quest; the venue's rules check it.
 * @property explorerUid Uid of the explorer who submitted the proof.
 * @property proof What the explorer submitted, of the quest's [ProofType].
 * @property status Lifecycle state; see [canTransition] for the allowed changes.
 * @property rejectReason Why the venue rejected the proof; required when [status] is
 *   [CompletionStatus.REJECTED], and null otherwise.
 * @property submittedAt Submission time, in epoch milliseconds.
 * @throws IllegalArgumentException if [status] is [CompletionStatus.REJECTED] without a reason, or
 *   if a reason is given for any other status.
 */
data class Completion(
    val id: String,
    val reservationId: String,
    val venueId: String,
    val explorerUid: String,
    val proof: Proof,
    val status: CompletionStatus,
    val rejectReason: String?,
    val submittedAt: Long,
) {
  init {
    require(status != CompletionStatus.REJECTED || !rejectReason.isNullOrBlank()) {
      "A rejected completion needs a reason"
    }
    require(status == CompletionStatus.REJECTED || rejectReason == null) {
      "Only a rejected completion has a reason"
    }
  }
}

/** What an explorer submits to complete a quest: a photo or a written answer, never both. */
sealed interface Proof {
  /** The kind of proof this is, which must be the quest's. */
  val type: ProofType

  /**
   * A photo taken at the venue.
   *
   * @property url Cloud Storage URL of the uploaded photo.
   * @throws IllegalArgumentException if [url] is blank.
   */
  data class Photo(val url: String) : Proof {
    override val type: ProofType
      get() = ProofType.PHOTO

    init {
      require(url.isNotBlank()) { "A photo proof needs the photo's URL" }
    }
  }

  /**
   * A written answer.
   *
   * @property answer What the explorer wrote.
   * @throws IllegalArgumentException if [answer] is blank.
   */
  data class Text(val answer: String) : Proof {
    override val type: ProofType
      get() = ProofType.TEXT

    init {
      require(answer.isNotBlank()) { "A text proof needs an answer" }
    }
  }
}

/** Lifecycle state of a completion. */
enum class CompletionStatus {
  /** Submitted by the explorer; waiting for the venue's answer. */
  PENDING,
  /** Accepted by the venue. Final. */
  APPROVED,
  /** Declined by the venue. Final. */
  REJECTED,
}

/** Whether a completion may change from [from] to [to]: only pending ones can be answered. */
fun canTransition(from: CompletionStatus, to: CompletionStatus): Boolean =
    from == CompletionStatus.PENDING &&
        (to == CompletionStatus.APPROVED || to == CompletionStatus.REJECTED)
