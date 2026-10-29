// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.overview

import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar

/**
 * What the Quests tab's overview shows: the quests the explorer accepted and that are still on.
 *
 * @property status Whether the overview is loading, shown, or failed.
 * @property rows One row per pending or approved reservation, soonest slot first. Empty until
 *   [status] is [OverviewStatus.LOADED], and may stay empty then.
 * @property cancellingIds Ids of the reservations being cancelled right now, among [rows].
 * @property failedCancelIds Ids of the reservations whose last cancel failed, among [rows].
 */
data class OverviewUiState(
    val status: OverviewStatus = OverviewStatus.LOADING,
    val rows: List<OverviewRow> = emptyList(),
    val cancellingIds: Set<String> = emptySet(),
    val failedCancelIds: Set<String> = emptySet(),
)

/**
 * One accepted quest in the overview: the reservation, with its quest and venue.
 *
 * @property reservationId Id of the reservation the row shows.
 * @property questId Id of the accepted quest.
 * @property venueId Id of the quest's venue, whose page a tap opens.
 * @property questTitle The quest's title.
 * @property reward The quest's reward, or null if it has none.
 * @property venueName The venue's name.
 * @property address The venue's street address, or null if it gave none.
 * @property avatar What the row shows for the venue.
 * @property slotStart Start of the booked slot, in epoch milliseconds.
 * @property partySize How many explorers the party has, the explorer included.
 * @property status Whether the venue has approved the reservation yet.
 */
data class OverviewRow(
    val reservationId: String,
    val questId: String,
    val venueId: String,
    val questTitle: String,
    val reward: Reward?,
    val venueName: String,
    val address: String?,
    val avatar: VenueAvatar,
    val slotStart: Long,
    val partySize: Int,
    val status: OverviewRowStatus,
)

/** Where the overview stands. */
enum class OverviewStatus {
  /** The explorer's reservations, their quests and venues are being fetched. */
  LOADING,
  /** The rows are shown, even when there are none. */
  LOADED,
  /** Something could not be fetched; [OverviewViewModel.retry] tries again. */
  FAILED,
}

/** Where a row's reservation stands with its venue. */
enum class OverviewRowStatus {
  /** Pending: the venue hasn't answered yet. */
  WAITING,
  /** The venue approved it. */
  APPROVED,
}
