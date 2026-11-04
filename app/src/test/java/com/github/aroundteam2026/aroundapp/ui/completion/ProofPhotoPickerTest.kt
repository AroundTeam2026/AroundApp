// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.Manifest
import android.app.Application
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/**
 * The picker with its permission, camera and gallery, which [FakeActivityResults] answers in place
 * of Android and the other apps. How each state is drawn is in [ProofPhotoBoxTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProofPhotoPickerTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val app = ApplicationProvider.getApplicationContext<Application>()
  // Plain files instead of FileProvider, which can't find the cache under Windows paths
  private val photos = ProofPhotos(app, Dispatchers.Unconfined, share = { Uri.fromFile(it) })
  private val picked = mutableListOf<Uri>()
  private var retakes = 0

  /** Shows the picker with [photo]; each photo it hands over becomes the picked one. */
  private fun show(results: FakeActivityResults, photo: Uri? = null): FakeActivityResults {
    compose.setContent {
      var current by remember { mutableStateOf(photo) }
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides results) {
        AroundAppTheme {
          ProofPhotoPicker(
              venueName = "Café Lumière",
              photo = current,
              onPhotoPicked = {
                picked += it
                current = it
              },
              onRetake = {
                retakes++
                current = null
              },
              photos = photos,
          )
        }
      }
    }
    compose.waitForIdle()
    return results
  }

  private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

  private fun tap(tag: String) {
    node(tag).performClick()
    compose.waitForIdle()
  }

  private fun allowCamera() = shadowOf(app).grantPermissions(Manifest.permission.CAMERA)

  private fun leaveAndComeBack() {
    compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
    compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    compose.waitForIdle()
  }

  @Test
  fun takingAPhotoAsksForTheCameraFirst() {
    val results = show(FakeActivityResults(permission = true, camera = photo()))

    tap(C.Tag.PROOF_PHOTO_TAKE)

    assertEquals(listOf<Any?>(Manifest.permission.CAMERA), results.inputsOf<RequestPermission>())
    assertEquals(1, results.inputsOf<TakePicture>().size)
  }

  @Test
  fun aTakenPhotoIsPickedAndShown() {
    allowCamera()
    val results = show(FakeActivityResults(camera = photo()))

    tap(C.Tag.PROOF_PHOTO_TAKE)

    // An allowed camera opens without asking again
    assertTrue(results.inputsOf<RequestPermission>().isEmpty())
    assertEquals(results.inputsOf<TakePicture>(), picked)
    node(C.Tag.PROOF_PHOTO_IMAGE).assertIsDisplayed()
    node(C.Tag.PROOF_PHOTO_RETAKE).assertIsDisplayed()
  }

  @Test
  fun aCancelledCameraPicksNothing() {
    allowCamera()
    show(FakeActivityResults(camera = null))

    tap(C.Tag.PROOF_PHOTO_TAKE)

    assertTrue(picked.isEmpty())
    node(C.Tag.PROOF_PHOTO_TAKE).assertIsDisplayed()
  }

  @Test
  fun withoutACameraAppNothingOpensAndTheGalleryStillWorks() {
    allowCamera()
    show(FakeActivityResults(noCameraApp = true, gallery = testImage(app, 80, 60)))

    tap(C.Tag.PROOF_PHOTO_TAKE)
    tap(C.Tag.PROOF_PHOTO_GALLERY)

    assertEquals(1, picked.size)
  }

  @Test
  fun openSettingsShowsTheAppsSettings() {
    show(FakeActivityResults(permission = false))
    tap(C.Tag.PROOF_PHOTO_TAKE)

    tap(C.Tag.PROOF_PHOTO_OPEN_SETTINGS)

    val opened = shadowOf(compose.activity).nextStartedActivity
    assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, opened.action)
    assertEquals("package:${app.packageName}", opened.dataString)
  }

  @Test
  fun backFromTheSettingsWithTheCameraAllowedTakePhotoReturns() {
    show(FakeActivityResults(permission = false))
    tap(C.Tag.PROOF_PHOTO_TAKE)

    allowCamera()
    leaveAndComeBack()

    node(C.Tag.PROOF_PHOTO_TAKE).assertIsDisplayed()
  }

  @Test
  fun backFromTheSettingsWithoutTheCameraTheRefusalStays() {
    show(FakeActivityResults(permission = false))
    tap(C.Tag.PROOF_PHOTO_TAKE)

    leaveAndComeBack()

    node(C.Tag.PROOF_PHOTO_OPEN_SETTINGS).assertIsDisplayed()
  }

  @Test
  fun theGalleryAsksForNoPermissionAndItsPhotoIsPicked() {
    val image = testImage(app, 80, 60)
    val results = show(FakeActivityResults(gallery = image))

    tap(C.Tag.PROOF_PHOTO_GALLERY)

    assertTrue(results.inputsOf<RequestPermission>().isEmpty())
    assertEquals(listOf(image), picked)
    node(C.Tag.PROOF_PHOTO_IMAGE).assertIsDisplayed()
  }

  @Test
  fun aClosedGalleryPicksNothing() {
    show(FakeActivityResults(gallery = null))

    tap(C.Tag.PROOF_PHOTO_GALLERY)

    assertTrue(picked.isEmpty())
    node(C.Tag.PROOF_PHOTO_GALLERY).assertIsDisplayed()
  }

  @Test
  fun retakeClearsThePhotoBackToTheChoices() {
    show(FakeActivityResults(), photo = testImage(app, 80, 60))

    tap(C.Tag.PROOF_PHOTO_RETAKE)

    assertEquals(1, retakes)
    node(C.Tag.PROOF_PHOTO_TAKE).assertIsDisplayed()
  }

  @Test
  fun aPhotoThatCantBeShownStillCountsAsPicked() {
    show(FakeActivityResults(), photo = Uri.fromFile(File(app.cacheDir, "gone.jpg")))

    node(C.Tag.PROOF_PHOTO_IMAGE).assertDoesNotExist()
    node(C.Tag.PROOF_PHOTO_RETAKE).assertIsDisplayed()
  }

  // The app passes no ProofPhotos, so the picker makes its own from the app's context
  @Test
  fun withoutProofPhotosThePickerMakesItsOwn() {
    compose.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides FakeActivityResults()) {
        AroundAppTheme { ProofPhotoPicker("Café Lumière", photo = null, {}, {}) }
      }
    }

    node(C.Tag.PROOF_PHOTO_TAKE).assertIsDisplayed()
  }

  /** A small photo, as the camera app would take it. */
  private fun photo() = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888)
}

