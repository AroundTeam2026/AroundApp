// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.party

import com.github.aroundteam2026.aroundapp.model.quest.Quest

/**
 * The most explorers that can be [PartyMemberStatus.JOINED] in a party for this quest: its
 * [Quest.minPartySize] if the size is exact, [MAX_PARTY_SIZE] otherwise, and never more than
 * [MAX_PARTY_SIZE]. Joining a party that already has this many is refused, as by the rules.
 */
val Quest.partyCapacity: Int
  get() = if (exactPartySize) minOf(minPartySize, MAX_PARTY_SIZE) else MAX_PARTY_SIZE

/**
 * Whether a party of [joined] explorers has the size this quest asks for: exactly or at least
 * [Quest.minPartySize]. The venue can approve a reservation only then, as the rules enforce.
 *
 * A member may still leave an approved party, which can take it below this size: the reservation
 * stays approved. Warning the venue when that happens, so it can cancel, is left to its dashboard.
 */
fun Quest.meetsPartySize(joined: Int): Boolean =
    if (exactPartySize) joined == minPartySize else joined >= minPartySize
