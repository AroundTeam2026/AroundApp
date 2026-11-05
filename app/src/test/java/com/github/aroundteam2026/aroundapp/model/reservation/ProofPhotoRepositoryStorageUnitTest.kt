// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests what `ProofPhotoRepositoryStorageTest` can't make happen on the emulator: transfers that
 * never finish, a cancelled caller, a type the `ContentResolver` knows, an unreadable photo, and
 * input rejected before Storage is reached. Storage and the context are mocks, and time is
 * `runTest`'s virtual time, so a timeout passes at once.
 *
 * The context hands out its `ContentResolver` only through its application context, so every test
 * fails if the repository reads photos through the context it was given, which may be a screen's.
 */
@RunWith(AndroidJUnit4::class)
class ProofPhotoRepositoryStorageUnitTest {
  private val resolver = mockk<ContentResolver>()
  private val appContext = mockk<Context> { every { contentResolver } returns resolver }
  private val context = mockk<Context> { every { applicationContext } returns appContext }

  private val photoReference = mockk<StorageReference>()
  private val root =
      mockk<StorageReference> {
        every { bucket } returns BUCKET
        every { child(any()) } returns photoReference
      }
  private val storage = mockk<FirebaseStorage> { every { reference } returns root }

  /** The repository under test, reading photos on the test's dispatcher. */
  private fun TestScope.repository() =
      ProofPhotoRepositoryStorage(
          context,
          storage,
          TRANSFER_TIMEOUT_MS,
          StandardTestDispatcher(testScheduler),
      )

  @Test
  fun upload_invalidReservationId_failsBeforeAnythingIsReadOrUploaded() = runTest {
    val photo = photo()

    for (reservationId in listOf("", " ", "nested/$RESERVATION_ID")) {
      val error = repository().upload(reservationId, photo).exceptionOrNull()

      assertTrue("\"$reservationId\" gave $error", error is IllegalArgumentException)
    }
    verify(exactly = 0) { resolver.openInputStream(any()) }
    verify(exactly = 0) { root.child(any()) }
  }

  @Test
  fun upload_photoAtTheSizeLimit_failsBeforeAnythingIsUploaded() = runTest {
    val error =
        repository()
            .upload(RESERVATION_ID, photo(ByteArray(PROOF_PHOTO_LIMIT_BYTES)))
            .exceptionOrNull()

    assertTrue(
        "Expected IllegalArgumentException but got $error",
        error is IllegalArgumentException,
    )
    verify(exactly = 0) { root.child(any()) }
  }

  @Test
  fun upload_photoOverTheSizeLimit_failsWithoutBeingReadWhole() = runTest {
    val photo = mockk<Uri>()
    val stream = ByteArrayInputStream(ByteArray(OVERSIZED_PHOTO_BYTES))
    every { resolver.openInputStream(photo) } returns stream

    val error = repository().upload(RESERVATION_ID, photo).exceptionOrNull()

    assertTrue(
        "Expected IllegalArgumentException but got $error",
        error is IllegalArgumentException,
    )
    assertTrue("The whole photo was read", stream.available() > 0)
  }

  @Test
  fun upload_photoJustUnderTheSizeLimit_isUploaded() = runTest {
    val bytes = ByteArray(PROOF_PHOTO_LIMIT_BYTES - 1)
    uploadReturns(finishedUpload())

    assertTrue(repository().upload(RESERVATION_ID, photo(bytes)).isSuccess)
    verify { photoReference.putBytes(bytes, any()) }
  }

  @Test
  fun upload_photoTheAppCanNoLongerOpen_failsWithFileNotFound() = runTest {
    val photo = mockk<Uri>()
    every { resolver.openInputStream(photo) } returns null

    val error = repository().upload(RESERVATION_ID, photo).exceptionOrNull()

    assertTrue("Expected FileNotFoundException but got $error", error is FileNotFoundException)
    verify(exactly = 0) { root.child(any()) }
  }

