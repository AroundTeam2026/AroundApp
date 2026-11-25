// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
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
 * Stores profile pictures under `{folder}/{uid}/{photoId}` using the shared image transfer.
 *
 * @param folder [USER_PICTURES] for users' pictures, or [VENUE_PICTURES] for venues', whose id is
 *   their owner's uid.
 */
class UserPictureRepositoryStorage(
    context: Context,
    storage: FirebaseStorage = Firebase.storage,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    folder: String = USER_PICTURES,
) : UserPictureRepository {
  private val transfer =
      ProofPhotoRepositoryStorage(
          context,
          storage,
          ioDispatcher = ioDispatcher,
          folder = folder,
      )

  override suspend fun upload(uid: String, photo: Uri): Result<String> = transfer.upload(uid, photo)

  override suspend fun download(location: String): Result<ByteArray> = transfer.download(location)

  companion object {
    /** The folder of users' profile pictures. */
    const val USER_PICTURES = "user-pictures"

    /** The folder of venues' profile pictures. */
    const val VENUE_PICTURES = "venue-pictures"
  }
}
