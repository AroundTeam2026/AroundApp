// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.user

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import java.util.Date
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

/**
 * [UserRepository] backed by the Firestore `users` collection.
 *
 * Firestore types stay inside this class. [User.createdAt] is stored as a `Timestamp` and the role
 * as its enum name, omitted while null. The uid is the document id rather than a stored field.
 * Documents that cannot be mapped back to a [User] are logged and read as missing.
 *
 * The security rules let a user read and write only their own profile, so reading or writing
 * another uid's fails with the Firestore exception, and the role, once set, never changes.
 *
 * @param db Firestore client to read from and write to.
 */
class UserRepositoryFirestore(private val db: FirebaseFirestore) : UserRepository {
  private val users
    get() = db.collection(USERS)

  /**
   * The document is built once the flow is collected, so an id Firestore refuses fails the flow
   * instead of throwing here.
   */
  override fun observeUser(uid: String): Flow<User?> = flow {
    emitAll(users.document(uid).observe())
  }

  override suspend fun getUser(uid: String): User? = users.document(uid).get().await().toUser()

  /**
   * Reads and writes in one transaction, so of two creations with the same uid only one succeeds.
   * Needs a connection: a transaction fails while the device is offline.
   */
  override suspend fun createUser(user: User): Result<Unit> = transaction {
    val document = users.document(user.uid)
    if (it.get(document).exists()) {
      IllegalStateException("User ${user.uid} already exists")
    } else {
      it.set(document, user.toFirestore())
      null
    }
  }

  override suspend fun setRole(uid: String, role: Role): Result<Unit> =
      TODO("Set in the next commit")

  /**
   * Runs [block] in a transaction. [block] returns the reason for refusing instead of throwing it,
   * so the caller gets that exact exception rather than one wrapped by Firestore. Other exceptions,
   * e.g. the rules refusing the write, are returned as a failure too.
   */
  private suspend fun transaction(block: (Transaction) -> Exception?): Result<Unit> =
      try {
        val refusal = db.runTransaction<Exception?> { block(it) }.await()
        if (refusal == null) Result.success(Unit) else Result.failure(refusal)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }

  /** Turns Firestore's callback-style listener into a Flow of this document's profile. */
  private fun DocumentReference.observe(): Flow<User?> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
      if (error != null) {
        close(error)
      } else if (snapshot != null) {
        trySend(snapshot.toUser())
      }
    }
    awaitClose { registration.remove() }
  }

  /** The document fields for this profile, without its uid and its null role. */
  private fun User.toFirestore(): Map<String, Any> = buildMap {
    put(DISPLAY_NAME, displayName)
    role?.let { put(ROLE, it.name) }
    put(CREATED_AT, Timestamp(Date(createdAt)))
  }

  /** Maps this document to a [User], or returns null if it is missing or malformed. */
  private fun DocumentSnapshot.toUser(): User? {
    if (!exists()) return null
    return try {
      User(
          uid = id,
          displayName = required(DISPLAY_NAME) { getString(it) },
          role = getString(ROLE)?.let(Role::valueOf),
          createdAt = required(CREATED_AT) { getTimestamp(it) }.toDate().time,
      )
    } catch (e: Exception) {
      Log.w(TAG, "Skipping malformed user document $id", e)
      null
    }
  }

  /** Reads [field] with [read], throwing if it is absent. */
  private fun <T : Any> required(field: String, read: (String) -> T?): T =
      read(field) ?: throw IllegalArgumentException("Missing field $field")

  private companion object {
    const val TAG = "UserRepositoryFirestore"
    const val USERS = "users"

    const val DISPLAY_NAME = "displayName"
    const val ROLE = "role"
    const val CREATED_AT = "createdAt"
  }
}
