// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException

/**
 * Whether [error], as [ProofPhotoRepository] or [CompletionRepository] return it, is the security
 * rules refusing the request, which fails the same way on every retry: Storage's
 * `ERROR_NOT_AUTHORIZED` or Firestore's `PERMISSION_DENIED`. Code outside `model/` can tell a
 * refusal from a failure that can pass without depending on Firebase.
 */
fun isRulesRefusal(error: Throwable): Boolean =
    when (error) {
      is StorageException -> error.errorCode == StorageException.ERROR_NOT_AUTHORIZED
      is FirebaseFirestoreException ->
          error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
      else -> false
    }
