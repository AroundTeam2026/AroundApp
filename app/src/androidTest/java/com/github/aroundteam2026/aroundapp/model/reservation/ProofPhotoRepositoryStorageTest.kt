// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.aroundteam2026.aroundapp.testing.SETUP_TIMEOUT_MS
import com.github.aroundteam2026.aroundapp.testing.TIMEOUT_MS
import com.github.aroundteam2026.aroundapp.testing.emulatorStorage
import com.github.aroundteam2026.aroundapp.testing.signInNewUser
import com.github.aroundteam2026.aroundapp.testing.withTimeoutBlocking
import com.github.aroundteam2026.aroundapp.testing.writeBypassingRules
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/**
 * Tests [ProofPhotoRepositoryStorage] on the Auth, Firestore and Storage emulators, started with
 * `firebase emulators:start --only auth,firestore,storage --project demo-project`, so storage.rules
 * apply.
 *
 * One explorer is signed in for the whole class, on a Storage client from [emulatorStorage] on its
 * own named app on the `demo-project` id, so the tests cannot reach the real bucket. Each test
 * writes the reservations and memberships the rules read straight into the Firestore emulator,
 * under fresh ids, so tests never see each other's photos. Photos are files in the app's cache,
 * which the app's `ContentResolver` reads without a type, as for some camera files. Each test's
 * KDoc names the production bug it catches; what the emulator can't make happen, like a transfer
 * that never ends, is in `ProofPhotoRepositoryStorageUnitTest`.
 */
@RunWith(AndroidJUnit4::class)
class ProofPhotoRepositoryStorageTest {
  /**
   * Fails any test that runs longer than this instead of blocking CI forever. It is longer than the
   * longest test body timeout, so that timeout reports where the test was stuck before this rule
   * cuts it off.
   */
  @get:Rule val timeout: Timeout = Timeout.millis(RULE_TIMEOUT_MS)

  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

  private val repository = ProofPhotoRepositoryStorage(context, storage)

  /** Every photo file a test wrote, deleted by [tearDown]. */
  private val files = mutableListOf<File>()

  /** Deletes the photo files, so the near-limit ones don't pile up in the app's cache. */
  @After
  fun tearDown() {
    files.forEach { it.delete() }
  }

  /**
   * Bug: the bytes are changed on the way up or down, or the download is capped below the size
   * limit, so a large photo can't be shown.
   */
  @Test
  fun upload_thenDownload_returnsThePhotosBytes() =
      runBlocking<Unit> {
        withTimeout(LARGE_PHOTO_TIMEOUT_MS) {
          val bytes = photoBytes(PROOF_PHOTO_LIMIT_BYTES - 1)

          val location = repository.upload(approvedReservation(), photo(bytes)).getOrThrow()

          assertArrayEquals(bytes, repository.download(location).getOrThrow())
        }
      }

  /**
   * Bug: the photo is stored outside its reservation's proofs, or its location isn't the `gs://`
   * one, or it is stored as another type than a JPEG when the device can't tell.
   */
  @Test
  fun upload_storesAJpegInTheReservationsProofs() = withTimeoutBlocking {
    val reservationId = approvedReservation()

    val location = repository.upload(reservationId, photo(photoBytes())).getOrThrow()

    val reservationProofs = "gs://${storage.reference.bucket}/$PROOFS/$reservationId/"
    assertTrue("$location isn't in $reservationProofs", location.startsWith(reservationProofs))
    val stored = storage.getReferenceFromUrl(location).metadata.await()
    assertEquals(JPEG, stored.contentType)
  }

  /** Bug: every photo of a reservation gets the same id, so the rules refuse a retake. */
  @Test
  fun upload_twice_keepsBothPhotos() = withTimeoutBlocking {
    val reservationId = approvedReservation()
    val first = photoBytes()
    val retake = photoBytes().reversedArray()

    val firstLocation = repository.upload(reservationId, photo(first)).getOrThrow()
    val retakeLocation = repository.upload(reservationId, photo(retake)).getOrThrow()

    assertNotEquals(firstLocation, retakeLocation)
    assertArrayEquals(first, repository.download(firstLocation).getOrThrow())
    assertArrayEquals(retake, repository.download(retakeLocation).getOrThrow())
  }

