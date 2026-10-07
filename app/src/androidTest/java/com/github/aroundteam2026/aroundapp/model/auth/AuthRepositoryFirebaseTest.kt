// Co-authored-by: OpenAI Codex
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.testing.FirebaseEmulator
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against the Firebase Auth emulator. Each test uses a fresh email or Google account, so tests
 * never collide.
 */
@RunWith(AndroidJUnit4::class)
class AuthRepositoryFirebaseTest {

  private lateinit var repo: AuthRepositoryFirebase

  @Before
  fun setUp() {
    FirebaseEmulator.connect()
    Firebase.auth.signOut()
    repo = AuthRepositoryFirebase(Firebase.auth)
  }

  @Test
  fun signUpSignsTheUserIn() = runBlocking {
    val uid = repo.signUpWithEmail(newEmail(), PASSWORD).getOrThrow()
    assertEquals(uid, repo.currentUserId.value)
  }

  @Test
  fun signUpWithTakenEmailFails() = runBlocking {
    val email = newEmail()
    repo.signUpWithEmail(email, PASSWORD).getOrThrow()
    repo.signOut()
    assertEquals(
        AuthError.EmailAlreadyInUse,
        repo.signUpWithEmail(email, PASSWORD).exceptionOrNull(),
    )
  }

  @Test
  fun signUpWithShortPasswordFails() = runBlocking {
    assertEquals(
        AuthError.WeakPassword,
        repo.signUpWithEmail(newEmail(), "12345").exceptionOrNull(),
    )
  }

  @Test
  fun signInReturnsTheSameUidAsSignUp() = runBlocking {
    val email = newEmail()
    val uid = repo.signUpWithEmail(email, PASSWORD).getOrThrow()
    repo.signOut()
    assertEquals(uid, repo.signInWithEmail(email, PASSWORD).getOrThrow())
  }

  @Test
  fun signInWithWrongPasswordFails() = runBlocking {
    val email = newEmail()
    repo.signUpWithEmail(email, PASSWORD).getOrThrow()
    repo.signOut()
    val result = repo.signInWithEmail(email, "wrong-password")
    assertEquals(AuthError.InvalidCredentials, result.exceptionOrNull())
    assertNull(repo.currentUserId.value)
  }

  @Test
  fun signOutClearsTheCurrentUser() = runBlocking {
    repo.signUpWithEmail(newEmail(), PASSWORD).getOrThrow()
    repo.signOut()
    assertNull(repo.currentUserId.value)
  }

  @Test
  fun googleSignInSignsTheUserIn() = runBlocking {
    val uid = repo.signInWithGoogle(fakeGoogleIdToken()).getOrThrow()
    assertEquals(uid, repo.currentUserId.value)
  }

  @Test
  fun googleSignInReturnsTheSameUidForTheSameAccount() = runBlocking {
    val token = fakeGoogleIdToken()
    val uid = repo.signInWithGoogle(token).getOrThrow()
    repo.signOut()
    assertEquals(uid, repo.signInWithGoogle(token).getOrThrow())
  }

  @Test
  fun newRepositoryStartsWithTheAlreadySignedInUser() = runBlocking {
    // Simulates reopening the app: Firebase still holds the session, so a fresh repository
    // must report that user straight away, without a new sign-in.
    val uid = repo.signUpWithEmail(newEmail(), PASSWORD).getOrThrow()
    assertEquals(uid, AuthRepositoryFirebase(Firebase.auth).currentUserId.value)
  }

  @Test
  fun signOutOutsideTheRepositoryClearsTheCurrentUser() = runBlocking {
    // Covers sessions that end without going through the repository, such as a deleted account.
    // Only Firebase's auth state listener can notice those.
    repo.signUpWithEmail(newEmail(), PASSWORD).getOrThrow()
    Firebase.auth.signOut()
    withTimeout(LISTENER_TIMEOUT_MS) { repo.currentUserId.first { it == null } }
    assertNull(repo.currentUserId.value)
  }

  @Test
  fun invalidInputsKeepTheCurrentSession() = runBlocking {
    val email = newEmail()
    val uid = repo.signUpWithEmail(email, PASSWORD).getOrThrow()
    val cases =
        listOf<Pair<AuthError, suspend () -> Result<String>>>(
            AuthError.InvalidEmail to { repo.signUpWithEmail("", PASSWORD) },
            AuthError.InvalidEmail to { repo.signInWithEmail(" ", PASSWORD) },
            AuthError.InvalidEmail to { repo.signUpWithEmail("not-an-email", PASSWORD) },
            AuthError.InvalidEmail to { repo.signInWithEmail("not-an-email", PASSWORD) },
            AuthError.WeakPassword to { repo.signUpWithEmail(newEmail(), "") },
            AuthError.WeakPassword to { repo.signUpWithEmail(newEmail(), "12345") },
            AuthError.InvalidCredentials to { repo.signInWithEmail(email, "") },
            AuthError.InvalidCredentials to { repo.signInWithGoogle("") },
            AuthError.InvalidCredentials to { repo.signInWithGoogle(" ") },
        )
    for ((expected, call) in cases) {
      assertEquals(expected, call().exceptionOrNull())
      assertEquals(uid, repo.currentUserId.value)
      assertEquals(uid, Firebase.auth.currentUser?.uid)
    }
  }

  @Test
  fun failedFirebaseAuthenticationKeepsTheCurrentSession() = runBlocking {
    val email = newEmail()
    repo.signUpWithEmail(email, PASSWORD).getOrThrow()
    val uid = repo.signUpWithEmail(newEmail(), PASSWORD).getOrThrow()
    val cases =
        listOf<Pair<AuthError, suspend () -> Result<String>>>(
            AuthError.EmailAlreadyInUse to { repo.signUpWithEmail(email, "other-password") },
            AuthError.InvalidCredentials to { repo.signInWithEmail(email, "wrong-password") },
            AuthError.InvalidCredentials to { repo.signInWithEmail(newEmail(), PASSWORD) },
        )
    for ((expected, call) in cases) {
      assertEquals(expected, call().exceptionOrNull())
      assertEquals(uid, repo.currentUserId.value)
      assertEquals(uid, Firebase.auth.currentUser?.uid)
    }
  }

  private fun newEmail() = "test-${UUID.randomUUID()}@around.test"

  /**
   * The Auth emulator accepts unsigned JSON in place of a real Google ID token, so tests can sign
   * in with Google without a Google account.
   */
  private fun fakeGoogleIdToken(): String {
    val sub = UUID.randomUUID().toString()
    return """{"sub": "$sub", "email": "$sub@around.test", "email_verified": true}"""
  }

  private companion object {
    const val PASSWORD = "password123"
    const val LISTENER_TIMEOUT_MS = 5_000L
  }
}
