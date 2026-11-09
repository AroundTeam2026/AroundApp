// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.util.Log
import com.github.aroundteam2026.aroundapp.model.required
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import java.util.Date
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [CompletionRepository] backed by the Firestore `completions` collection.
 *
 * Documents that cannot be mapped back to a [Completion] are logged and skipped: the `observe*`
 * flows leave them out, and [approve] and [reject] treat them as missing.
 *
 * The security rules let a joined member of an approved reservation create only their own pending
 * completion, and only the venue answer it. A submission refused within [confirmTimeoutMs] comes
 * back as a failure, and one refused later is only logged.
 *
 * @param db Firestore client to read from and write to.
 * @param confirmTimeoutMs How long [submit] waits for the server, in milliseconds, before it
 *   returns success with the write still queued.
 * @param now Clock used for `submittedAt`, in epoch milliseconds.
 */
class CompletionRepositoryFirestore(
    private val db: FirebaseFirestore,
    private val confirmTimeoutMs: Long = CONFIRM_TIMEOUT_MS,
    private val now: () -> Long = System::currentTimeMillis,
) : CompletionRepository {
  private val completions
    get() = db.collection(COMPLETIONS)

  /**
   * Writes the completion under its [completionId] and waits up to [confirmTimeoutMs] for the
   * server's answer.
   *
   * A refusal in that time is returned as the Firestore exception, or as an [IllegalStateException]
   * if the completion was already there. Without an answer in time, e.g. while offline, it returns
   * success and a later refusal is only logged. Errors while issuing the write are returned as a
   * failure too.
   */
  override suspend fun submit(
      reservationId: String,
      venueId: String,
      explorerUid: String,
      proof: Proof,
  ): Result<String> =
      try {
        val document = completions.document(completionId(reservationId, explorerUid))
        // A plain write, unlike a transaction, works offline, so it doesn't read first: the rules
        // refuse a second submission as an update, and only then is the document read to tell
        // that refusal apart from the others
        val write =
            document.set(
                mapOf(
                    RESERVATION_ID to reservationId,
                    VENUE_ID to venueId,
                    EXPLORER_UID to explorerUid,
                    proof.toField(),
                    STATUS to CompletionStatus.PENDING.name,
                    SUBMITTED_AT to Timestamp(Date(now())),
                )
            )
        write.addOnFailureListener { Log.w(TAG, "Write of completion ${document.id} failed", it) }
        try {
          // Null when the server hasn't answered in time; a refusal throws
          withTimeoutOrNull(confirmTimeoutMs) { write.await() }
        } catch (e: FirebaseFirestoreException) {
          if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED && isStored(document)) {
            throw IllegalStateException("Completion ${document.id} was already submitted", e)
          }
          throw e
        }
        Result.success(document.id)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }

  override fun observeForExplorer(uid: String): Flow<List<Completion>> =
      completions.whereEqualTo(EXPLORER_UID, uid).observe()

  override fun observeForVenue(venueId: String): Flow<List<Completion>> =
      completions.whereEqualTo(VENUE_ID, venueId).observe()

  override fun observeForReservation(reservationId: String): Flow<List<Completion>> =
      completions.whereEqualTo(RESERVATION_ID, reservationId).observe()

  /** Answers the completion with [id]; see [answer]. */
  override suspend fun approve(id: String): Result<Unit> =
      answer(id, CompletionStatus.APPROVED, rejectReason = null)

  /**
   * Answers the completion with [id]; see [answer]. A blank [reason] fails with
   * [IllegalArgumentException] before any request reaches the backend.
   */
  override suspend fun reject(id: String, reason: String): Result<Unit> {
    if (reason.isBlank()) {
      return Result.failure(IllegalArgumentException("A rejection needs a reason"))
    }
    return answer(id, CompletionStatus.REJECTED, reason)
  }

  /** Whether the server holds [document], or false if it can't tell in [confirmTimeoutMs]. */
  private suspend fun isStored(document: DocumentReference): Boolean =
      try {
        withTimeoutOrNull(confirmTimeoutMs) { document.get(Source.SERVER).await().exists() } == true
      } catch (e: FirebaseFirestoreException) {
        Log.w(TAG, "Reading completion ${document.id} failed", e)
        false
      }

  /**
   * Reads the completion and writes the venue's answer in one transaction, so the transition is
   * checked against the stored status.
   *
   * Only [status] and [rejectReason] are written, which is all the rules let the venue change; a
   * null [rejectReason] deletes the stored one. Needs a connection, as a transaction fails offline.
   *
   * Fails with [NoSuchElementException] if the completion is missing or malformed,
   * [IllegalStateException] if [canTransition] forbids the change, and the Firestore exception if
   * the rules refuse it.
   */
  private suspend fun answer(
      id: String,
      status: CompletionStatus,
      rejectReason: String?,
  ): Result<Unit> =
      try {
        val document = completions.document(id)
        val fields =
            mapOf(STATUS to status.name, REJECT_REASON to (rejectReason ?: FieldValue.delete()))
        // The transaction returns the reason for refusing instead of throwing it, so the caller
        // gets the exact exception rather than one wrapped by Firestore.
        val refusal =
            db.runTransaction<Exception?> { transaction ->
                  val current = transaction.get(document).toCompletion()
                  when {
                    current == null -> NoSuchElementException("No completion with id $id")
                    !canTransition(current.status, status) ->
                        IllegalStateException(
                            "A ${current.status} completion cannot become $status"
                        )
                    else -> {
                      transaction.update(document, fields)
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
   * Turns Firestore's callback-style listener into a Kotlin Flow of the completions, leaving out
   * those it can't map, which ViewModels can collect. The flow ends with the Firestore exception if
   * the backend stops the listener, e.g. when the rules refuse the query.
   */
  private fun Query.observe(): Flow<List<Completion>> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
      if (error != null) {
        close(error)
      } else if (snapshot != null) {
        trySend(snapshot.documents.mapNotNull { it.toCompletion() })
      }
    }
    awaitClose { registration.remove() }
  }

  /** The document field that holds this proof. */
  private fun Proof.toField(): Pair<String, String> =
      when (this) {
        is Proof.Photo -> PHOTO_URL to location
        is Proof.Text -> TEXT_ANSWER to answer
      }

  /** Maps this document to a [Completion], or returns null if it is missing or malformed. */
  private fun DocumentSnapshot.toCompletion(): Completion? {
    if (!exists()) return null
    return try {
      Completion(
          id = id,
          reservationId = required(RESERVATION_ID) { getString(it) },
          venueId = required(VENUE_ID) { getString(it) },
          explorerUid = required(EXPLORER_UID) { getString(it) },
          proof = toProof(),
          status = CompletionStatus.valueOf(required(STATUS) { getString(it) }),
          rejectReason = getString(REJECT_REASON),
          submittedAt = required(SUBMITTED_AT) { getTimestamp(it) }.toDate().time,
      )
    } catch (e: Exception) {
      Log.w(TAG, "Skipping malformed completion document $id", e)
      null
    }
  }

  /** Reads the proof of this document, throwing unless it holds exactly one. */
  private fun DocumentSnapshot.toProof(): Proof {
    val photoUrl = getString(PHOTO_URL)
    val textAnswer = getString(TEXT_ANSWER)
    return when {
      photoUrl != null && textAnswer == null -> Proof.Photo(photoUrl)
      textAnswer != null && photoUrl == null -> Proof.Text(textAnswer)
      else -> throw IllegalArgumentException("A completion needs exactly one proof")
    }
  }

  private companion object {
    const val TAG = "CompletionRepositoryFirestore"

    /** Long enough for the server to answer on a slow connection, short for a waiting explorer. */
    const val CONFIRM_TIMEOUT_MS = 5_000L

    const val COMPLETIONS = "completions"

    const val RESERVATION_ID = "reservationId"
    const val VENUE_ID = "venueId"
    const val EXPLORER_UID = "explorerUid"
    const val PHOTO_URL = "photoUrl"
    const val TEXT_ANSWER = "textAnswer"
    const val STATUS = "status"
    const val REJECT_REASON = "rejectReason"
    const val SUBMITTED_AT = "submittedAt"
  }
}
