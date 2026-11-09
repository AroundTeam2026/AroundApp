// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.model.user

import android.net.Uri

/** Transfers profile pictures without exposing Cloud Storage to a ViewModel. */
interface UserPictureRepository {
  /** Uploads a new picture for [uid] and returns its `gs://` location. */
  suspend fun upload(uid: String, photo: Uri): Result<String>

  /** Reads a previously uploaded picture for display; the returned bytes belong to the caller. */
  suspend fun download(location: String): Result<ByteArray>
}
