// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Invalid rejection reasons must be refused before constructing any Firestore request. */
class CompletionRepositoryFirestoreValidationTest {
  @Test
  fun reject_oversizedReason_failsBeforeAnyFirestoreAccess() = runBlocking {
    val db = mockk<FirebaseFirestore>()
    val repository = CompletionRepositoryFirestore(db)

    val result = repository.reject("completion", "x".repeat(MAX_REJECT_REASON_LENGTH + 1))

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    verify(exactly = 0) { db.collection(any<String>()) }
  }

  /** The limit itself is allowed: the request goes on to the backend, which fails here. */
  @Test
  fun reject_reasonAtTheLimit_reachesTheBackend() = runBlocking {
    val offline = IllegalStateException("offline")
    val db = mockk<FirebaseFirestore>()
    every { db.collection(any<String>()) } throws offline
    val repository = CompletionRepositoryFirestore(db)

    val result = repository.reject("completion", "x".repeat(MAX_REJECT_REASON_LENGTH))

    assertSame(offline, result.exceptionOrNull())
  }
}
