// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.util.Log
import com.github.aroundteam2026.aroundapp.model.party.PartyMemberStatus
import com.github.aroundteam2026.aroundapp.model.party.PartyUserFirestore
import com.github.aroundteam2026.aroundapp.model.party.partyMemberId
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [ReservationRepository] backed by the Firestore `reservations` collection, with each party in the
 * `party_user` collection.
 *
 * Firestore types stay inside this class. Epoch-millisecond times are stored as `Timestamp`s and
 * the status as its enum name. A document without `explorerName`, written before it was stored, is
 * read with an empty one. The reservation id is the document id rather than a stored field.
 * Documents that cannot be mapped back to a [Reservation] are logged and skipped: the `observe*`
 * flows leave them out, and [updateStatus] treats them as missing.
 *
 * The security rules decide who may do what: an explorer may only create a pending reservation
 * together with their own joined membership, only the venue and the party's joined members read it,
 * and only the venue may change a reservation's status, along an allowed transition, apart from a
 * joined member cancelling it. A creation the rules refuse within [confirmTimeoutMs] comes back as
 * a failure, and one they refuse later is only logged; a refused [updateStatus] comes back as a
 * failure.
 *
 * @param db Firestore client to read from and write to.
 * @param confirmTimeoutMs How long [createReservation] waits for the server to confirm a write, in
 *   milliseconds, before it returns success with the write still queued.
 * @param now Clock used for `createdAt`, in epoch milliseconds.
 */
