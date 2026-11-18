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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * The proof photo of a photo quest, as the Figma "Photo" box of `S4 · Submit proof`: "Take photo"
 * and "Choose from gallery" before a photo, then the photo with "Retake".
 *
 * "Take photo" asks for the camera permission first; after a refusal, the box offers the phone's
 * settings, and "Take photo" returns once the camera is allowed there. The gallery needs no
 * permission and always works. Every photo goes through [ProofPhotos.shrink] before [onPhotoPicked]
 * gets it, and the camera's original and a retaken copy are deleted, off the main thread. A picked
 * photo that can't be shown is dropped through [onRetake], and the box says why. Those decisions
 * are [ProofPhotoPickerState]'s; this only wires the launchers and the box to it.
 *
 * @param venueName The venue the proof is for, named in the box's text.
 * @param photo The picked photo, as [onPhotoPicked] gave it, or null while there is none.
 * @param onPhotoPicked Receives the shrunk copy of a taken or picked photo.
 * @param onRetake Clears [photo], so the explorer can take or pick another one.
 * @param photos Prepares the photos.
 * @param questTitle The quest the photo is for, said when the photo is opened full screen.
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
    questTitle: String = "",
) {
  val context = LocalContext.current
  val state = rememberProofPhotoPickerState(photos)
  LaunchedEffect(state) { state.discardStale() }
  val camera =
      rememberLauncherForActivityResult(TakePicture()) { taken ->
        state.onCameraResult(taken, onPhotoPicked)
      }
  val gallery =
      rememberLauncherForActivityResult(PickVisualMedia()) { picked ->
        picked?.let { state.use(it, onPhotoPicked) }
      }
  val openCamera = {
    try {
      camera.launch(state.newCameraTarget())
    } catch (_: ActivityNotFoundException) {
      state.noCamera()
    }
  }
  val askForCamera =
      rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) openCamera() else state.refuseCamera()
      }
  // Back from the phone's settings, a camera allowed there brings "Take photo" back
  LifecycleResumeEffect(Unit) {
    state.onResumed(cameraAllowed(context))
    onPauseOrDispose {}
  }
  val currentOnRetake by rememberUpdatedState(onRetake)
  val preview by
      produceState<ImageBitmap?>(null, photo) { value = state.preview(photo) { currentOnRetake() } }

  ProofPhotoBox(
      venueName = venueName,
      picked = photo != null,
      preview = preview,
      cameraRefused = state.cameraRefused,
      error = state.error,
      enabled = enabled && !state.shrinking,
      questTitle = questTitle,
      onTakePhoto = {
        state.clearError()
        if (cameraAllowed(context)) openCamera()
        else askForCamera.launch(Manifest.permission.CAMERA)
      },
      onChooseFromGallery = {
        state.clearError()
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
          state.noSettings()
        }
      },
      onRetake = {
        state.retake(photo)
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
