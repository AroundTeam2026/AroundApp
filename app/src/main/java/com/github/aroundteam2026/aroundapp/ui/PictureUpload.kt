// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui

import android.net.Uri
import com.github.aroundteam2026.aroundapp.model.user.UserPictureRepository

/**
 * Uploads the picture a form saves, once per picked picture: a save retried after a failed write
 * reuses the upload instead of uploading the same picture again.
 *
 * Not thread-safe: use it from one coroutine at a time, as a ViewModel's single save does.
 */
internal class PictureUpload(private val pictures: UserPictureRepository) {
  private var uploaded: Pair<Uri, String>? = null

  /**
   * Where the picture to save is stored: none when [remove], [photo]'s location once uploaded for
   * [uid], or else the [saved] one, unchanged. Throws if the upload fails.
   */
  suspend fun locationOf(uid: String, photo: Uri?, remove: Boolean, saved: String?): String? =
      when {
        remove -> null
        photo != null ->
            uploaded?.takeIf { it.first == photo }?.second
                ?: pictures.upload(uid, photo).getOrThrow().also { uploaded = photo to it }
        else -> saved
      }
}
