// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.auth

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoogleCredentialSessionEffectTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun signedOutStartupDoesNotClearButSessionLossDoes() {
    val auth = FakeAuthRepository()
    var clears = 0
    compose.setContent { GoogleCredentialSessionEffect(auth) { clears++ } }
    compose.runOnIdle { assertEquals(0, clears) }
    compose.runOnIdle { runBlocking { auth.signInWithGoogle("first") } }
    compose.runOnIdle {
      assertEquals(0, clears)
      auth.signOut()
    }
    compose.runOnIdle { assertEquals(1, clears) }
    compose.runOnIdle { auth.signOut() }
    compose.runOnIdle { assertEquals(1, clears) }
  }

  @Test
  fun cleanupFailureDoesNotPreventFutureSessionsOrSignOut() {
    val auth = FakeAuthRepository("restored")
    var clears = 0
    compose.setContent {
      GoogleCredentialSessionEffect(auth) {
        clears++
        error("unavailable")
      }
    }
    compose.runOnIdle { auth.signOut() }
    compose.runOnIdle {
      assertEquals(1, clears)
      runBlocking { auth.signInWithGoogle("next") }
    }
    compose.runOnIdle { auth.signOut() }
    compose.runOnIdle {
      assertEquals(2, clears)
      assertEquals(null, auth.currentUserId.value)
    }
  }
}
