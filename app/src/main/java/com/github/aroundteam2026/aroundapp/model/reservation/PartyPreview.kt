// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

/**
 * A party as an explorer who hasn't joined it sees it, e.g. one who typed its party code: the
 * [reservation] and how many explorers have [joinedCount] it.
 *
 * The count comes from the reservation itself, since only the party's members and venue read the
 * memberships.
 *
 * @property reservation The party's reservation, with its quest, venue and status.
 * @property joinedCount How many explorers are joined in the party, the creator included.
 */
data class PartyPreview(val reservation: Reservation, val joinedCount: Int)
