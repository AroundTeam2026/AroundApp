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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A photo whose shrunk copy is still [PROOF_PHOTO_LIMIT_BYTES] or more, too big to upload. */
class ProofPhotoTooBigException : IOException("The proof photo is still too big once shrunk")

/**
 * Prepares proof photos on the phone, in the app's private storage: a file the camera app writes a
 * new photo into, a smaller JPEG copy of a taken or picked photo, and a preview to show it.
 *
 * Unlike the cache, which the system may clear at any time, app storage keeps the copy readable
 * until its proof is uploaded: while its screen is open for now, and as groundwork for uploading it
 * later (#413). It is never cleared for the app, so a copy is deleted with [discard] once its proof
 * is submitted, and [discardStale] deletes the ones left behind. A screen that uploads at once, as
 * the profile does, passes the cache as [root] instead, which the system clears.
 *
 * The copy keeps uploads under the [PROOF_PHOTO_LIMIT_BYTES] that `storage.rules` allow, and fast
 * on mobile data: a full-resolution photo from the camera or the gallery can be bigger than that.
 *
 * @param context Reads the photos.
 * @param root Holds the photos' folder: app storage by default. The FileProvider only shares camera
 *   targets there, so another [root], such as the cache, is for screens that don't use the camera.
 * @param io Where photos are decoded and written, off the main thread.
 * @param limitBytes A copy of this many bytes or more fails with [ProofPhotoTooBigException].
 * @param share Turns a file of the photos' folder into the [Uri] the camera app writes to; the
 *   app's FileProvider unless a test needs a plain file.
 * @param compress Writes a bitmap as a JPEG and tells whether it could; [Bitmap.compress] unless a
 *   test needs it to fail.
 */
class ProofPhotos(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val limitBytes: Int = PROOF_PHOTO_LIMIT_BYTES,
    private val share: (File) -> Uri = {
      FileProvider.getUriForFile(context, authority(context), it)
    },
    private val compress: (Bitmap, OutputStream) -> Boolean = { bitmap, out ->
      bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    },
    private val root: File = context.filesDir,
) {
  private val folder: File
    get() = File(root, FOLDER).apply { mkdirs() }

  /**
   * A new, empty file for the camera app to write a photo into. Other apps can only write to it
   * through the returned `content://` [Uri], which the app's FileProvider grants them.
   */
  fun newCameraTarget(): Uri = share(File(folder, "camera-${UUID.randomUUID()}.jpg"))

  /**
   * Writes a JPEG copy of [source] whose longer side is at most [MAX_EDGE] pixels, turned upright
   * if the photo says it was taken on its side. A smaller photo keeps its size.
   *
   * @return the copy, a `file://` [Uri] in the app's storage.
   * @throws ProofPhotoTooBigException if the copy is still too big to upload; nothing is written.
   * @throws IOException if [source] can't be read or isn't an image, or the copy can't be
   *   compressed; nothing is written.
   */
  suspend fun shrink(source: Uri): Uri =
      withContext(io) {
        val bitmap = decode(source, MAX_EDGE)
        // Compressed in memory first, so a copy too big is never written
        val jpeg = ByteArrayOutputStream()
        val compressed =
            try {
              compress(bitmap, jpeg)
            } finally {
              bitmap.recycle()
            }
        // A failed compression leaves the buffer empty or cut short, which must never be the proof
        if (!compressed) throw IOException("The proof photo couldn't be compressed")
        if (jpeg.size() >= limitBytes) throw ProofPhotoTooBigException()
        val copy = File(folder, "proof-${UUID.randomUUID()}.jpg")
        copy.outputStream().use(jpeg::writeTo)
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
   * Deletes [photo] if it is one of this folder's files: a camera target or a shrunk copy. Anything
   * else, such as a photo from the gallery, is left alone. Runs on [io], off the main thread, as
   * deleting touches the disk.
   *
   * @return whether a file was deleted; false too when [photo]'s path can't even be resolved, as a
   *   cleanup that fails must not crash the screen.
   */
  suspend fun discard(photo: Uri): Boolean =
      withContext(io) {
        val file =
            when {
              photo.scheme == ContentResolver.SCHEME_FILE -> photo.path?.let(::File)
              photo.scheme == ContentResolver.SCHEME_CONTENT &&
                  photo.authority == authority(context) ->
                  photo.lastPathSegment?.let { File(folder, it) }
              else -> null
            }
        try {
          file != null && file.canonicalFile.parentFile == folder.canonicalFile && file.delete()
        } catch (_: IOException) {
          false
        }
      }

  /**
   * Deletes the camera targets and copies last written more than [STALE_AFTER_MILLIS] before [now]:
   * ones the app was closed on, or that a crash left behind. A photo still being used is younger.
   * Runs on [io]; a file that can't be deleted is left for next time.
   *
   * @return how many files were deleted.
   */
  suspend fun discardStale(now: Long = System.currentTimeMillis()): Int =
      withContext(io) {
        folder.listFiles().orEmpty().count {
          now - it.lastModified() > STALE_AFTER_MILLIS && it.delete()
        }
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

    /**
     * How long a camera target or copy is kept before [discardStale] deletes it, 7 days. For now a
     * proof's photo is only kept while its screen is open, so this only clears what a closed app
     * left behind; 7 days leave room for a later upload of a proof waiting for a connection (#413).
     */
    const val STALE_AFTER_MILLIS = 7L * 24 * 60 * 60 * 1000

    private const val JPEG_QUALITY = 85
    private const val FOLDER = "proofs"

    /** The authority of the FileProvider the manifest declares for proof photos. */
    fun authority(context: Context) = "${context.packageName}.proofphotos"
  }
}
