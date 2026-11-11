// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.party

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Whether this member is still in the party. */
val PartyMember.isJoined: Boolean
  get() = status == PartyMemberStatus.JOINED

/**
 * Observes the parties of [reservationIds], keyed by reservation id, joined and withdrawn members
 * alike. Emits once every party is read, then on each change; at once an empty map without ids.
 * Fails as [PartyRepository.observeMembers] does.
 */
fun PartyRepository.observeParties(
    reservationIds: Collection<String>
): Flow<Map<String, List<PartyMember>>> {
  val ids = reservationIds.distinct()
  if (ids.isEmpty()) return flowOf(emptyMap())
  return combine(ids.map { id -> observeMembers(id).map { id to it } }) { it.toMap() }
}
