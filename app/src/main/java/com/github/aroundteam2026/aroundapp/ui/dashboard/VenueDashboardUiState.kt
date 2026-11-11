// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.dashboard

import androidx.compose.ui.graphics.ImageBitmap
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar

/**
 * What the venue's dashboard shows: the venue, the proofs it has to review, the reservations it has
 * to answer, the approved ones, and the summary tiles.
 *
 * Reservations have no visit time yet: accepting a quest doesn't pick a slot, so a reservation's
 * stored slot only dates the request. The dashboard therefore shows no slot, and nothing that
 * depends on one, such as a count of today's visits.
 *
 * @property status Whether the dashboard is loading, shown, or failed.
 * @property venueName The venue's name, for the header; empty until it is read.
 * @property avatar What the header shows for the venue, or null until it is read.
 * @property needsAnswer The pending reservations, oldest request first. Empty until [status] is
 *   [VenueDashboardStatus.LOADED].
 * @property upcoming The approved reservations, oldest request first. Empty until [status] is
 *   [VenueDashboardStatus.LOADED].
 * @property activeQuestsCount How many of the venue's quests are active, so explorers can take
 *   them.
 * @property proofsToReview The proofs explorers sent and the venue hasn't reviewed, oldest first.
 *   Empty until [status] is [VenueDashboardStatus.LOADED].
 * @property savingIds Ids of the pending reservations whose answer is being saved, or saved but not
 *   shown by the listener yet; their buttons are disabled meanwhile.
 * @property updateFailed Whether the last approve or decline failed. The screen shows it once, then
 *   calls [VenueDashboardViewModel.clearUpdateError].
 */
data class VenueDashboardUiState(
    val status: VenueDashboardStatus = VenueDashboardStatus.LOADING,
    val venueName: String = "",
    val avatar: VenueAvatar? = null,
    val needsAnswer: List<DashboardReservation> = emptyList(),
    val upcoming: List<DashboardReservation> = emptyList(),
    val activeQuestsCount: Int = 0,
    val proofsToReview: List<DashboardProof> = emptyList(),
    val savingIds: Set<String> = emptySet(),
    val updateFailed: Boolean = false,
) {
  /** How many reservations wait for the venue's answer. */
  val pendingCount: Int
    get() = needsAnswer.size

  /**
   * Whether the dashboard is shown and has no reservation or proof to list: "No reservations yet".
   */
  val isEmpty: Boolean
    get() =
        status == VenueDashboardStatus.LOADED &&
            needsAnswer.isEmpty() &&
            upcoming.isEmpty() &&
            proofsToReview.isEmpty()
}

/**
 * One reservation on the dashboard.
 *
 * @property reservationId Id of the reservation, which approve and decline take.
 * @property questTitle Title of the reserved quest, or null if the venue deleted it.
 * @property explorerName Display name of the explorer who reserved, from their profile or else as
 *   stored with the reservation, or null if neither has one.
 * @property partySize How many explorers the party has.
 * @property requestedAt When the explorer made the reservation, in epoch milliseconds.
 * @property approved Whether the venue approved it; a pending one shows Decline and Approve.
 * @property approveBlock Why the venue can't approve it yet, or null if it can. Only a pending
 *   reservation has one; Decline stays available whatever it is.
 * @property members The party's joined members, the explorer who reserved first, then in the order
 *   they joined. Empty while the party can't be read.
 * @property groupSize The party size the quest asks for, or null if it takes explorers alone as
 *   well, or was deleted.
 * @property exactGroupSize Whether the quest takes exactly [groupSize] explorers, "3 of 4 joined",
 *   rather than at least that many, "6 joined · at least 4".
 */
data class DashboardReservation(
    val reservationId: String,
    val questTitle: String?,
    val explorerName: String?,
    val partySize: Int,
    val requestedAt: Long,
    val approved: Boolean,
    val approveBlock: ApproveBlock? = null,
    val members: List<DashboardMember> = emptyList(),
    val groupSize: Int? = null,
    val exactGroupSize: Boolean = false,
)

/**
 * One explorer in a reservation's party.
 *
 * @property name Their display name, or null if their profile can't be read.
 * @property picture Their profile picture, or null if they have none or it isn't downloaded yet:
 *   their initials show instead.
 */
data class DashboardMember(val name: String?, val picture: ImageBitmap? = null)

/**
 * Why a pending reservation can't be approved: the rules only approve a party that has the size its
 * quest asks for, at least or exactly [com.github.aroundteam2026.aroundapp.model.quest.Quest]'s
 * `minPartySize`, so the dashboard disables Approve and says why rather than fail on tap.
 */
sealed interface ApproveBlock {
  /** The party needs [missing] more explorers, at least one. */
  data class TooFew(val missing: Int) : ApproveBlock

  /** The quest takes exactly [size] explorers and the party has more: some have to leave. */
  data class TooMany(val size: Int) : ApproveBlock

  /** The venue deleted the quest, so there is no size to check and the rules refuse approving. */
  data object QuestMissing : ApproveBlock
}

/**
 * One proof to review on the dashboard, as the Figma row `S4 · Proof row`: opening it shows the
 * proof itself.
 *
 * @property completionId Id of the completion the proof was sent with.
 * @property explorerName Display name of the explorer who made the reservation the proof belongs
 *   to, or null if unknown. Only that explorer's name is stored, so a party member's proof shows it
 *   too.
 * @property questTitle Title of the quest, or null if the venue deleted it or the reservation can't
 *   be read.
 * @property partySize How many explorers the reservation's party has.
 * @property proofType Whether the proof is a photo or a written answer.
 * @property sentAt When the explorer sent it, in epoch milliseconds.
 */
data class DashboardProof(
    val completionId: String,
    val explorerName: String?,
    val questTitle: String?,
    val partySize: Int,
    val proofType: ProofType,
    val sentAt: Long,
)

/** Where the dashboard stands. */
enum class VenueDashboardStatus {
  /** The venue, its reservations and quests are being fetched. */
  LOADING,
  /** The reservations are shown, even when there are none. */
  LOADED,
  /** Listening failed; [VenueDashboardViewModel.retry] tries again. */
  FAILED,
}