  @Test
  fun upload_storesThePhotoWithTheTypeTheResolverGives() = runTest {
    val metadata = slot<StorageMetadata>()
    every { photoReference.putBytes(any(), capture(metadata)) } returns finishedUpload()

    repository().upload(RESERVATION_ID, photo(type = PNG)).getOrThrow()

    assertEquals(PNG, metadata.captured.contentType)
  }

  @Test
  fun upload_thatDoesNotFinishInTime_failsWithTimeoutAndIsCancelled() = runTest {
    val upload = unfinishedUpload()
    uploadReturns(upload)

    val error = repository().upload(RESERVATION_ID, photo()).exceptionOrNull()

    assertTrue("Expected TimeoutException but got $error", error is TimeoutException)
    verify { upload.cancel() }
  }

  @Test
  fun upload_whenTheCallerIsCancelled_isCancelled() = runTest {
    val upload = unfinishedUpload()
    uploadReturns(upload)
    val repository = repository()

    val caller = launch { repository.upload(RESERVATION_ID, photo()) }
    runCurrent()
    caller.cancel()
    runCurrent()

    verify { upload.cancel() }
  }

  @Test
  fun download_outsideTheBucket_failsBeforeAnythingIsDownloaded() = runTest {
    for (location in listOf(OTHER_BUCKET_LOCATION, HTTPS_LOCATION)) {
      val error = repository().download(location).exceptionOrNull()

      assertTrue("$location gave $error", error is IllegalArgumentException)
    }
    verify(exactly = 0) { storage.getReferenceFromUrl(any()) }
  }

  @Test
  fun download_thatDoesNotFinishInTime_failsWithTimeout() = runTest {
    every { storage.getReferenceFromUrl(LOCATION) } returns photoReference
    every { photoReference.getBytes(any()) } returns TaskCompletionSource<ByteArray>().task

    val error = repository().download(LOCATION).exceptionOrNull()

    assertTrue("Expected TimeoutException but got $error", error is TimeoutException)
  }

  // ---- helpers ----

  /** A photo the resolver reads as [bytes], of [type] or of no known type. */
  private fun photo(bytes: ByteArray = ByteArray(PHOTO_SIZE), type: String? = null): Uri {
    val photo = mockk<Uri>()
    every { resolver.openInputStream(photo) } returns OneByteAtATime(bytes)
    every { resolver.getType(photo) } returns type
    return photo
  }

  /**
   * [bytes], one per read, as a provider may hand them out, so a read that stops short of the size
   * limit shows instead of being hidden by a full buffer.
   */
  private class OneByteAtATime(bytes: ByteArray) : ByteArrayInputStream(bytes) {
    override fun read(b: ByteArray, off: Int, len: Int) = super.read(b, off, minOf(len, 1))
  }

  private fun uploadReturns(upload: UploadTask) {
    every { photoReference.putBytes(any(), any()) } returns upload
  }

  /** An upload that has succeeded. */
  private fun finishedUpload() =
      mockk<UploadTask>(relaxed = true) {
        every { isComplete } returns true
        every { exception } returns null
        every { isCanceled } returns false
        every { result } returns mockk()
      }

  /** An upload that never finishes. */
  private fun unfinishedUpload() =
      mockk<UploadTask>(relaxed = true) { every { isComplete } returns false }

  private companion object {
    const val BUCKET = "test-bucket"
    const val RESERVATION_ID = "r1"
    const val LOCATION = "gs://$BUCKET/proofs/$RESERVATION_ID/p1"
    const val OTHER_BUCKET_LOCATION = "gs://other-$BUCKET/proofs/$RESERVATION_ID/p1"
    const val HTTPS_LOCATION = "https://$BUCKET/proofs/$RESERVATION_ID/p1"
    const val PNG = "image/png"

    /** Size of a photo whose size doesn't matter to the test. */
    const val PHOTO_SIZE = 16

    /** Size of a photo well over the limit, so stopping at the limit leaves some unread. */
    const val OVERSIZED_PHOTO_BYTES = 2 * PROOF_PHOTO_LIMIT_BYTES

    /** Passes at once in virtual time; any length shows the timeout is applied. */
    const val TRANSFER_TIMEOUT_MS = 1_000L
  }
}
