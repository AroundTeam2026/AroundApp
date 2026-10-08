// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * [ReservationRepository] backed by the Firestore `reservations` collection.
 *
 * Firestore types stay inside this class. Epoch-millisecond times are stored as `Timestamp`s and
 * the status as its enum name. The reservation id is the document id rather than a stored field.
 * Documents that cannot be mapped back to a [Reservation] are logged and skipped: the `observe*`
 * flows leave them out, and [updateStatus] treats them as missing.
 *
 * The security rules decide who may do what: an explorer may only create a pending reservation that
 * lists them, and only the venue may change a reservation's status, along an allowed transition. A
 * creation the rules refuse is only logged, like a refused quest; a refused [updateStatus] comes
 * back as a failure.
 *
 * @param db Firestore client to read from and write to.
 * @param now Clock used for `createdAt`, in epoch milliseconds.
 */
class ReservationRepositoryFirestore(
    private val db: FirebaseFirestore,
    private val now: () -> Long = System::currentTimeMillis,
) : ReservationRepository {
  private val reservations
    get() = db.collection(RESERVATIONS)

  /**
   * Issues the write without waiting for the server, so it also succeeds offline; Firestore sends
   * it once the device is back online. A later server rejection, e.g. because the signed-in user is
   * not in `explorerUids`, is only logged. Exceptions thrown while issuing the write (e.g. on a
   * terminated client) are returned as a failure.
   */
  override suspend fun createReservation(reservation: Reservation): Result<String> =
      try {
        val document = reservations.document()
        document.set(reservation.toFirestore(Timestamp(Date(now())))).addOnFailureListener {
          Log.w(TAG, "Write of reservation ${document.id} was rejected", it)
        }
        Result.success(document.id)
      } catch (e: Exception) {
        Result.failure(e)
      }

  override fun observeForVenue(venueId: String): Flow<List<Reservation>> =
      reservations.whereEqualTo(VENUE_ID, venueId).observe()

  override fun observeForExplorer(uid: String): Flow<List<Reservation>> =
      reservations.whereArrayContains(EXPLORER_UIDS, uid).observe()

  /**
   * Reads the reservation and changes its status in one transaction, so the transition is checked
   * against the status that is actually stored. Only `status` is written, which is all the rules
   * let the venue change. Needs a connection: a transaction fails while the device is offline.
   *
   * Fails with [NoSuchElementException] if the reservation is missing or malformed, with
   * [IllegalStateException] if [canTransition] forbids the change, and with the Firestore exception
   * if the rules refuse it, which they do for everyone but the venue.
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
   * Turns Firestore's callback-style listener into a Kotlin Flow<List<Reservation>>, which
   * ViewModels can collect. Both observe functions use it. The flow ends with the Firestore
   * exception if the backend stops the listener, e.g. when the rules refuse the query.
   */
  private fun Query.observe(): Flow<List<Reservation>> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
      if (error != null) {
        close(error)
      } else if (snapshot != null) {
        trySend(snapshot.documents.mapNotNull { it.toReservation() })
      }
    }
    awaitClose { registration.remove() }
  }

  /**
   * The document fields for a new reservation. The status is always [ReservationStatus.PENDING],
   * which is also the only status the rules let an explorer create.
   */
  private fun Reservation.toFirestore(createdAt: Timestamp): Map<String, Any> =
      mapOf(
          QUEST_ID to questId,
          VENUE_ID to venueId,
          EXPLORER_UIDS to explorerUids,
          SLOT_START to Timestamp(Date(slotStart)),
          STATUS to ReservationStatus.PENDING.name,
          CREATED_AT to createdAt,
      )

  /** Maps this document to a [Reservation], or returns null if it is missing or malformed. */
  private fun DocumentSnapshot.toReservation(): Reservation? {
    if (!exists()) return null
    return try {
      Reservation(
          id = id,
          questId = required(QUEST_ID) { getString(it) },
          venueId = required(VENUE_ID) { getString(it) },
          explorerUids =
              required(EXPLORER_UIDS) { field -> (get(field) as List<*>?)?.map { it as String } },
          slotStart = required(SLOT_START) { getTimestamp(it) }.toDate().time,
          status = ReservationStatus.valueOf(required(STATUS) { getString(it) }),
          createdAt = required(CREATED_AT) { getTimestamp(it) }.toDate().time,
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
    const val RESERVATIONS = "reservations"

    const val QUEST_ID = "questId"
    const val VENUE_ID = "venueId"
    const val EXPLORER_UIDS = "explorerUids"
    const val SLOT_START = "slotStart"
    const val STATUS = "status"
    const val CREATED_AT = "createdAt"
  }
}
