// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.net.Uri
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests [FakeProofPhotoRepository] against the [ProofPhotoRepository] contract, one behaviour per
 * test. Photos are mocks: a unit test can't build a real [Uri], so the fake is given their bytes.
 */
class FakeProofPhotoRepositoryTest {
  private lateinit var repository: FakeProofPhotoRepository

  /** Gives every test a fresh repository with no uploads. */
  @Before
  fun setUp() {
    repository = FakeProofPhotoRepository()
  }

  @Test
  fun upload_recordsThePhotoUnderAStorageLocationInTheReservationsProofs() = runTest {
    val photo = mockk<Uri>()

    val location = repository.upload(RESERVATION_ID, photo).getOrThrow()

    assertTrue(location.startsWith(GS_SCHEME))
    assertTrue(location.contains(RESERVATION_PROOFS))
    assertEquals(mapOf(location to photo), repository.uploads)
  }

  @Test
  fun upload_twiceForTheSameReservation_keepsBothPhotos() = runTest {
    val first = mockk<Uri>()
    val retake = mockk<Uri>()

    val firstLocation = repository.upload(RESERVATION_ID, first).getOrThrow()
    val retakeLocation = repository.upload(RESERVATION_ID, retake).getOrThrow()

    assertNotEquals(firstLocation, retakeLocation)
    assertEquals(mapOf(firstLocation to first, retakeLocation to retake), repository.uploads)
  }

  @Test
  fun upload_whenFailureIsForced_returnsItAndRecordsNothing() = runTest {
    val failure = IllegalStateException(OFFLINE)
    repository.forcedFailure = failure

    assertEquals(failure, repository.upload(RESERVATION_ID, mockk()).exceptionOrNull())
    assertTrue(repository.uploads.isEmpty())
  }

  @Test
  fun upload_invalidReservationId_failsBeforeTheBackendAndRecordsNothing() = runTest {
    repository.forcedFailure = IllegalStateException(OFFLINE)

    val blank = repository.upload(BLANK, mockk())
    val nested = repository.upload(NESTED_ID, mockk())

    assertTrue(blank.exceptionOrNull() is IllegalArgumentException)
    assertTrue(nested.exceptionOrNull() is IllegalArgumentException)
    assertTrue(repository.uploads.isEmpty())
  }

  @Test
  fun forcedFailure_setBackToNull_letsUploadAndDownloadSucceedAgain() = runTest {
    repository.forcedFailure = IllegalStateException(OFFLINE)
    repository.upload(RESERVATION_ID, mockk())

    repository.forcedFailure = null

    val location = repository.upload(RESERVATION_ID, mockk()).getOrThrow()
    assertTrue(repository.download(location).isSuccess)
  }

  @Test
  fun upload_photoAtTheSizeLimit_failsBeforeTheBackendAndRecordsNothing() = runTest {
    repository = FakeProofPhotoRepository(read = { ByteArray(PROOF_PHOTO_LIMIT_BYTES) })
    repository.forcedFailure = IllegalStateException(OFFLINE)

    val result = repository.upload(RESERVATION_ID, mockk())

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    assertTrue(repository.uploads.isEmpty())
  }

  @Test
  fun upload_photoJustUnderTheSizeLimit_succeeds() = runTest {
    repository = FakeProofPhotoRepository(read = { ByteArray(PROOF_PHOTO_LIMIT_BYTES - 1) })

    assertTrue(repository.upload(RESERVATION_ID, mockk()).isSuccess)
  }

  @Test
  fun download_returnsTheBytesUploadedAtThatLocation() = runTest {
    val first = mockk<Uri>()
    val retake = mockk<Uri>()
    repository =
        FakeProofPhotoRepository(read = { if (it === retake) RETAKE_BYTES else FIRST_BYTES })
    repository.upload(RESERVATION_ID, first).getOrThrow()
    val retakeLocation = repository.upload(RESERVATION_ID, retake).getOrThrow()

    assertArrayEquals(RETAKE_BYTES, repository.download(retakeLocation).getOrThrow())
  }

  @Test
  fun download_returnsANewCopyEachTime() = runTest {
    val original = byteArrayOf(ORIGINAL_BYTE)
    repository = FakeProofPhotoRepository(read = { original.copyOf() })
    val location = repository.upload(RESERVATION_ID, mockk()).getOrThrow()

    repository.download(location).getOrThrow()[0] = CHANGED_BYTE

    assertArrayEquals(original, repository.download(location).getOrThrow())
  }

  @Test
  fun download_whereNothingWasUploaded_fails() = runTest {
    val result = repository.download(UNKNOWN_LOCATION)

    assertTrue(result.exceptionOrNull() is NoSuchElementException)
  }

  @Test
  fun download_outsideItsBucket_failsBeforeTheBackend() = runTest {
    repository.forcedFailure = IllegalStateException(OFFLINE)

    val result = repository.download(FOREIGN_LOCATION)

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
  }

  @Test
  fun download_whenFailureIsForced_returnsItBeforeTheLookup() = runTest {
    val failure = IllegalStateException(OFFLINE)
    repository.forcedFailure = failure

    assertEquals(failure, repository.download(UNKNOWN_LOCATION).exceptionOrNull())
  }

  private companion object {
    const val RESERVATION_ID = "r1"
    const val GS_SCHEME = "gs://"
    const val RESERVATION_PROOFS = "/proofs/$RESERVATION_ID/"
    const val OFFLINE = "offline"
    const val BLANK = " "
    const val NESTED_ID = "r1/nested"
    const val FOREIGN_LOCATION = "gs://other-bucket/proofs/$RESERVATION_ID/photo-1"
    const val UNKNOWN_LOCATION = "gs://fake-bucket/proofs/$RESERVATION_ID/missing"
    const val ORIGINAL_BYTE: Byte = 1
    const val CHANGED_BYTE: Byte = 2
    val FIRST_BYTES = byteArrayOf(1)
    val RETAKE_BYTES = byteArrayOf(2)
  }
}
