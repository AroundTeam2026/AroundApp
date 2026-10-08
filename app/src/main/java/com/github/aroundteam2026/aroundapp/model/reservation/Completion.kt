// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

/**
 * An explorer's proof that they completed a quest, which the venue approves or rejects.
 *
 * @property id Document id, assigned by the repository.
 * @property reservationId Id of the approved reservation this proof belongs to.
 * @property questId Id of the quest that was completed.
 * @property venueId Id of the venue that owns the quest; the venue's rules check it.
 * @property explorerUid Uid of the explorer who submitted the proof.
 * @property proofUrl Cloud Storage URL of the submitted proof.
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
    val questId: String,
    val venueId: String,
    val explorerUid: String,
    val proofUrl: String,
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
