// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.party

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.every
import io.mockk.mockk
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests [PartyUserFirestore.toPartyMember] without the Firebase emulator. */
class PartyUserFirestoreTest {
  private val joinedAt = Timestamp(Date(1_000))
  private val updatedAt = Timestamp(Date(2_000))

  /** A stored membership whose [PartyUserFirestore.STATUS] is [status] and [missing] is absent. */
  private fun document(
      status: String = PartyMemberStatus.JOINED.name,
      missing: String? = null,
  ): DocumentSnapshot {
    val fields =
        mapOf(
                PartyUserFirestore.RESERVATION_ID to "r1",
                PartyUserFirestore.UID to "u1",
                PartyUserFirestore.STATUS to status,
                PartyUserFirestore.JOINED_AT to joinedAt,
                PartyUserFirestore.UPDATED_AT to updatedAt,
            )
            .filterKeys { it != missing }
    return mockk {
      every { exists() } returns true
      every { id } returns "r1_u1"
      every { getString(any()) } answers { fields[firstArg()] as? String }
      every { getTimestamp(any()) } answers { fields[firstArg()] as? Timestamp }
    }
  }

  @Test
  fun toPartyMember_mapsAStoredMembership() {
    assertEquals(
        PartyMember(
            reservationId = "r1",
            uid = "u1",
            status = PartyMemberStatus.JOINED,
            joinedAt = 1_000,
            updatedAt = 2_000,
        ),
        PartyUserFirestore.toPartyMember(document()),
    )
  }

  @Test
  fun toPartyMember_returnsNullForAMissingDocument() {
    val document = mockk<DocumentSnapshot> { every { exists() } returns false }
    assertNull(PartyUserFirestore.toPartyMember(document))
  }

  /** Each field is required, so dropping any one of them skips the document. */
  @Test
  fun toPartyMember_returnsNullWhenAFieldIsMissing() {
    for (field in
        listOf(
            PartyUserFirestore.RESERVATION_ID,
            PartyUserFirestore.UID,
            PartyUserFirestore.STATUS,
            PartyUserFirestore.JOINED_AT,
            PartyUserFirestore.UPDATED_AT,
        )) {
      assertNull(field, PartyUserFirestore.toPartyMember(document(missing = field)))
    }
  }

  @Test
  fun toPartyMember_returnsNullForAnUnknownStatus() {
    assertNull(PartyUserFirestore.toPartyMember(document(status = "BOGUS")))
  }
}
