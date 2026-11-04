// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.net.Uri
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests [FakeProofPhotoRepository] against the [ProofPhotoRepository] contract, one behaviour per
 * test. Photos are mocks: a unit test can't build a real [Uri], and the fake never reads one.
 */
class FakeProofPhotoRepositoryTest {
  private lateinit var repository: FakeProofPhotoRepository

  /** Gives every test a fresh repository with no uploads. */
  @Before
  fun setUp() {
    repository = FakeProofPhotoRepository()
  }

  @Test
  fun upload_recordsThePhotoUnderAStorageLocationInTheReservationsProofs() =
      runBlocking<Unit> {
        val photo = mockk<Uri>()

        val location = repository.upload(RESERVATION_ID, photo).getOrThrow()

        assertTrue(location.startsWith(GS_SCHEME))
        assertTrue(location.contains(RESERVATION_PROOFS))
        assertEquals(mapOf(location to photo), repository.uploads)
      }

  @Test
  fun upload_twiceForTheSameReservation_keepsBothPhotos() =
      runBlocking<Unit> {
        val first = mockk<Uri>()
        val retake = mockk<Uri>()

        val firstLocation = repository.upload(RESERVATION_ID, first).getOrThrow()
        val retakeLocation = repository.upload(RESERVATION_ID, retake).getOrThrow()

        assertNotEquals(firstLocation, retakeLocation)
        assertEquals(mapOf(firstLocation to first, retakeLocation to retake), repository.uploads)
      }

  @Test
  fun upload_whenFailureIsForced_returnsItAndRecordsNothing() =
      runBlocking<Unit> {
        val failure = IllegalStateException(OFFLINE)
        repository.forcedFailure = failure

        assertEquals(failure, repository.upload(RESERVATION_ID, mockk()).exceptionOrNull())
        assertTrue(repository.uploads.isEmpty())
      }

  private companion object {
    const val RESERVATION_ID = "r1"
    const val GS_SCHEME = "gs://"
    const val RESERVATION_PROOFS = "/proofs/$RESERVATION_ID/"
    const val OFFLINE = "offline"
  }
}
