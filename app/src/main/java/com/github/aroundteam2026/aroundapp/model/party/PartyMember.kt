// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.party

/**
 * An explorer's membership in the party of one reservation. A withdrawal keeps the membership, so
 * the party's history stays: memberships are never deleted.
 *
 * @property reservationId Id of the reservation whose party this is.
 * @property uid Uid of the explorer.
 * @property status Whether the explorer is still in the party; see [canTransition].
 * @property joinedAt When the explorer joined, in epoch milliseconds.
 * @property updatedAt When the status last changed, in epoch milliseconds; [joinedAt] until then.
 */
data class PartyMember(
    val reservationId: String,
    val uid: String,
    val status: PartyMemberStatus,
    val joinedAt: Long,
    val updatedAt: Long,
)

/** The most explorers a party holds at once: more can't join it while it has that many. */
const val MAX_PARTY_SIZE = 10

/** Whether an explorer is in a reservation's party. */
enum class PartyMemberStatus {
  /** In the party. */
  JOINED,
  /** Left the party. Final: an explorer who withdrew can't join that reservation again. */
  WITHDRAWN,
}

/** Whether a membership may change from [from] to [to]: a member can only withdraw. */
fun canTransition(from: PartyMemberStatus, to: PartyMemberStatus): Boolean =
    from == PartyMemberStatus.JOINED && to == PartyMemberStatus.WITHDRAWN

/**
 * The id of [uid]'s membership in the party of [reservationId]. Built from both, so the rules can
 * find a membership with one read and an explorer can't join a party twice.
 */
fun partyMemberId(reservationId: String, uid: String) = "${reservationId}_$uid"
