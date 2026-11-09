// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.model.user

import android.content.Context
import android.net.Uri
import com.github.aroundteam2026.aroundapp.model.reservation.ProofPhotoRepositoryStorage
import com.google.firebase.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Stores profile pictures under `user-pictures/{uid}/{photoId}` using the shared image transfer.
 */
class UserPictureRepositoryStorage(
    context: Context,
    storage: FirebaseStorage = Firebase.storage,
    transferTimeoutMs: Long = 60_000L,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UserPictureRepository {
  private val transfer =
      ProofPhotoRepositoryStorage(
          context,
          storage,
          transferTimeoutMs,
          ioDispatcher,
          "user-pictures",
      )

  override suspend fun upload(uid: String, photo: Uri): Result<String> = transfer.upload(uid, photo)

  override suspend fun download(location: String): Result<ByteArray> = transfer.download(location)
}
