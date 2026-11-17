// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What [ProofPhotoPicker] decides about its photos, so each flow can be tested without the
 * composable: shrinking a taken or picked photo, turning each failure into [error], the file the
 * camera app writes into, whether the camera was refused, and deleting the photos that are ours.
 * The picker only wires the camera, the gallery, the permission request and the settings to it.
 *
 * @param photos Prepares and deletes the photos.
 * @param scope Runs the shrinking and the deletes; the picker's, so leaving the screen cancels a
 *   shrink, though never a delete.
 */
@Stable
internal class ProofPhotoPickerState(
    private val photos: ProofPhotos,
    private val scope: CoroutineScope,
    cameraRefused: Boolean = false,
    error: ProofPhotoError? = null,
    cameraTarget: Uri? = null,
) {
  /** Whether the camera permission was refused, so the box offers the phone's settings. */
  var cameraRefused by mutableStateOf(cameraRefused)
    private set

  /** Why the last photo couldn't be used, or null. */
  var error by mutableStateOf(error)
    private set

  /** Whether a photo is being shrunk; the box is disabled meanwhile. */
  var shrinking by mutableStateOf(false)
    private set

  /**
   * The file the camera app writes into while it's open, kept in case the activity is recreated.
   */
  private var cameraTarget: Uri? = cameraTarget

  /**
   * Shrinks [source] and gives the copy to [onPhotoPicked], or says in [error] why it couldn't.
   * [source] is then deleted if it is ours, the camera's original; a photo from the gallery isn't.
   */
  fun use(source: Uri, onPhotoPicked: (Uri) -> Unit) {
    shrinking = true
    error = null
    scope.launch {
      try {
        onPhotoPicked(photos.shrink(source))
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: ProofPhotoTooBigException) {
        error = ProofPhotoError.TOO_BIG
      } catch (_: Exception) {
        error = ProofPhotoError.UNREADABLE
      } finally {
        shrinking = false
        // Even once the screen is gone, which cancels this coroutine
        withContext(NonCancellable) { photos.discard(source) }
      }
    }
  }

  /** A new file for the camera app to write into, kept until [onCameraResult]. */
  fun newCameraTarget(): Uri = photos.newCameraTarget().also { cameraTarget = it }

  /** The camera app closed: uses its photo if one was [taken], or else deletes the empty file. */
  fun onCameraResult(taken: Boolean, onPhotoPicked: (Uri) -> Unit) {
    val target = cameraTarget ?: return
    cameraTarget = null
    if (taken) use(target, onPhotoPicked) else scope.launch { photos.discard(target) }
  }

  /** No app could take the photo: deletes the empty file, and says so. */
  fun noCamera() {
    cameraTarget?.let { scope.launch { photos.discard(it) } }
    cameraTarget = null
    error = ProofPhotoError.NO_CAMERA
  }

  /** The camera permission was refused. */
  fun refuseCamera() {
    cameraRefused = true
  }

  /** Back on screen: a camera [allowed] meanwhile, e.g. in the phone's settings, brings it back. */
  fun onResumed(allowed: Boolean) {
    if (allowed) cameraRefused = false
  }

  /** The phone has no screen for the app's settings. */
  fun noSettings() {
    error = ProofPhotoError.NO_SETTINGS
  }

  /** Clears [error], before the explorer tries again. */
  fun clearError() {
    error = null
  }

  /** Deletes [photo], the copy the explorer is about to retake. */
  fun retake(photo: Uri?) {
    photo?.let { scope.launch { photos.discard(it) } }
  }

  /**
   * [photo]'s preview, or null if there is none. A copy that can't even be shown isn't sent: it's
   * deleted, [error] says why, and [onUnreadable] drops it.
   */
  suspend fun preview(photo: Uri?, onUnreadable: () -> Unit): ImageBitmap? =
      try {
        photo?.let { photos.preview(it) }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        photo?.let { photos.discard(it) }
        error = ProofPhotoError.UNREADABLE
        onUnreadable()
        null
      }

  /** Deletes the photos left behind, as app storage is never cleared for the app. */
  suspend fun discardStale() {
    photos.discardStale()
  }

  companion object {
    /**
     * Keeps what the explorer would lose if the activity were recreated, e.g. by the camera app.
     */
    fun saver(photos: ProofPhotos, scope: CoroutineScope): Saver<ProofPhotoPickerState, Any> =
        listSaver(
            save = { listOf(it.cameraRefused, it.error?.name, it.cameraTarget?.toString()) },
            restore = {
              ProofPhotoPickerState(
                  photos,
                  scope,
                  cameraRefused = it[0] as Boolean,
                  error = (it[1] as String?)?.let(ProofPhotoError::valueOf),
                  cameraTarget = (it[2] as String?)?.toUri(),
              )
            },
        )
  }
}

/** A [ProofPhotoPickerState] for [photos], saved across recreation. */
@Composable
internal fun rememberProofPhotoPickerState(photos: ProofPhotos): ProofPhotoPickerState {
  val scope = rememberCoroutineScope()
  return rememberSaveable(photos, saver = ProofPhotoPickerState.saver(photos, scope)) {
    ProofPhotoPickerState(photos, scope)
  }
}
