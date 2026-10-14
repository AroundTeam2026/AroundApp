// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.user

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
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
   * Checks the uid is free and writes in one transaction, so of two creations with the same uid
   * only one succeeds. Needs a connection: a transaction fails while the device is offline.
   */
  override suspend fun createUser(user: User): Result<Unit> =
      write(
          user.uid,
          refusal = { stored ->
            if (stored.exists()) IllegalStateException("User ${user.uid} already exists") else null
          },
      ) { transaction, document ->
        transaction.set(document, user.toFirestore())
      }

  /**
   * Checks the stored role and writes only `role` in one transaction, so of two devices choosing at
   * once only one succeeds and the other fails with [IllegalStateException]. The stored role is
   * read on its own, so a profile whose other fields are malformed still keeps its role. Needs a
   * connection: a transaction fails while the device is offline.
   */
  override suspend fun setRole(uid: String, role: Role): Result<Unit> =
      write(
          uid,
          refusal = { stored ->
            when {
              !stored.exists() -> IllegalArgumentException("No user with uid $uid")
              stored.get(ROLE) != null -> IllegalStateException("User $uid already has a role")
              else -> null
            }
          },
      ) { transaction, document ->
        transaction.update(document, ROLE, role.name)
      }

  /**
   * Reads the profile of [uid] and, unless [refusal] returns a reason to refuse, writes it with
   * [apply], in one transaction. The caller gets [refusal]'s exception as it is, rather than one
   * wrapped by Firestore, and any other exception as a failure.
   *
   * A write that lost a race can be refused by the rules rather than retried: the server checks the
   * rules against the winner's profile, which e.g. no longer lets the role change, and answers
   * `PERMISSION_DENIED`. The profile is then read again, and if [refusal] now refuses it, that
   * reason is returned instead, so the loser learns what the winner did.
   */
  private suspend fun write(
      uid: String,
      refusal: (DocumentSnapshot) -> Exception?,
      apply: (Transaction, DocumentReference) -> Unit,
  ): Result<Unit> {
    val document = users.document(uid)
    return try {
      val refused =
          db.runTransaction<Exception?> { transaction ->
                refusal(transaction.get(document)).also {
                  if (it == null) apply(transaction, document)
                }
              }
              .await()
      if (refused == null) Result.success(Unit) else Result.failure(refused)
    } catch (e: CancellationException) {
      throw e
    } catch (e: FirebaseFirestoreException) {
      val lostRace =
          if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) null
          else
              try {
                refusal(document.get(Source.SERVER).await())
              } catch (_: FirebaseFirestoreException) {
                null
              }
      Result.failure(lostRace ?: e)
    } catch (e: Exception) {
      Result.failure(e)
    }
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
