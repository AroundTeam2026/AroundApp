// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.party

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [PartyRepository] backed by the Firestore `party_user` collection, one document per membership
 * under its [partyMemberId].
 *
 * Firestore types stay inside this class. Times are stored as `Timestamp`s and the status as its
 * enum name. Documents that cannot be mapped back to a [PartyMember] are logged and skipped:
 * [observeMembers] leaves them out, and [withdraw] treats them as missing.
 *
 * The security rules decide who may do what: an explorer joins a pending reservation as themselves,
 * only the member withdraws, and the member, the party's other joined members and the reservation's
 * venue read. A withdrawn member can still read their own membership, but not the party.
 *
 * @param db Firestore client to read from and write to.
 * @param confirmTimeoutMs How long [join] waits for the server to confirm the write, in
 *   milliseconds, before it returns success with the write still queued.
 * @param now Clock used for `joinedAt` and `updatedAt`, in epoch milliseconds.
 */
class PartyRepositoryFirestore(
    private val db: FirebaseFirestore,
    private val confirmTimeoutMs: Long = CONFIRM_TIMEOUT_MS,
    private val now: () -> Long = System::currentTimeMillis,
) : PartyRepository {
  private val members
    get() = db.collection(PARTY_USER)

  /**
   * Issues the write and waits up to [confirmTimeoutMs] for the server's answer, like
   * `ReservationRepositoryFirestore.createReservation`: a refusal in that time is returned as the
   * Firestore exception (`PERMISSION_DENIED`), which is also what joining twice gives, since the
   * second write is an update the rules refuse. Without an answer in time, e.g. while offline, it
   * returns success and the write stays queued.
   */
  override suspend fun join(reservationId: String, uid: String): Result<Unit> =
      try {
        val id = partyMemberId(reservationId, uid)
        val time = Timestamp(Date(now()))
        val write =
            members
                .document(id)
                .set(
                    mapOf(
                        RESERVATION_ID to reservationId,
                        UID to uid,
                        STATUS to PartyMemberStatus.JOINED.name,
                        JOINED_AT to time,
                        UPDATED_AT to time,
                    )
                )
        write.addOnFailureListener { Log.w(TAG, "Write of membership $id failed", it) }
        // Null when the server hasn't answered in time; a refusal throws
        withTimeoutOrNull(confirmTimeoutMs) { write.await() }
        Result.success(Unit)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }

  /**
   * Reads the membership and withdraws it in one transaction, so [canTransition] is checked against
   * the stored status. Needs a connection: a transaction fails while the device is offline.
   *
   * Fails with [NoSuchElementException] if the membership is malformed, with
   * [IllegalStateException] if it is already withdrawn, and with the Firestore exception if the
   * rules refuse it. A missing membership is refused too: the read rule cannot evaluate a missing
   * document, so the server answers `PERMISSION_DENIED`.
   */
  override suspend fun withdraw(reservationId: String, uid: String): Result<Unit> =
      try {
        val document = members.document(partyMemberId(reservationId, uid))
        // The transaction returns the reason for refusing instead of throwing it, so the caller
        // gets the exact exception rather than one wrapped by Firestore.
        val refusal =
            db.runTransaction<Exception?> { transaction ->
                  val current = transaction.get(document).toPartyMember()
                  when {
                    current == null -> NoSuchElementException("No membership ${document.id}")
                    !canTransition(current.status, PartyMemberStatus.WITHDRAWN) ->
                        IllegalStateException("$uid already withdrew from $reservationId")
                    else -> {
                      transaction.update(
                          document,
                          mapOf(
                              STATUS to PartyMemberStatus.WITHDRAWN.name,
                              UPDATED_AT to Timestamp(Date(now())),
                          ),
                      )
                      null
                    }
                  }
                }
                .await()
        if (refusal == null) Result.success(Unit) else Result.failure(refusal)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }

  override fun observeMembers(reservationId: String): Flow<List<PartyMember>> = callbackFlow {
    val registration =
        members.whereEqualTo(RESERVATION_ID, reservationId).addSnapshotListener { snapshot, error ->
          if (error != null) {
            close(error)
          } else if (snapshot != null) {
            trySend(snapshot.documents.mapNotNull { it.toPartyMember() })
          }
        }
    awaitClose { registration.remove() }
  }

  /** Maps this document to a [PartyMember], or returns null if it is missing or malformed. */
  private fun DocumentSnapshot.toPartyMember(): PartyMember? {
    if (!exists()) return null
    return try {
      PartyMember(
          reservationId = required(RESERVATION_ID) { getString(it) },
          uid = required(UID) { getString(it) },
          status = PartyMemberStatus.valueOf(required(STATUS) { getString(it) }),
          joinedAt = required(JOINED_AT) { getTimestamp(it) }.toDate().time,
          updatedAt = required(UPDATED_AT) { getTimestamp(it) }.toDate().time,
      )
    } catch (e: Exception) {
      Log.w(TAG, "Skipping malformed membership document $id", e)
      null
    }
  }

  /** Reads [field] with [read], throwing if it is absent. */
  private fun <T : Any> required(field: String, read: (String) -> T?): T =
      read(field) ?: throw IllegalArgumentException("Missing field $field")

  private companion object {
    const val TAG = "PartyRepositoryFirestore"

    /** Long enough for the server to answer on a slow connection, short for a waiting explorer. */
    const val CONFIRM_TIMEOUT_MS = 5_000L

    const val PARTY_USER = "party_user"

    const val RESERVATION_ID = "reservationId"
    const val UID = "uid"
    const val STATUS = "status"
    const val JOINED_AT = "joinedAt"
    const val UPDATED_AT = "updatedAt"
  }
}
