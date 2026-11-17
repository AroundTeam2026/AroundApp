// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.saveable.SaverScope
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The picker's decisions through [ProofPhotoPickerState], without the composable. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProofPhotoPickerStateTest {
  private val app = ApplicationProvider.getApplicationContext<Application>()
  private val scope = CoroutineScope(Dispatchers.Unconfined)
  // Camera targets as plain files, so a test can write the "taken" photo into them
  private val photos = ProofPhotos(app, Dispatchers.Unconfined, share = Uri::fromFile)
  private val state = ProofPhotoPickerState(photos, scope)
  private val picked = mutableListOf<Uri>()

  private fun Uri.file() = File(path!!)

  @Test
  fun aPickedPhotoIsShrunkAndGivenToThePicker() {
    val source = testImage(app, 80, 60)

    state.use(source) { picked += it }

    assertEquals(File(app.filesDir, "proofs"), picked.single().file().parentFile)
    assertNull(state.error)
    assertFalse(state.shrinking)
    // A photo from the gallery isn't ours to delete
    assertTrue(source.file().exists())
  }

  @Test
  fun somethingThatIsNotAPhotoSaysItIsUnreadable() {
    val text = Uri.fromFile(File(app.cacheDir, "not-a-photo.txt").apply { writeText("hello") })

    state.use(text) { picked += it }

    assertEquals(ProofPhotoError.UNREADABLE, state.error)
    assertEquals(emptyList<Uri>(), picked)
  }

  @Test
  fun aPhotoStillTooBigSaysSo() {
    val tiny = ProofPhotos(app, Dispatchers.Unconfined, limitBytes = 1)
    val tooBig = ProofPhotoPickerState(tiny, scope)

    tooBig.use(testImage(app, 80, 60)) { picked += it }

    assertEquals(ProofPhotoError.TOO_BIG, tooBig.error)
  }

  @Test
  fun aTakenPhotoIsUsedAndTheCamerasOriginalDeleted() {
    val target = state.newCameraTarget()
    testImage(app, 80, 60).file().copyTo(target.file(), overwrite = true)

    state.onCameraResult(taken = true) { picked += it }

    assertEquals(1, picked.size)
    assertFalse(target.file().exists())
  }

  @Test
  fun cancellingTheCameraDeletesItsEmptyFile() {
    val target = state.newCameraTarget().file().apply { writeText("") }

    state.onCameraResult(taken = false) { picked += it }

    assertFalse(target.exists())
    assertEquals(emptyList<Uri>(), picked)
  }

  @Test
  fun noCameraAppDeletesTheTargetAndSaysSo() {
    val target = state.newCameraTarget().file().apply { writeText("") }

    state.noCamera()

    assertFalse(target.exists())
    assertEquals(ProofPhotoError.NO_CAMERA, state.error)
  }

  // The camera app can make the system recreate the activity while it's open
  @Test
  fun theCameraTargetAndErrorSurviveRecreation() {
    val saver = ProofPhotoPickerState.saver(photos, scope)
    val target = state.newCameraTarget()
    testImage(app, 80, 60).file().copyTo(target.file(), overwrite = true)
    state.refuseCamera()
    state.noSettings()

    val saved = with(saver) { SaverScope { true }.save(state) }!!
    val restored = saver.restore(saved)!!

    assertTrue(restored.cameraRefused)
    assertEquals(ProofPhotoError.NO_SETTINGS, restored.error)
    restored.onCameraResult(taken = true) { picked += it }
    assertEquals(1, picked.size)
  }

  @Test
  fun aRefusedCameraComesBackOnlyOnceAllowed() {
    state.refuseCamera()

    state.onResumed(allowed = false)
    assertTrue(state.cameraRefused)
    state.onResumed(allowed = true)
    assertFalse(state.cameraRefused)
  }

  @Test
  fun aCopyThatCantBeShownIsDeletedAndDropped() {
    val broken = File(File(app.filesDir, "proofs").apply { mkdirs() }, "proof-broken.jpg")
    broken.writeText("not a photo")
    var dropped = 0

    val preview = runBlocking { state.preview(Uri.fromFile(broken)) { dropped++ } }

    assertNull(preview)
    assertEquals(1, dropped)
    assertFalse(broken.exists())
    assertEquals(ProofPhotoError.UNREADABLE, state.error)
  }

  @Test
  fun retakingDeletesTheCopyAndClearErrorClearsTheError() {
    val copy = File(File(app.filesDir, "proofs").apply { mkdirs() }, "proof-old.jpg")
    copy.writeText("photo")
    state.noSettings()

    state.retake(Uri.fromFile(copy))
    state.clearError()

    assertFalse(copy.exists())
    assertNull(state.error)
  }
}