/**
 * Answers the picker's requests in place of Android and the other apps, and records them: the
 * camera permission with [permission], the camera by writing [camera] into its file (or cancelling
 * when null, or failing when there is [noCameraApp]), and the gallery with [gallery].
 */
internal class FakeActivityResults(
    private val permission: Boolean? = null,
    private val camera: Bitmap? = null,
    private val noCameraApp: Boolean = false,
    private val gallery: Uri? = null,
) : ActivityResultRegistryOwner {
  val launches = mutableListOf<Pair<ActivityResultContract<*, *>, Any?>>()

  inline fun <reified T : ActivityResultContract<*, *>> inputsOf() =
      launches.filter { it.first is T }.map { it.second }

  override val activityResultRegistry =
      object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
          if (contract is TakePicture && noCameraApp) {
            throw ActivityNotFoundException("No camera app")
          }
          launches += contract to input
          when (contract) {
            is TakePicture -> {
              camera?.let { photo ->
                File((input as Uri).path!!).outputStream().use {
                  photo.compress(Bitmap.CompressFormat.JPEG, 90, it)
                }
              }
              dispatchResult(requestCode, camera != null)
            }
            is RequestPermission -> permission?.let { dispatchResult(requestCode, it) }
            is PickVisualMedia -> dispatchResult(requestCode, gallery)
            else -> error("Unexpected request: $contract")
          }
        }
      }
}
