// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * [NotificationTokenRepository] backed by the Firestore subcollection `users/{uid}/tokens`.
 *
 * The token is the document's id, which is what the Cloud Functions read, and its only field is
 * `updatedAt`, the server's time of the last save, so stale tokens can be found later. The rules
 * let only the user write and delete their own tokens, and nobody read them from the app.
 *
 * Each call returns once the server has answered, so while offline it waits for the connection.
 *
 * @param db Firestore client to write to.
 */
class NotificationTokenRepositoryFirestore(private val db: FirebaseFirestore) :
    NotificationTokenRepository {

  override suspend fun save(uid: String, token: String): Result<Unit> =
      write(uid, token) { set(mapOf(UPDATED_AT to FieldValue.serverTimestamp())).await() }

  override suspend fun delete(uid: String, token: String): Result<Unit> =
      write(uid, token) { delete().await() }

  /**
   * Runs [change] on the document of [token] under [uid], checking first that both are ids, so a
   * slash can't point it at another document. Returns any exception as a failure.
   */
  private suspend fun write(
      uid: String,
      token: String,
      change: suspend DocumentReference.() -> Unit,
  ): Result<Unit> =
      try {
        requireTokenPath(uid, token)
        db.collection(USERS).document(uid).collection(TOKENS).document(token).change()
        Result.success(Unit)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }

  private companion object {
    const val USERS = "users"
    const val TOKENS = "tokens"
    const val UPDATED_AT = "updatedAt"
  }
}
