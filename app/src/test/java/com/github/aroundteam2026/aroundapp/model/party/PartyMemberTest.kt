// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.party

import com.github.aroundteam2026.aroundapp.model.party.PartyMemberStatus.JOINED
import com.github.aroundteam2026.aroundapp.model.party.PartyMemberStatus.WITHDRAWN
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests [canTransition] for memberships and [partyMemberId]. */
class PartyMemberTest {
  /** Checks every pair of statuses, so an extra or a missing transition fails the test. */
  @Test
  fun canTransition_allowsOnlyWithdrawing() {
    for (from in PartyMemberStatus.entries) {
      for (to in PartyMemberStatus.entries) {
        assertEquals("$from -> $to", from == JOINED && to == WITHDRAWN, canTransition(from, to))
      }
    }
  }

  /** The rules rebuild the id as `{reservationId}_{uid}`, so any other format is refused. */
  @Test
  fun partyMemberId_joinsTheReservationAndTheUid() {
    assertEquals("r1_u1", partyMemberId("r1", "u1"))
  }
}
