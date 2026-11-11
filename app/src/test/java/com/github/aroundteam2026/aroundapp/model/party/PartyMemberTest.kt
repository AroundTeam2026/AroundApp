// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.party

import com.github.aroundteam2026.aroundapp.model.party.PartyMemberStatus.JOINED
import com.github.aroundteam2026.aroundapp.model.party.PartyMemberStatus.WITHDRAWN
import com.github.aroundteam2026.aroundapp.model.reservation.Reservation
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests [canTransition] for memberships, [createdParty] and [partyMemberId]. */
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

  /** The creator's membership is written with the reservation, at its creation time. */
  @Test
  fun createdParty_isTheMembershipThatJoinedAsTheReservationWasMade() {
    val reservation = Reservation("r1", "q", "v", 0L, ReservationStatus.PENDING, createdAt = 5L)
    fun member(reservationId: String = "r1", joinedAt: Long = 5L) =
        PartyMember(reservationId, "u1", JOINED, joinedAt, joinedAt)

    assertTrue(member().createdParty(reservation))
    assertFalse(member(joinedAt = 6L).createdParty(reservation))
    assertFalse(member(reservationId = "r2").createdParty(reservation))
  }

  /** The rules rebuild the id as `{reservationId}_{uid}`, so any other format is refused. */
  @Test
  fun partyMemberId_joinsTheReservationAndTheUid() {
    assertEquals("r1_u1", partyMemberId("r1", "u1"))
  }
}
