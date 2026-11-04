// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.CancellationException

/**
 * The proof photo of a photo quest, as the Figma "Photo" box of `S4 · Submit proof`: before a
 * photo, "Take photo" and "Choose from gallery"; once one is picked, the photo with "Retake".
 *
 * "Take photo" asks for the camera permission first. After a refusal, the box says so and offers
 * the phone's settings instead, and the gallery still works; back from the settings with the camera
 * allowed, "Take photo" returns. The gallery needs no permission: Android's photo picker shares
 * only the photo the explorer picks.
 *
 * @param venueName The venue the proof is for, named in the box's text.
 * @param photo The picked photo, as [onPhotoPicked] gave it, or null while there is none.
 * @param onPhotoPicked Receives a taken or picked photo.
 * @param onRetake Clears [photo], so the explorer can take or pick another one.
 * @param photos Gives the camera its file and draws the picked photo.
 */
@Composable
fun ProofPhotoPicker(
    venueName: String,
    photo: Uri?,
    onPhotoPicked: (Uri) -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier,
    photos: ProofPhotos = rememberProofPhotos(),
) {
  val context = LocalContext.current
  var cameraRefused by rememberSaveable { mutableStateOf(false) }
  // The file the camera app writes into, kept in case the activity is recreated while it's open
  var cameraTarget by rememberSaveable { mutableStateOf<String?>(null) }

  val camera =
      rememberLauncherForActivityResult(TakePicture()) { taken ->
        val target = cameraTarget
        cameraTarget = null
        if (taken && target != null) onPhotoPicked(target.toUri())
      }
  val gallery =
      rememberLauncherForActivityResult(PickVisualMedia()) { picked -> picked?.let(onPhotoPicked) }
  val openCamera = {
    val target = photos.newCameraTarget()
    cameraTarget = target.toString()
    try {
      camera.launch(target)
    } catch (_: ActivityNotFoundException) {
      // No app takes photos on this phone: nothing opens, and the gallery still works
      cameraTarget = null
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
  val preview by
      produceState<ImageBitmap?>(null, photo) {
        value =
            try {
              photo?.let { photos.preview(it) }
            } catch (cancelled: CancellationException) {
              throw cancelled
            } catch (_: Exception) {
              // The box still shows a photo is picked, without drawing it
              null
            }
      }

  ProofPhotoBox(
      venueName = venueName,
      picked = photo != null,
      preview = preview,
      cameraRefused = cameraRefused,
      onTakePhoto = {
        if (cameraAllowed(context)) openCamera()
        else askForCamera.launch(Manifest.permission.CAMERA)
      },
      onChooseFromGallery = { gallery.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
      onOpenSettings = {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            )
        )
      },
      onRetake = onRetake,
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
