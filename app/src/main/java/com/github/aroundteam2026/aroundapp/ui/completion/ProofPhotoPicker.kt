// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The proof photo of a photo quest, as the Figma "Photo" box of `S4 · Submit proof`: "Take photo"
 * and "Choose from gallery" before a photo, then the photo with "Retake".
 *
 * "Take photo" asks for the camera permission first; after a refusal, the box offers the phone's
 * settings, and "Take photo" returns once the camera is allowed there. The gallery needs no
 * permission and always works. Every photo goes through [ProofPhotos.shrink] before [onPhotoPicked]
 * gets it, and the camera's original and a retaken copy are deleted from the cache, off the main
 * thread. A picked photo that can't be shown is dropped through [onRetake], and the box says why.
 *
 * @param venueName The venue the proof is for, named in the box's text.
 * @param photo The picked photo, as [onPhotoPicked] gave it, or null while there is none.
 * @param onPhotoPicked Receives the shrunk copy of a taken or picked photo.
 * @param onRetake Clears [photo], so the explorer can take or pick another one.
 * @param photos Prepares the photos.
 */
@Composable
fun ProofPhotoPicker(
    venueName: String,
    photo: Uri?,
    onPhotoPicked: (Uri) -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier,
    photos: ProofPhotos = rememberProofPhotos(),
    enabled: Boolean = true,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var cameraRefused by rememberSaveable { mutableStateOf(false) }
  var error by rememberSaveable { mutableStateOf<ProofPhotoError?>(null) }
  var shrinking by remember { mutableStateOf(false) }
  // The file the camera app writes into, kept in case the activity is recreated while it's open
  var cameraTarget by rememberSaveable { mutableStateOf<String?>(null) }

  val usePhoto: (Uri) -> Unit = { source ->
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
        // The camera's original, whether or not the copy worked; a photo from the gallery is not
        // ours
        // to delete
        photos.discard(source)
        shrinking = false
      }
    }
  }
  val camera =
      rememberLauncherForActivityResult(TakePicture()) { taken ->
        val target = cameraTarget?.toUri()
        cameraTarget = null
        if (target != null) {
          if (taken) usePhoto(target) else scope.launch { photos.discard(target) }
        }
      }
  val gallery =
      rememberLauncherForActivityResult(PickVisualMedia()) { picked -> picked?.let(usePhoto) }
  val openCamera = {
    val target = photos.newCameraTarget()
    cameraTarget = target.toString()
    try {
      camera.launch(target)
    } catch (_: ActivityNotFoundException) {
      cameraTarget = null
      scope.launch { photos.discard(target) }
      error = ProofPhotoError.NO_CAMERA
    }
  }
  val askForCamera =
      rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) openCamera() else cameraRefused = true
      }
  // Back from the phone's settings, a camera allowed there brings "Take photo" back
  LifecycleResumeEffect(Unit) {
    if (cameraRefused && cameraAllowed(context)) cameraRefused = false
    onPauseOrDispose {}
  }
  val currentOnRetake by rememberUpdatedState(onRetake)
  val preview by
      produceState<ImageBitmap?>(null, photo) {
        value =
            try {
              photo?.let { photos.preview(it) }
            } catch (cancelled: CancellationException) {
              throw cancelled
            } catch (_: Exception) {
              // A copy that can't even be shown isn't sent: it's dropped, and the box says why,
              // instead of staying empty as if it were still loading
              photo?.let { photos.discard(it) }
              error = ProofPhotoError.UNREADABLE
              currentOnRetake()
              null
            }
      }

  ProofPhotoBox(
      venueName = venueName,
      picked = photo != null,
      preview = preview,
      cameraRefused = cameraRefused,
      error = error,
      enabled = enabled && !shrinking,
      onTakePhoto = {
        error = null
        if (cameraAllowed(context)) openCamera()
        else askForCamera.launch(Manifest.permission.CAMERA)
      },
      onChooseFromGallery = {
        error = null
        gallery.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
      },
      onOpenSettings = {
        try {
          context.startActivity(
              Intent(
                  Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                  Uri.fromParts("package", context.packageName, null),
              )
          )
        } catch (_: ActivityNotFoundException) {
          // As for a missing camera app, the gallery stays the way out
          error = ProofPhotoError.NO_SETTINGS
        }
      },
      onRetake = {
        photo?.let { scope.launch { photos.discard(it) } }
        onRetake()
      },
      modifier = modifier,
  )
}

/** A [ProofPhotos] for the app's context, kept across recompositions. */
@Composable
private fun rememberProofPhotos(): ProofPhotos {
  val context = LocalContext.current.applicationContext
  return remember(context) { ProofPhotos(context) }
}

private fun cameraAllowed(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
