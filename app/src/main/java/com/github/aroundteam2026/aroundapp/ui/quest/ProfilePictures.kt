// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest

import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import com.github.aroundteam2026.aroundapp.model.user.UserPictureRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The profile pictures of the party members a screen shows, as the venue's dashboard and the quest
 * details do. Each is downloaded through [repository] once, in [scope], and decoded by [decode];
 * until it arrives, or if that fails, the member shows their initials. A failed download is
 * forgotten, so the next [load] tries it again, since a download mostly fails for a passing reason
 * such as no network. Without a [repository], nothing is downloaded.
 *
 * Not thread-safe: call [load] from [scope]'s thread, as a ViewModel does from its own scope.
 */
internal class ProfilePictures(
    private val scope: CoroutineScope,
    private val repository: UserPictureRepository?,
    private val decode: suspend (ByteArray) -> ImageBitmap? = { decodeAvatar(it) },
) {
  private val mutablePictures = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())

  /** The pictures downloaded so far, keyed by their location. */
  val pictures: StateFlow<Map<String, ImageBitmap>> = mutablePictures.asStateFlow()

  /** The locations downloaded or being downloaded, so each is fetched once. */
  private val requested = HashSet<String>()

  /** Downloads each picture of [locations] that isn't downloaded or being downloaded yet. */
  fun load(locations: Collection<String>) {
    val repository = repository ?: return
    for (location in locations) {
      if (!requested.add(location)) continue
      scope.launch {
        repository
            .download(location)
            .mapCatching { checkNotNull(decode(it)) { "Not an image" } }
            .onSuccess { picture -> mutablePictures.update { it + (location to picture) } }
            .onFailure {
              requested.remove(location)
              Log.w(TAG, "Showing the profile picture $location failed", it)
            }
      }
    }
  }

  private companion object {
    const val TAG = "ProfilePictures"
  }
}
