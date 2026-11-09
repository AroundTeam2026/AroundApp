// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.model.user

import android.net.Uri
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
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
}
