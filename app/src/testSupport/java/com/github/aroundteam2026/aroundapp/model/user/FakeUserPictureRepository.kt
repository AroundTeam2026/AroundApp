// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.model.user

import android.net.Uri

/** In-memory [UserPictureRepository] for tests without Cloud Storage. */
class FakeUserPictureRepository(private val read: (Uri) -> ByteArray = { ByteArray(0) }) :
    UserPictureRepository {
  private val pictures = mutableMapOf<String, ByteArray>()
  private var nextId = 1
  var failure: Throwable? = null

  override suspend fun upload(uid: String, photo: Uri): Result<String> {
    if (uid.isBlank() || '/' in uid) {
      return Result.failure(IllegalArgumentException("Invalid user id"))
    }
    failure?.let {
      return Result.failure(it)
    }
    val location = "gs://fake-bucket/user-pictures/$uid/${nextId++}"
    pictures[location] = read(photo).copyOf()
    return Result.success(location)
  }

  override suspend fun download(location: String): Result<ByteArray> {
    failure?.let {
      return Result.failure(it)
    }
    return pictures[location]?.let { Result.success(it.copyOf()) }
        ?: Result.failure(NoSuchElementException("No picture at $location"))
  }
}
