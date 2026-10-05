package com.github.aroundteam2026.aroundapp.data.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against the Firebase Auth emulator. Each test uses a fresh email, so tests never collide.
 *
 * Replace [useEmulatorOnce] with the shared emulator setup once mcpeblocker's CI work lands.
 */
@RunWith(AndroidJUnit4::class)
class AuthRepositoryFirebaseTest {

  private lateinit var repo: AuthRepositoryFirebase

  @Before
  fun setUp() {
    useEmulatorOnce()
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

  private fun newEmail() = "test-${UUID.randomUUID()}@around.test"

  private companion object {
    const val PASSWORD = "password123"
    // 10.0.2.2 is the host machine as seen from the Android emulator.
    const val EMULATOR_HOST = "10.0.2.2"
    const val AUTH_PORT = 9099
    var emulatorConfigured = false

    fun useEmulatorOnce() {
      if (!emulatorConfigured) {
        Firebase.auth.useEmulator(EMULATOR_HOST, AUTH_PORT)
        emulatorConfigured = true
      }
    }
  }
}
