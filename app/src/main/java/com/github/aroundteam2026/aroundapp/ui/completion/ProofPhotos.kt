// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The files behind proof photos, under the app's cache: a new file for the camera app to write a
 * photo into, and a preview of a taken or picked photo to show on screen.
 *
 * @param context Gives the cache folder and reads the photos.
 * @param io Where photos are decoded, off the main thread.
 * @param share Turns a file of the cache folder into the [Uri] the camera app writes to; the app's
 *   FileProvider unless a test needs a plain file.
 */
class ProofPhotos(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val share: (File) -> Uri = {
      FileProvider.getUriForFile(context, authority(context), it)
    },
) {
  /**
   * A new, empty file for the camera app to write a photo into. Other apps can only write to it
   * through the returned `content://` [Uri], which the app's FileProvider grants them.
   */
  fun newCameraTarget(): Uri {
    val folder = File(context.cacheDir, FOLDER).apply { mkdirs() }
    return share(File(folder, "camera-${UUID.randomUUID()}.jpg"))
  }

  /**
   * [photo], at most [PREVIEW_EDGE] pixels on its longer side, to show on screen. Turned upright if
   * the photo says it was taken on its side.
   *
   * @throws java.io.IOException if [photo] can't be read or isn't an image.
   */
  suspend fun preview(photo: Uri): ImageBitmap =
      withContext(io) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, photo)) {
                decoder,
                info,
                _ ->
              val scale = PREVIEW_EDGE.toFloat() / maxOf(info.size.width, info.size.height)
              if (scale < 1f) {
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
              }
            }
            .asImageBitmap()
      }

  companion object {
    /** The longest side of the photo shown on screen, in pixels. */
    const val PREVIEW_EDGE = 1024

    private const val FOLDER = "proofs"

    /** The authority of the FileProvider the manifest declares for proof photos. */
    fun authority(context: Context) = "${context.packageName}.proofphotos"
  }
}
