// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.github.aroundteam2026.aroundapp.model.reservation.PROOF_PHOTO_LIMIT_BYTES
import java.io.File
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProofPhotosTest {
  private val app = ApplicationProvider.getApplicationContext<Application>()
  private val photos = ProofPhotos(app, Dispatchers.Unconfined)
  private val folder = File(app.cacheDir, "proofs").apply { mkdirs() }

  private fun shrink(source: Uri, with: ProofPhotos = photos) = runBlocking { with.shrink(source) }

  private fun discard(photo: Uri, with: ProofPhotos = photos) = runBlocking { with.discard(photo) }

  @Test
  fun aLargePhotoShrinksToTheMaximumEdgeKeepingItsShape() {
    assertEquals(2048 to 1536, sizeOf(shrink(testImage(app, 4000, 3000))))
  }

  @Test
  fun aTallPhotoShrinksAlongItsHeight() {
    assertEquals(1024 to 2048, sizeOf(shrink(testImage(app, 1500, 3000))))
  }

  @Test
  fun aSmallPhotoKeepsItsSize() {
    assertEquals(800 to 600, sizeOf(shrink(testImage(app, 800, 600))))
  }

  @Test
  fun theCopyIsAJpegInTheProofsFolderNotTheSource() {
    val source = testImage(app, 800, 600)

    val copy = shrink(source)

    assertNotEquals(source, copy)
    assertEquals(folder.canonicalFile, File(copy.path!!).canonicalFile.parentFile)
    assertEquals("image/jpeg", decodeBounds(copy).outMimeType)
  }

  // The case #330 is about: a photo the upload would refuse comes out small enough to send
  @Test
  fun aPhotoOverTheUploadLimitShrinksUnderIt() {
    val source = noisyImage(4000, 3000)
    assertTrue(File(source.path!!).length() >= PROOF_PHOTO_LIMIT_BYTES)

    val copy = File(shrink(source).path!!)

    assertTrue(copy.length() < PROOF_PHOTO_LIMIT_BYTES)
  }

  @Test
  fun aCopyStillTooBigFailsAndIsNotKept() {
    val tiny = ProofPhotos(app, Dispatchers.Unconfined, limitBytes = 1)
    val before = folder.listFiles().orEmpty().toSet()

    assertThrows(ProofPhotoTooBigException::class.java) {
      shrink(testImage(app, 80, 60), with = tiny)
    }
    assertEquals(before, folder.listFiles().orEmpty().toSet())
  }

  @Test
  fun somethingThatIsNotAnImageFailsWithIOException() {
    val text = Uri.fromFile(File(app.cacheDir, "not-a-photo.txt").apply { writeText("hello") })

    assertThrows(IOException::class.java) { shrink(text) }
    assertThrows(IOException::class.java) { runBlocking { photos.preview(text) } }
  }

  @Test
  fun aLargePhotoIsPreviewedAtThePreviewEdgeKeepingItsShape() {
    val preview = runBlocking { photos.preview(testImage(app, 3000, 4000)) }

    assertEquals(768 to 1024, preview.width to preview.height)
  }

  @Test
  fun aSmallPhotoIsPreviewedAtItsSize() {
    val preview = runBlocking { photos.preview(testImage(app, 800, 600)) }

    assertEquals(800 to 600, preview.width to preview.height)
  }

  @Test
  fun discardDeletesACopyOrACameraTargetOfTheProofsFolder() {
    val copy = shrink(testImage(app, 80, 60))
    val target = File(folder, "camera-shared.jpg").apply { writeText("photo") }
    val shared = Uri.parse("content://${ProofPhotos.authority(app)}/proofs/${target.name}")

    assertTrue(discard(copy))
    assertTrue(discard(shared))

    assertFalse(File(copy.path!!).exists())
    assertFalse(target.exists())
  }

  // Deleting touches the disk, so it mustn't run on the screen's thread
  @Test
  fun discardDeletesOnItsIoDispatcher() {
    val io = RecordingDispatcher()
    val target = File(folder, "camera-io.jpg").apply { writeText("photo") }

    assertTrue(discard(Uri.fromFile(target), ProofPhotos(app, io)))

    assertTrue(io.dispatched)
    assertFalse(target.exists())
  }

  // A failed compression would otherwise send an empty file as the proof
  @Test
  fun aCopyThatFailsToCompressFailsAndIsNotKept() {
    val source = testImage(app, 80, 60)
    val failing = ProofPhotos(app, Dispatchers.Unconfined, compress = { _, _ -> false })
    val before = folder.list().orEmpty().toSet()

    assertThrows(IOException::class.java) { shrink(source, failing) }

    assertEquals(before, folder.list().orEmpty().toSet())
  }

  @Test
  fun discardLeavesEveryOtherPhotoAlone() {
    val gallery = testImage(app, 80, 60)
    val elsewhere = File(folder, "kept.jpg").apply { writeText("photo") }

    assertFalse(discard(gallery))
    assertFalse(discard(Uri.parse("content://media/external/images/1")))
    assertFalse(discard(Uri.parse("content://other.app.files/proofs/kept.jpg")))
    assertFalse(discard(Uri.fromFile(File(folder, "../${File(gallery.path!!).name}"))))
    // Already gone: nothing to delete
    assertFalse(discard(Uri.fromFile(File(folder, "gone.jpg"))))

    assertTrue(File(gallery.path!!).exists())
    assertTrue(elsewhere.exists())
  }

  // The picker discards in a finally, where a throw would crash the screen
  @Test
  fun discardOfAPathThatCantBeResolvedDeletesNothingAndDoesNotThrow() {
    // Java can't resolve a path with a NUL in it: canonicalFile throws IOException
    assertFalse(discard(Uri.fromFile(File(folder, "bad\u0000.jpg"))))
  }

  // FileProvider matches paths with "/", so it can't find the cache under Windows' "\" paths: this
  // test runs on CI (Linux) and on Linux and macOS machines
  @Test
  fun eachCameraTargetIsANewFileSharedThroughTheProvider() {
    assumeFalse(File.separatorChar == '\\')
    val first = photos.newCameraTarget()
    val second = photos.newCameraTarget()

    assertEquals("content", first.scheme)
    assertEquals(ProofPhotos.authority(app), first.authority)
    assertNotEquals(first, second)
  }

  /**
   * A PNG over the upload limit, as an original photo from the camera: a gradient with grain, which
   * PNG can't pack small.
   */
  private fun noisyImage(width: Int, height: Int): Uri {
    val random = Random(42)
    val pixels =
        IntArray(width * height) { i ->
          val shade = (i % width) * 200 / width + random.nextInt(GRAIN)
          (0xFF shl 24) or (shade shl 16) or ((shade + random.nextInt(GRAIN)) shl 8) or shade
        }
    val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    val file = File.createTempFile("original", ".png", app.cacheDir)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    return Uri.fromFile(file)
  }

  private fun sizeOf(uri: Uri) = decodeBounds(uri).let { it.outWidth to it.outHeight }

  private fun decodeBounds(uri: Uri) =
      BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(uri.path, this)
      }

  private companion object {
    const val GRAIN = 24
  }
}

/** Runs what it's given at once, and records that it was given something. */
private class RecordingDispatcher : CoroutineDispatcher() {
  var dispatched = false

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    dispatched = true
    block.run()
  }
}

/** A plain PNG of [width] by [height] pixels in the cache, as a photo picked from the gallery. */
internal fun testImage(app: Application, width: Int, height: Int): Uri {
  val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
  val file = File.createTempFile("picked", ".png", app.cacheDir)
  file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  return Uri.fromFile(file)
}
