// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** Invalid rejection reasons must be refused before constructing any Firestore request. */
class CompletionRepositoryFirestoreValidationTest {
  @Test
  fun oversizedReasonFailsBeforeAnyFirestoreAccess() = runBlocking {
    val db = mockk<FirebaseFirestore>()
    val repository = CompletionRepositoryFirestore(db)

    val result = repository.reject("completion", "x".repeat(301))

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    verify(exactly = 0) { db.collection(any<String>()) }
  }
}
