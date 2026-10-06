// Co-authored-by: OpenAI Codex
package com.github.aroundteam2026.aroundapp.model.auth

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Invalid inputs must fail consistently before Firebase makes a network request. */
@RunWith(AndroidJUnit4::class)
class AuthInputValidationTest {
  private lateinit var app: FirebaseApp
  private lateinit var repositories: List<AuthRepository>

  @Before
  fun setUp() {
    app =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            FirebaseOptions.Builder()
                .setApplicationId("1:123456789:android:validation")
                .setApiKey("validation-api-key")
                .setProjectId("auth-validation")
                .build(),
            "auth-validation",
        )
    val auth = FirebaseAuth.getInstance(app)
    auth.signOut()
    repositories = listOf(FakeAuthRepository(), AuthRepositoryFirebase(auth))
  }

  @After
  fun tearDown() {
    app.delete()
  }

  @Test
  fun emptyAndMalformedEmailsAreInvalidForSignUpAndSignIn() = runBlocking {
    for (repo in repositories) {
      for (email in listOf("", " ", "not-an-email")) {
        assertEquals(
            AuthError.InvalidEmail,
            repo.signUpWithEmail(email, "password123").exceptionOrNull(),
        )
        assertNull(repo.currentUserId.value)
        assertEquals(
            AuthError.InvalidEmail,
            repo.signInWithEmail(email, "password123").exceptionOrNull(),
        )
        assertNull(repo.currentUserId.value)
      }
    }
  }

  @Test
  fun emptyAndShortSignUpPasswordsAreWeak() = runBlocking {
    for (repo in repositories) {
      for (password in listOf("", "12345", " ")) {
        assertEquals(
            AuthError.WeakPassword,
            repo.signUpWithEmail("ada@around.test", password).exceptionOrNull(),
        )
        assertNull(repo.currentUserId.value)
      }
    }
  }

  @Test
  fun emptySignInPasswordIsInvalidCredentials() = runBlocking {
    for (repo in repositories) {
      assertEquals(
          AuthError.InvalidCredentials,
          repo.signInWithEmail("ada@around.test", "").exceptionOrNull(),
      )
      assertNull(repo.currentUserId.value)
    }
  }

  @Test
  fun emptyAndWhitespaceGoogleTokensAreInvalidCredentials() = runBlocking {
    for (repo in repositories) {
      for (token in listOf("", " ", "\t\n")) {
        assertEquals(AuthError.InvalidCredentials, repo.signInWithGoogle(token).exceptionOrNull())
        assertNull(repo.currentUserId.value)
      }
    }
  }

  @Test
  fun invalidEmailTakesPrecedenceOverEmptyPassword() = runBlocking {
    for (repo in repositories) {
      assertEquals(AuthError.InvalidEmail, repo.signUpWithEmail("", "").exceptionOrNull())
      assertEquals(AuthError.InvalidEmail, repo.signInWithEmail("", "").exceptionOrNull())
      assertNull(repo.currentUserId.value)
    }
  }
}
