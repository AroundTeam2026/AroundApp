// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import com.github.aroundteam2026.aroundapp.model.reservation.PROOF_PHOTO_LIMIT_BYTES
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A photo whose shrunk copy is still [PROOF_PHOTO_LIMIT_BYTES] or more, too big to upload. */
class ProofPhotoTooBigException : IOException("The proof photo is still too big once shrunk")

/**
 * Prepares proof photos on the phone, under the app's cache: a file the camera app writes a new
 * photo into, a smaller JPEG copy of a taken or picked photo, and a preview to show it.
 *
 * The copy keeps uploads under the [PROOF_PHOTO_LIMIT_BYTES] that `storage.rules` allow, and fast
 * on mobile data: a full-resolution photo from the camera or the gallery can be bigger than that.
 *
 * @param context Gives the cache folder and reads the photos.
 * @param io Where photos are decoded and written, off the main thread.
 * @param limitBytes A copy of this many bytes or more fails with [ProofPhotoTooBigException].
 * @param share Turns a file of the cache folder into the [Uri] the camera app writes to; the app's
 *   FileProvider unless a test needs a plain file.
 */
class ProofPhotos(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val limitBytes: Int = PROOF_PHOTO_LIMIT_BYTES,
    private val share: (File) -> Uri = {
      FileProvider.getUriForFile(context, authority(context), it)
    },
) {
  private val folder: File
    get() = File(context.cacheDir, FOLDER).apply { mkdirs() }

  /**
   * A new, empty file for the camera app to write a photo into. Other apps can only write to it
   * through the returned `content://` [Uri], which the app's FileProvider grants them.
   */
  fun newCameraTarget(): Uri = share(File(folder, "camera-${UUID.randomUUID()}.jpg"))

  /**
   * Writes a JPEG copy of [source] whose longer side is at most [MAX_EDGE] pixels, turned upright
   * if the photo says it was taken on its side. A smaller photo keeps its size.
   *
   * @return the copy, a `file://` [Uri] in the cache.
   * @throws ProofPhotoTooBigException if the copy is still too big to upload; it is deleted.
   * @throws IOException if [source] can't be read or isn't an image.
   */
  suspend fun shrink(source: Uri): Uri =
      withContext(io) {
        val bitmap = decode(source, MAX_EDGE)
        val copy = File(folder, "proof-${UUID.randomUUID()}.jpg")
        try {
          copy.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        } finally {
          bitmap.recycle()
        }
        if (copy.length() >= limitBytes) {
          copy.delete()
          throw ProofPhotoTooBigException()
        }
        Uri.fromFile(copy)
      }

  /**
   * [photo], at most [PREVIEW_EDGE] pixels on its longer side, to show on screen.
   *
   * @throws IOException if [photo] can't be read or isn't an image.
   */
  suspend fun preview(photo: Uri): ImageBitmap =
      withContext(io) { decode(photo, PREVIEW_EDGE).asImageBitmap() }

  /**
   * Deletes [photo] if it is one of this cache's files: a camera target or a shrunk copy. Anything
   * else, such as a photo from the gallery, is left alone.
   */
  fun discard(photo: Uri) {
    val file =
        when {
          photo.scheme == ContentResolver.SCHEME_FILE -> photo.path?.let(::File)
          photo.scheme == ContentResolver.SCHEME_CONTENT && photo.authority == authority(context) ->
              photo.lastPathSegment?.let { File(folder, it) }
          else -> null
        }
    if (file != null && file.canonicalFile.parentFile == folder.canonicalFile) file.delete()
  }

  /** Decodes [uri], scaled down so that its longer side is at most [maxEdge] pixels. */
  private fun decode(uri: Uri, maxEdge: Int): Bitmap =
      ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) {
          decoder,
          info,
          _ ->
        val scale = maxEdge.toFloat() / maxOf(info.size.width, info.size.height)
        if (scale < 1f) {
          decoder.setTargetSize(
              (info.size.width * scale).roundToInt().coerceAtLeast(1),
              (info.size.height * scale).roundToInt().coerceAtLeast(1),
          )
        }
        // A software bitmap, as a hardware one can't be compressed
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
      }

  companion object {
    /** The longest side of an uploaded proof photo, in pixels: sharp enough for the venue. */
    const val MAX_EDGE = 2048

    /** The longest side of the photo shown on screen, in pixels. */
    const val PREVIEW_EDGE = 1024

    private const val JPEG_QUALITY = 85
    private const val FOLDER = "proofs"

    /** The authority of the FileProvider the manifest declares for proof photos. */
    fun authority(context: Context) = "${context.packageName}.proofphotos"
  }
}
