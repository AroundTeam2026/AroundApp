// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.dashboard

/**
 * What the venue's dashboard shows: the reservations it has to answer, the approved ones still
 * ahead, and the summary tiles.
 *
 * @property status Whether the dashboard is loading, shown, or failed.
 * @property needsAnswer The pending reservations, oldest request first. Empty until [status] is
 *   [VenueDashboardStatus.LOADED].
 * @property upcoming The approved reservations whose slot starts today or later, soonest first.
 *   Empty until [status] is [VenueDashboardStatus.LOADED].
 * @property todayCount How many approved reservations have their slot today, in the phone's time
 *   zone: the visits the venue expects today.
 * @property completionsCount How many quests explorers completed at this venue; 0 until completions
 *   exist.
 * @property savingIds Ids of the reservations whose answer is being saved; their buttons are
 *   disabled meanwhile.
 * @property updateFailed Whether the last approve or decline failed. The screen shows it once, then
 *   calls [VenueDashboardViewModel.clearUpdateError].
 */
data class VenueDashboardUiState(
    val status: VenueDashboardStatus = VenueDashboardStatus.LOADING,
    val needsAnswer: List<DashboardReservation> = emptyList(),
    val upcoming: List<DashboardReservation> = emptyList(),
    val todayCount: Int = 0,
    val completionsCount: Int = 0,
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
 * @property slotStart Start of the requested slot, in epoch milliseconds.
 * @property approved Whether the venue approved it; a pending one shows Decline and Approve.
 */
data class DashboardReservation(
    val reservationId: String,
    val questTitle: String?,
    val explorerName: String?,
    val partySize: Int,
    val slotStart: Long,
    val approved: Boolean,
)

/** Where the dashboard stands. */
enum class VenueDashboardStatus {
  /** The venue's reservations and quests are being fetched. */
  LOADING,
  /** The reservations are shown, even when there are none. */
  LOADED,
  /** Listening failed; [VenueDashboardViewModel.retry] tries again. */
  FAILED,
}
