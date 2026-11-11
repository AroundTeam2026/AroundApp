// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import com.github.aroundteam2026.aroundapp.model.quest.ProofType

/**
 * A party's proof that it completed a quest, which the venue approves or rejects. One member sends
 * it for the whole party, and every member reads it and its result. The quest is the one the
 * reservation [reservationId] names, so it isn't repeated here.
 *
 * @property id Document id, assigned by the repository: the reservation's, see [completionId].
 * @property reservationId Id of the approved reservation this proof belongs to.
 * @property venueId Id of the venue that owns the quest; the venue's rules check it.
 * @property explorerUid Uid of the party member who submitted the proof: the latest, if a new proof
 *   replaced an earlier one.
 * @property proof What the member submitted, of the quest's [ProofType].
 * @property status Lifecycle state; see [canTransition] for the allowed changes.
 * @property rejectReason Why the venue rejected the proof; required when [status] is
 *   [CompletionStatus.REJECTED], and null otherwise.
 * @property submittedAt Submission time of the latest proof, in epoch milliseconds.
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
   * @property location The photo's Cloud Storage location (`gs://...`), as returned by
   *   [ProofPhotoRepository.upload]. It is not a download URL, so an image loader can't open it:
   *   read the photo with [ProofPhotoRepository.download].
   * @throws IllegalArgumentException if [location] is blank or exceeds [MAX_PHOTO_LOCATION_LENGTH].
   */
  data class Photo(val location: String) : Proof {
    override val type: ProofType
      get() = ProofType.PHOTO

    init {
      require(location.isNotBlank()) { "A photo proof needs the photo's location" }
      require(location.length <= MAX_PHOTO_LOCATION_LENGTH) {
        "A photo location must be at most $MAX_PHOTO_LOCATION_LENGTH characters"
      }
    }
  }

  /**
   * A written answer.
   *
   * @property answer What the explorer wrote.
   * @throws IllegalArgumentException if [answer] is blank or exceeds [MAX_TEXT_ANSWER_LENGTH].
   */
  data class Text(val answer: String) : Proof {
    override val type: ProofType
      get() = ProofType.TEXT

    init {
      require(answer.isNotBlank()) { "A text proof needs an answer" }
      require(answer.length <= MAX_TEXT_ANSWER_LENGTH) {
        "A text answer must be at most $MAX_TEXT_ANSWER_LENGTH characters"
      }
    }
  }
}

/** Lifecycle state of a completion. */
enum class CompletionStatus {
  /** Submitted by the explorer; waiting for the venue's answer. */
  PENDING,
  /** Accepted by the venue. Final. */
  APPROVED,
  /** Declined by the venue, which can still approve it. The party can send a new proof. */
  REJECTED,
}

/**
 * The id of the party's completion of the reservation [reservationId]: the reservation's own id, so
 * a retried or new submission writes the same document and a party, whichever member sends it, has
 * one completion of a reservation.
 */
fun completionId(reservationId: String) = reservationId

/** The longest reason a venue can give for rejecting a proof: the security rules refuse longer. */
const val MAX_REJECT_REASON_LENGTH = 300

/** The longest stored text proof allowed by the security rules. */
const val MAX_TEXT_ANSWER_LENGTH = 1000

/** The longest stored photo location allowed by the security rules. */
const val MAX_PHOTO_LOCATION_LENGTH = 2000

/**
 * Whether a completion may change from [from] to [to]: a pending one can be answered, and a venue
 * that changes its mind can approve one it rejected. A party can also send a new proof, which makes
 * a pending or rejected completion pending again; an approved one is final.
 */
fun canTransition(from: CompletionStatus, to: CompletionStatus): Boolean =
    when (from) {
      CompletionStatus.PENDING -> true
      CompletionStatus.REJECTED -> to != CompletionStatus.REJECTED
      CompletionStatus.APPROVED -> false
    }
