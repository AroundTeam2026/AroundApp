// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.dashboard

import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar

/**
 * What the venue's dashboard shows: the venue, the reservations it has to answer, the approved
 * ones, and the summary tiles.
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
    val savingIds: Set<String> = emptySet(),
    val updateFailed: Boolean = false,
) {
  /** How many reservations wait for the venue's answer. */
  val pendingCount: Int
    get() = needsAnswer.size

  /** Whether the dashboard is shown and has no reservation to list: "No reservations yet". */
  val isEmpty: Boolean
    get() = status == VenueDashboardStatus.LOADED && needsAnswer.isEmpty() && upcoming.isEmpty()
}

/**
 * One reservation on the dashboard.
 *
 * @property reservationId Id of the reservation, which approve and decline take.
 * @property questTitle Title of the reserved quest, or null if the venue deleted it.
 * @property explorerName Display name of the explorer who reserved, or null for a reservation made
 *   before names were stored.
 * @property partySize How many explorers the party has.
 * @property requestedAt When the explorer made the reservation, in epoch milliseconds.
 * @property approved Whether the venue approved it; a pending one shows Decline and Approve.
 */
data class DashboardReservation(
    val reservationId: String,
    val questTitle: String?,
    val explorerName: String?,
    val partySize: Int,
    val requestedAt: Long,
    val approved: Boolean,
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
