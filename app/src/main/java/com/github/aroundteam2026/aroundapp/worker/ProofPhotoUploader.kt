package com.github.aroundteam2026.aroundapp.worker

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Sends a proof photo that the app keeps in its own storage (#412) to remote storage. */
fun interface ProofPhotoUploader {
  /** Uploads [file] to [remotePath] and returns the remote path once it is stored. */
  suspend fun upload(file: File, remotePath: String): String
}

/** Uploads to Firebase Storage. */
class FirebaseProofPhotoUploader(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) : ProofPhotoUploader {
  override suspend fun upload(file: File, remotePath: String): String =
      suspendCancellableCoroutine { cont ->
        val task = storage.reference.child(remotePath).putFile(Uri.fromFile(file))
        task
            .addOnSuccessListener { cont.resume(remotePath) }
            .addOnFailureListener { cont.resumeWithException(it) }
        cont.invokeOnCancellation { task.cancel() }
      }
}