class ReservationRepositoryFirestore(
    private val db: FirebaseFirestore,
    private val confirmTimeoutMs: Long = CONFIRM_TIMEOUT_MS,
    private val now: () -> Long = System::currentTimeMillis,
) : ReservationRepository {
  private val reservations
    get() = db.collection(RESERVATIONS)

  /**
   * Writes the reservation and [creatorUid]'s membership, under [partyMemberId], in one batch,
   * which the server applies whole or not at all, and waits up to [confirmTimeoutMs] for its
   * answer. Both share the creation time. A refusal in that time, e.g. because [creatorUid] is not
   * the signed-in user, is returned as the Firestore exception (`PERMISSION_DENIED`), and Firestore
   * drops its local copy of both. Without an answer in time, e.g. while offline, it returns
   * success: Firestore sends the batch once the device is back online, and a refusal then is only
   * logged. Exceptions thrown while issuing the write (e.g. on a terminated client) are returned as
   * a failure too.
   */
  override suspend fun createReservation(
      reservation: Reservation,
      creatorUid: String,
  ): Result<String> =
      try {
        val document = reservations.document()
        val time = Timestamp(Date(now()))
        val write =
            db.batch()
                .set(document, reservation.toFirestore(time))
                .set(
                    db.collection(PartyUserFirestore.COLLECTION)
                        .document(partyMemberId(document.id, creatorUid)),
                    PartyUserFirestore.joinedMembership(document.id, creatorUid, time),
                )
                .commit()
        write.addOnFailureListener { Log.w(TAG, "Write of reservation ${document.id} failed", it) }
        // Null when the server hasn't answered in time; a refusal throws
        withTimeoutOrNull(confirmTimeoutMs) { write.await() }
        Result.success(document.id)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }

  override fun observeForVenue(venueId: String): Flow<List<Reservation>> =
      reservations.whereEqualTo(VENUE_ID, venueId).observe { it.toReservation() }

  /**
   * Listens to [uid]'s joined memberships and, for each, to its reservation's document. The rules
   * only let a joined member read a reservation one document at a time, so the reservations can't
   * be read with one query.
   *
   * The flow ends with the Firestore exception if the memberships' listener stops, e.g. after
   * sign-out. A reservation whose document is missing or can't be read is left out instead: e.g.
   * when the server refuses a reservation, its listener may fail just before the refused membership
   * disappears.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeForExplorer(uid: String): Flow<List<Reservation>> =
      db.collection(PartyUserFirestore.COLLECTION)
          .whereEqualTo(PartyUserFirestore.UID, uid)
          .whereEqualTo(PartyUserFirestore.STATUS, PartyMemberStatus.JOINED.name)
          .observe { PartyUserFirestore.toPartyMember(it)?.reservationId }
          .flatMapLatest { ids ->
            if (ids.isEmpty()) {
              flowOf(emptyList())
            } else {
              combine(ids.distinct().map { reservations.document(it).observe() }) {
                it.filterNotNull()
              }
            }
          }

  /**
   * Reads the reservation and changes its status in one transaction, so the transition is checked
   * against the status that is actually stored. Only `status` is written, which is all the rules
   * let anyone change. Needs a connection: a transaction fails while the device is offline.
   *
   * Fails with [NoSuchElementException] if the reservation is missing or malformed, with
   * [IllegalStateException] if [canTransition] forbids the change, and with the Firestore exception
   * if the rules refuse it, which they do for everyone but the venue and, to cancel, a joined
   * member of the party.
   */
  override suspend fun updateStatus(id: String, status: ReservationStatus): Result<Unit> =
      try {
        val document = reservations.document(id)
        // The transaction returns the reason for refusing instead of throwing it, so the caller
        // gets the exact exception rather than one wrapped by Firestore.
        val refusal =
            db.runTransaction<Exception?> { transaction ->
                  val current = transaction.get(document).toReservation()
                  when {
                    current == null -> NoSuchElementException("No reservation with id $id")
                    !canTransition(current.status, status) ->
                        IllegalStateException("${current.status} cannot become $status")
                    else -> {
                      transaction.update(document, STATUS, status.name)
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

  /**
   * Turns Firestore's callback-style listener into a Kotlin Flow of the documents mapped by [read],
   * leaving out those it maps to null, which ViewModels can collect. The flow ends with the
   * Firestore exception if the backend stops the listener, e.g. when the rules refuse the query.
   */
  private fun <T : Any> Query.observe(read: (DocumentSnapshot) -> T?): Flow<List<T>> =
      callbackFlow {
        val registration = addSnapshotListener { snapshot, error ->
          if (error != null) {
            close(error)
          } else if (snapshot != null) {
            trySend(snapshot.documents.mapNotNull(read))
          }
        }
        awaitClose { registration.remove() }
      }

  /**
   * Listens to this reservation's document: emits it, or null while it is missing or malformed. If
   * the backend stops the listener, e.g. when the rules refuse the read, it emits null and ends.
   */
  private fun DocumentReference.observe(): Flow<Reservation?> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
      if (error != null) {
        Log.w(TAG, "Listening to reservation $id failed", error)
        trySend(null)
        close()
      } else {
        trySend(snapshot?.toReservation())
      }
    }
    awaitClose { registration.remove() }
  }

  /**
   * The document fields for a new reservation. The status is always [ReservationStatus.PENDING],
   * which is also the only status the rules let an explorer create, and its party has one joined
   * member, the creator, whose membership is in the same batch.
   */
  private fun Reservation.toFirestore(createdAt: Timestamp): Map<String, Any> =
      mapOf(
          QUEST_ID to questId,
          VENUE_ID to venueId,
          SLOT_START to Timestamp(Date(slotStart)),
          STATUS to ReservationStatus.PENDING.name,
          CREATED_AT to createdAt,
          EXPLORER_NAME to explorerName,
          PartyUserFirestore.JOINED_COUNT to 1,
      )

  /** Maps this document to a [Reservation], or returns null if it is missing or malformed. */
  private fun DocumentSnapshot.toReservation(): Reservation? {
    if (!exists()) return null
    return try {
      Reservation(
          id = id,
          questId = required(QUEST_ID) { getString(it) },
          venueId = required(VENUE_ID) { getString(it) },
          slotStart = required(SLOT_START) { getTimestamp(it) }.toDate().time,
          status = ReservationStatus.valueOf(required(STATUS) { getString(it) }),
          createdAt = required(CREATED_AT) { getTimestamp(it) }.toDate().time,
          // Reservations made before the name was stored have none: show them, unnamed
          explorerName = getString(EXPLORER_NAME) ?: "",
      )
    } catch (e: Exception) {
      Log.w(TAG, "Skipping malformed reservation document $id", e)
      null
    }
  }

  /** Reads [field] with [read], throwing if it is absent. */
  private fun <T : Any> required(field: String, read: (String) -> T?): T =
      read(field) ?: throw IllegalArgumentException("Missing field $field")

  private companion object {
    const val TAG = "ReservationRepositoryFirestore"

    /** Long enough for the server to answer on a slow connection, short for a waiting explorer. */
    const val CONFIRM_TIMEOUT_MS = 5_000L

    const val RESERVATIONS = PartyUserFirestore.RESERVATIONS

    const val QUEST_ID = "questId"
    const val VENUE_ID = "venueId"
    const val SLOT_START = "slotStart"
    const val STATUS = "status"
    const val CREATED_AT = "createdAt"
    const val EXPLORER_NAME = "explorerName"
  }
}
