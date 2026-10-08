// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.model.auth

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthRepositoryProviderTest {
  @Test
  fun sharesOneFirebaseRepositoryAcrossCallers() {
    FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext())
    val first = AuthRepositoryProvider.repository
    assertSame(first, AuthRepositoryProvider.repository)
    assertTrue(first is AuthRepositoryFirebase)
  }
}
