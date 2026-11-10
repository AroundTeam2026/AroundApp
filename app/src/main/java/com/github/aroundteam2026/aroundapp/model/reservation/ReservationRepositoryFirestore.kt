// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.util.Log
import com.github.aroundteam2026.aroundapp.model.party.EVERY_PARTY_CODE_TAKEN
import com.github.aroundteam2026.aroundapp.model.party.MAX_PARTY_CODE_ATTEMPTS
import com.github.aroundteam2026.aroundapp.model.party.PartyCodeNotFoundException
import com.github.aroundteam2026.aroundapp.model.party.PartyMemberStatus
import com.github.aroundteam2026.aroundapp.model.party.PartyUserFirestore
import com.github.aroundteam2026.aroundapp.model.party.newPartyCode
import com.github.aroundteam2026.aroundapp.model.party.normalizePartyCode
import com.github.aroundteam2026.aroundapp.model.party.partyMemberId
import com.github.aroundteam2026.aroundapp.model.required
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
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
 * `party_user` collection and each party code in the `party_codes` collection.
 *
 * Documents that cannot be mapped back to a [Reservation] are logged and skipped: the `observe*`
 * flows leave them out, and [updateStatus] treats them as missing.
 *
 * The security rules let an explorer create only a pending reservation with their own joined
 * membership, and only the venue change its status, apart from a joined member cancelling it. A
 * creation refused within [confirmTimeoutMs] comes back as a failure, and one refused later is only
 * logged.
 *
 * @param db Firestore client to read from and write to.
 * @param confirmTimeoutMs How long [createReservation] waits for the server to confirm a write, in
 *   milliseconds, before it returns success with the write still queued.
 * @param newCode Draws the party code of a new reservation. Random by default; tests pass a taken
 *   code to cause a collision.
 * @param now Clock used for `createdAt`, in epoch milliseconds.
 */
class ReservationRepositoryFirestore(
    private val db: FirebaseFirestore,
    private val confirmTimeoutMs: Long = CONFIRM_TIMEOUT_MS,
    private val newCode: () -> String = { newPartyCode() },
    private val now: () -> Long = System::currentTimeMillis,
) : ReservationRepository {
  private val reservations
    get() = db.collection(RESERVATIONS)

  private val partyCodes
    get() = db.collection(PARTY_CODES)

  /**
   * Writes the reservation, [creatorUid]'s membership and its party code in one batch, applied
   * whole or not at all, and waits up to [confirmTimeoutMs] for the server's answer.
   *
   * The rules refuse a code that is already taken. When they refuse a batch and its code's document
   * exists on the server, it writes the batch again under a new id with a new code, at most
   * [MAX_PARTY_CODE_ATTEMPTS] codes in all, and then fails with an [IllegalStateException] caused
   * by the last refusal. Any other refusal in that time, e.g. because [creatorUid] is not the
   * signed-in user, is returned as the Firestore exception, and Firestore drops its local copy of
   * the batch. Without an answer in time, e.g. while offline, it returns success and a later
   * refusal, a taken code included, is only logged. Errors while issuing the write are returned as
   * a failure too.
   */
  override suspend fun createReservation(
      reservation: Reservation,
      creatorUid: String,
  ): Result<String> {
    return try {
      val time = Timestamp(Date(now()))
      var takenCodeRefusal: FirebaseFirestoreException? = null
      for (attempt in 1..MAX_PARTY_CODE_ATTEMPTS) {
        val code = newCode()
        try {
          return Result.success(create(reservation, creatorUid, time, code))
        } catch (e: FirebaseFirestoreException) {
          if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED || !isTaken(code)) throw e
          Log.i(TAG, "Party code $code is taken (attempt $attempt)")
          takenCodeRefusal = e
        }
      }
      Result.failure(IllegalStateException(EVERY_PARTY_CODE_TAKEN, takenCodeRefusal))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Writes the batch of a new reservation with the party code [code], created at [time], and waits
   * up to [confirmTimeoutMs] for the server's answer. Returns the reservation's id, also when the
   * server hasn't answered in time, and throws its refusal.
   */
  private suspend fun create(
      reservation: Reservation,
      creatorUid: String,
      time: Timestamp,
      code: String,
  ): String {
    val document = reservations.document()
    val write =
        db.batch()
            .set(document, reservation.toFirestore(time, code))
            .set(
                db.collection(PartyUserFirestore.COLLECTION)
                    .document(partyMemberId(document.id, creatorUid)),
                PartyUserFirestore.joinedMembership(document.id, creatorUid, time),
            )
            .set(partyCodes.document(code), mapOf(CODE_RESERVATION_ID to document.id))
            .commit()
    write.addOnFailureListener { Log.w(TAG, "Write of reservation ${document.id} failed", it) }
    // Null when the server hasn't answered in time; a refusal throws
    withTimeoutOrNull(confirmTimeoutMs) { write.await() }
    return document.id
  }

  /** Whether another reservation already has the party code [code], asking the server. */
  private suspend fun isTaken(code: String): Boolean =
      partyCodes.document(code).get(Source.SERVER).await().exists()

  /**
   * Normalizes [code] and reads its `party_codes` document from the server. A code that is not one,
   * or whose document is missing or has no reservation id, is a [PartyCodeNotFoundException]; a
   * read that fails, e.g. offline, is the Firestore exception.
   */
  override suspend fun findReservationIdByCode(code: String): Result<String> =
      try {
        val normalized = normalizePartyCode(code) ?: throw PartyCodeNotFoundException(code)
        val id =
            partyCodes
                .document(normalized)
                .get(Source.SERVER)
                .await()
                .getString(CODE_RESERVATION_ID) ?: throw PartyCodeNotFoundException(normalized)
        Result.success(id)
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
   * member of the party, and when approving a party without the size its quest asks for.
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
   * The document fields for a new reservation with the party code [partyCode]. The status is always
   * [ReservationStatus.PENDING], which is also the only status the rules let an explorer create,
   * and its party has one joined member, the creator, whose membership is in the same batch, as is
   * the code's document.
   */
  private fun Reservation.toFirestore(createdAt: Timestamp, partyCode: String): Map<String, Any> =
      mapOf(
          QUEST_ID to questId,
          VENUE_ID to venueId,
          SLOT_START to Timestamp(Date(slotStart)),
          STATUS to ReservationStatus.PENDING.name,
          CREATED_AT to createdAt,
          EXPLORER_NAME to explorerName,
          PartyUserFirestore.JOINED_COUNT to 1,
          PARTY_CODE to partyCode,
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
          // Reservations made before party codes have none
          partyCode = getString(PARTY_CODE),
      )
    } catch (e: Exception) {
      Log.w(TAG, "Skipping malformed reservation document $id", e)
      null
    }
  }

  private companion object {
    const val TAG = "ReservationRepositoryFirestore"

    /** Long enough for the server to answer on a slow connection, short for a waiting explorer. */
    const val CONFIRM_TIMEOUT_MS = 5_000L

    const val RESERVATIONS = PartyUserFirestore.RESERVATIONS
    const val PARTY_CODES = "party_codes"

    /** The field of a `party_codes` document holding the id of the code's reservation. */
    const val CODE_RESERVATION_ID = "reservationId"

    const val QUEST_ID = "questId"
    const val VENUE_ID = "venueId"
    const val SLOT_START = "slotStart"
    const val STATUS = "status"
    const val CREATED_AT = "createdAt"
    const val EXPLORER_NAME = "explorerName"
    const val PARTY_CODE = "partyCode"
  }
}