  /** Bug: a refusal of the rules escapes upload, or comes back as a success. */
  @Test
  fun upload_forAReservationNotApproved_returnsTheRulesRefusal() = withTimeoutBlocking {
    val result = repository.upload(reservation(PENDING), photo(photoBytes()))

    assertNotAuthorized(result)
  }

  /** Bug: every failed download is reported as a missing photo, even one the rules refused. */
  @Test
  fun download_ofAnotherPartysPhoto_returnsTheRulesRefusal() = withTimeoutBlocking {
    val otherReservation = UUID.randomUUID().toString()

    val result = repository.download(locationIn(otherReservation))

    assertNotAuthorized(result)
  }

  /** Bug: a missing photo comes back as Storage's own exception, which callers can't tell apart. */
  @Test
  fun download_whereNoPhotoWasUploaded_failsWithNoSuchElement() = withTimeoutBlocking {
    val result = repository.download(locationIn(approvedReservation()))

    val error = result.exceptionOrNull()
    assertTrue("Expected NoSuchElementException but got $error", error is NoSuchElementException)
  }

  // ---- helpers ----

  /** Stores an approved reservation the explorer joined, past firestore.rules; returns its id. */
  private fun approvedReservation(): String = reservation(APPROVED)

  /** Stores a reservation with [status] the explorer joined, past firestore.rules. */
  private fun reservation(status: String): String {
    val id = UUID.randomUUID().toString()
    writeBypassingRules("reservations/$id", mapOf("venueId" to VENUE_ID, "status" to status))
    writeBypassingRules(
        "party_user/${id}_$explorerUid",
        mapOf("reservationId" to id, "uid" to explorerUid, "status" to JOINED),
    )
    return id
  }

  /** A location in the bucket, among [reservationId]'s proofs, where nothing was uploaded. */
  private fun locationIn(reservationId: String): String =
      storage.reference.child("$PROOFS/$reservationId/${UUID.randomUUID()}").toString()

  /** [size] bytes that differ from one position to the next, so a shifted byte shows. */
  private fun photoBytes(size: Int = PHOTO_SIZE): ByteArray = ByteArray(size) { it.toByte() }

  /** A `file://` photo in the app's cache holding [bytes]. */
  private fun photo(bytes: ByteArray): Uri {
    val file = File(context.cacheDir, UUID.randomUUID().toString())
    files += file
    file.writeBytes(bytes)
    return Uri.fromFile(file)
  }

  /** Asserts [result] is a failure caused by the security rules refusing the transfer. */
  private fun assertNotAuthorized(result: Result<*>) {
    val error = result.exceptionOrNull()
    assertTrue("Expected a StorageException but got $error", error is StorageException)
    assertEquals(StorageException.ERROR_NOT_AUTHORIZED, (error as StorageException).errorCode)
  }

  companion object {
    /** Name of the app the explorer signs in on. */
    private const val APP = "proof-photo-test-explorer"

    /** The folder storage.rules keep proofs in. */
    private const val PROOFS = "proofs"

    /** What a photo without a type is stored as. */
    private const val JPEG = "image/jpeg"

    private const val APPROVED = "APPROVED"
    private const val PENDING = "PENDING"
    private const val JOINED = "JOINED"

    /** Venue of every reservation; the rules only read it for the venue's own reads. */
    private const val VENUE_ID = "venue-uid"

    /** Size of a photo whose size doesn't matter to the test. */
    private const val PHOTO_SIZE = 1024

    /** Bounds a test sending a photo near the size limit up and down, slower on CI. */
    private const val LARGE_PHOTO_TIMEOUT_MS = 3 * TIMEOUT_MS

    /** Longer than the longest test body timeout, [LARGE_PHOTO_TIMEOUT_MS]. */
    private const val RULE_TIMEOUT_MS = LARGE_PHOTO_TIMEOUT_MS + TIMEOUT_MS

    /** The explorer's Storage client, pointed at the emulator once per process. */
    private lateinit var storage: FirebaseStorage

    /** Uid of the explorer signed in by [signIn]. */
    private lateinit var explorerUid: String

    /** Signs the explorer in once: each test's fresh reservations keep the tests apart. */
    @BeforeClass
    @JvmStatic
    fun signIn() = runBlocking {
      withTimeout(SETUP_TIMEOUT_MS) { explorerUid = signInNewUser(APP) }
      storage = emulatorStorage(APP)
    }
  }
}
