// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.model.user

import android.net.Uri
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FakeUserPictureRepositoryTest {
  @Test
  fun replacingAPictureCreatesANewLocationAndKeepsTheOldBytes() = runTest {
    val firstPhoto = Uri.parse("content://photos/first")
    val secondPhoto = Uri.parse("content://photos/second")
    val repository = FakeUserPictureRepository {
      if (it == firstPhoto) byteArrayOf(1) else byteArrayOf(2)
    }

    val first = repository.upload("user-1", firstPhoto).getOrThrow()
    val second = repository.upload("user-1", secondPhoto).getOrThrow()

    assertNotEquals(first, second)
    assertArrayEquals(byteArrayOf(1), repository.download(first).getOrThrow())
    assertArrayEquals(byteArrayOf(2), repository.download(second).getOrThrow())
  }

  @Test
  fun failureDoesNotCreateAPicture() = runTest {
    val repository = FakeUserPictureRepository()
    repository.failure = IllegalStateException("offline")

    val result = repository.upload("user-1", Uri.parse("content://photos/first"))

    assertTrue(result.isFailure)
    repository.failure = null
    assertTrue(repository.download("gs://fake-bucket/user-pictures/user-1/1").isFailure)
  }

  @Test
  fun invalidUserIdsFailBeforeReadingThePhoto() = runTest {
    var reads = 0
    val repository = FakeUserPictureRepository {
      reads++
      byteArrayOf(1)
    }
    val photo = Uri.parse("content://photos/first")

    assertTrue(repository.upload("", photo).exceptionOrNull() is IllegalArgumentException)
    assertTrue(repository.upload("other/user", photo).exceptionOrNull() is IllegalArgumentException)
    assertEquals(0, reads)
  }

  @Test
  fun uploadedAndDownloadedBytesAreDefensiveCopies() = runTest {
    val original = byteArrayOf(1)
    val repository = FakeUserPictureRepository { original }
    val location = repository.upload("user-1", Uri.parse("content://photos/first")).getOrThrow()

    original[0] = 2
    val downloaded = repository.download(location).getOrThrow()
    assertArrayEquals(byteArrayOf(1), downloaded)
    downloaded[0] = 3
    assertArrayEquals(byteArrayOf(1), repository.download(location).getOrThrow())
  }

  @Test
  fun downloadReturnsTheConfiguredFailure() = runTest {
    val repository = FakeUserPictureRepository()
    val location = repository.upload("user-1", Uri.parse("content://photos/first")).getOrThrow()
    val error = IllegalStateException("offline")
    repository.failure = error

    assertSame(error, repository.download(location).exceptionOrNull())
  }
}
