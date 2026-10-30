// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.party

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

/**
 * How memberships are stored in the Firestore `party_user` collection. Shared by
 * [PartyRepositoryFirestore] and the reservation repository, which creates the creator's membership
 * in the same batch as the reservation, so both write and read the same document.
 */
internal object PartyUserFirestore {
  const val COLLECTION = "party_user"

  const val RESERVATION_ID = "reservationId"
  const val UID = "uid"
  const val STATUS = "status"
  const val JOINED_AT = "joinedAt"
  const val UPDATED_AT = "updatedAt"

  private const val TAG = "PartyUserFirestore"

  /**
   * The fields of [uid]'s new [PartyMemberStatus.JOINED] membership in the party of
   * [reservationId], joined at [time]. It is stored under [partyMemberId].
   */
  fun joinedMembership(reservationId: String, uid: String, time: Timestamp): Map<String, Any> =
      mapOf(
          RESERVATION_ID to reservationId,
          UID to uid,
          STATUS to PartyMemberStatus.JOINED.name,
          JOINED_AT to time,
          UPDATED_AT to time,
      )

  /** Maps [document] to a [PartyMember], or returns null if it is missing or malformed. */
  fun toPartyMember(document: DocumentSnapshot): PartyMember? {
    if (!document.exists()) return null
    return try {
      PartyMember(
          reservationId = document.required(RESERVATION_ID) { getString(it) },
          uid = document.required(UID) { getString(it) },
          status = PartyMemberStatus.valueOf(document.required(STATUS) { getString(it) }),
          joinedAt = document.required(JOINED_AT) { getTimestamp(it) }.toDate().time,
          updatedAt = document.required(UPDATED_AT) { getTimestamp(it) }.toDate().time,
      )
    } catch (e: Exception) {
      Log.w(TAG, "Skipping malformed membership document ${document.id}", e)
      null
    }
  }

  /** Reads [field] with [read], throwing if it is absent. */
  private fun <T : Any> DocumentSnapshot.required(
      field: String,
      read: DocumentSnapshot.(String) -> T?,
  ): T = read(field) ?: throw IllegalArgumentException("Missing field $field")
}
