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

/** Tests [canTransition] for memberships, [createdParty], [leavesPartiesOf] and [partyMemberId]. */
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

  /**
   * A joined party is left, a created one cancelled, and one without the explorer's joined
   * membership is in neither, so nothing is offered for it.
   */
  @Test
  fun leavesPartiesOf_tellsOnlyFromTheExplorersJoinedMembership() {
    fun reservation(id: String) =
        Reservation(id, "q", "v", 0L, ReservationStatus.PENDING, createdAt = 5L)
    fun member(id: String, uid: String = "me", joinedAt: Long = 5L, withdrawn: Boolean = false) =
        PartyMember(id, uid, if (withdrawn) WITHDRAWN else JOINED, joinedAt, joinedAt)
    val parties =
        mapOf(
            "created" to listOf(member("created"), member("created", uid = "friend", joinedAt = 6)),
            "joined" to listOf(member("joined", uid = "friend"), member("joined", joinedAt = 6)),
            "withdrawn" to listOf(member("withdrawn", joinedAt = 6, withdrawn = true)),
            "notMine" to listOf(member("notMine", uid = "friend")),
        )
    val reservations =
        listOf("created", "joined", "withdrawn", "notMine", "unread").map(::reservation)

    assertEquals(
        mapOf("created" to false, "joined" to true),
        leavesPartiesOf(reservations, parties, "me"),
    )
  }

  /** The rules rebuild the id as `{reservationId}_{uid}`, so any other format is refused. */
  @Test
  fun partyMemberId_joinsTheReservationAndTheUid() {
    assertEquals("r1_u1", partyMemberId("r1", "u1"))
  }
}
