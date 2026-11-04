// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
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
  fun somethingThatIsNotAnImageFailsWithIOException() {
    val text = File(app.cacheDir, "not-a-photo.txt").apply { writeText("hello") }

    assertThrows(IOException::class.java) { runBlocking { photos.preview(Uri.fromFile(text)) } }
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
}

/** A plain PNG of [width] by [height] pixels in the cache, as a photo picked from the gallery. */
internal fun testImage(app: Application, width: Int, height: Int): Uri {
  val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
  val file = File.createTempFile("picked", ".png", app.cacheDir)
  file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  return Uri.fromFile(file)
}
