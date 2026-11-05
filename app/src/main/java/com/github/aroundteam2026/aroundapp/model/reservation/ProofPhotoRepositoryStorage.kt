// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.content.Context
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeoutException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [ProofPhotoRepository] backed by Cloud Storage, under `proofs/{reservationId}/{photoId}` as
 * `storage.rules` and the Cloud Storage section of `docs/firestore-schema.md` describe.
 *
 * Each photo gets a random UUID as its id, and its location is the `gs://` form of its reference.
 * Failures are returned as Storage's [StorageException], e.g. `ERROR_NOT_AUTHORIZED` when the rules
 * refuse, except a missing photo, which [download] returns as a [NoSuchElementException]. It keeps
 * only the application context, so it may outlive the screen that built it.
 *
 * @param context Any context; its application's `ContentResolver` reads the photos.
 * @param storage Cloud Storage client to upload to and download from.
 * @param transferTimeoutMs How long [upload] and [download] wait for Storage, in milliseconds,
 *   before they fail with a [TimeoutException].
 * @param ioDispatcher Where photos are read from the device.
 */
class ProofPhotoRepositoryStorage(
    context: Context,
    private val storage: FirebaseStorage = Firebase.storage,
    private val transferTimeoutMs: Long = TRANSFER_TIMEOUT_MS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ProofPhotoRepository {
  private val contentResolver = context.applicationContext.contentResolver

  /**
   * Reads [photo], at most up to the size limit, then uploads it with a bounded wait. On a timeout
   * or when the caller is cancelled, the upload is cancelled too, so it never finishes after the
   * caller gave up on it.
   */
  override suspend fun upload(reservationId: String, photo: Uri): Result<String> =
      try {
        require(reservationId.isNotBlank() && '/' !in reservationId) {
          "Invalid reservation id \"$reservationId\""
        }
        val bytes = read(photo)
        require(bytes.size < PROOF_PHOTO_LIMIT_BYTES) {
          "A proof photo must be under $PROOF_PHOTO_LIMIT_BYTES bytes"
        }
        // The rules require an image/ content type; some camera files have none
        val type = contentResolver.getType(photo) ?: DEFAULT_CONTENT_TYPE
        val reference = storage.reference.child("$PROOFS/$reservationId/${UUID.randomUUID()}")
        val task = reference.putBytes(bytes, StorageMetadata.Builder().setContentType(type).build())
        try {
          withTimeoutOrNull(transferTimeoutMs) { task.await() }
              ?: throw TimeoutException("Upload to $reference took over $transferTimeoutMs ms")
        } finally {
          // Cancelling a failed upload calls its listeners again, and await then throws instead of
          // the upload's own exception
          if (!task.isComplete) task.cancel()
        }
        Result.success(reference.toString())
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }

  /**
   * Downloads the photo with a bounded wait; Storage rejects one over the size limit. Unlike an
   * upload, the download isn't cancelled on a timeout: `getBytes` gives a task that can't be.
   */
  override suspend fun download(location: String): Result<ByteArray> =
      try {
        val bucket = "$GS_SCHEME${storage.reference.bucket}/"
        require(location.startsWith(bucket)) { "$location isn't in $bucket" }
        val task = storage.getReferenceFromUrl(location).getBytes(PROOF_PHOTO_LIMIT_BYTES.toLong())
        val bytes =
            withTimeoutOrNull(transferTimeoutMs) { task.await() }
                ?: throw TimeoutException("Download of $location took over $transferTimeoutMs ms")
        Result.success(bytes)
      } catch (e: StorageException) {
        if (e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) {
          Result.failure(
              NoSuchElementException("No proof photo at $location").apply { initCause(e) }
          )
        } else {
          Result.failure(e)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }

  /**
   * The bytes of [photo], or a [FileNotFoundException] if the app may no longer read it. Stops at
   * [PROOF_PHOTO_LIMIT_BYTES], so a photo that large or larger is refused without being loaded
   * whole.
   */
  private suspend fun read(photo: Uri): ByteArray =
      withContext(ioDispatcher) {
        val stream =
            contentResolver.openInputStream(photo)
                ?: throw FileNotFoundException("Can't open $photo")
        stream.use { it.readUntil(PROOF_PHOTO_LIMIT_BYTES) }
      }

  /** Reads this stream until it ends or at least [limit] bytes were read, whichever comes first. */
  private fun InputStream.readUntil(limit: Int): ByteArray {
    val bytes = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (bytes.size() < limit) {
      val read = read(buffer)
      if (read < 0) break
      bytes.write(buffer, 0, read)
    }
    return bytes.toByteArray()
  }

  private companion object {
    /** Folder of every proof photo, as `storage.rules` require. */
    const val PROOFS = "proofs"

    const val GS_SCHEME = "gs://"

    /** What the camera's photos are when the `ContentResolver` can't tell. */
    const val DEFAULT_CONTENT_TYPE = "image/jpeg"

    /** Long enough to upload a photo at the size limit on a slow mobile connection. */
    const val TRANSFER_TIMEOUT_MS = 60_000L
  }
}
