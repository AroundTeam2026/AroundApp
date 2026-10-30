// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.model.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.auth.AdditionalUserInfo
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Holds Firebase's credential task open to check session changes during Google signup. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AuthRepositoryFirebaseGoogleSignupTest {

  private class PendingFirebase(signedIn: Boolean = false) {
    val auth = mockk<FirebaseAuth>(relaxed = true)
    val credential = TaskCompletionSource<AuthResult>()
    private val user = mockk<FirebaseUser> { every { uid } returns "new-google-uid" }
    private var currentUser: FirebaseUser? = user.takeIf { signedIn }

    init {
      every { auth.currentUser } answers { currentUser }
      every { auth.signInWithCredential(any()) } returns credential.task
      every { auth.signOut() } answers { currentUser = null }
    }

    val repository = AuthRepositoryFirebase(auth)

    fun completeNewAccount() {
      val info = mockk<AdditionalUserInfo> { every { isNewUser } returns true }
      val result =
          mockk<AuthResult> {
            every { user } returns this@PendingFirebase.user
            every { additionalUserInfo } returns info
          }
      currentUser = user
      credential.setResult(result)
    }

    fun completeWithoutUser() {
      val result = mockk<AuthResult> { every { user } returns null }
      credential.setResult(result)
    }
  }

  @Test
  fun signupCannotReplaceAnAlreadySignedInAccount() = runTest {
    val firebase = PendingFirebase(signedIn = true)

    assertEquals(
        AuthError.InvalidCredentials,
        firebase.repository.signUpWithGoogle("other-token").exceptionOrNull(),
    )
    assertEquals("new-google-uid", firebase.repository.currentUserId.value)
    verify(exactly = 0) { firebase.auth.signInWithCredential(any()) }
  }

  @Test
  fun signOutDuringPendingSignupDoesNotRestoreTheSession() = runTest {
    val firebase = PendingFirebase()
    val request = async { firebase.repository.signUpWithGoogle("token") }
    runCurrent()

    firebase.repository.signOut()
    firebase.completeNewAccount()
    runCurrent()

    assertEquals(AuthError.InvalidCredentials, request.await().exceptionOrNull())
    assertNull(firebase.repository.currentUserId.value)
    verify(exactly = 2) { firebase.auth.signOut() }
  }

  @Test
  fun cancellationAfterFirebaseStartsClearsItsLateSession() = runTest {
    val firebase = PendingFirebase()
    val request = launch { firebase.repository.signUpWithGoogle("token") }
    runCurrent()

    request.cancel()
    firebase.completeNewAccount()
    runCurrent()
    request.join()

    assertTrue(request.isCancelled)
    assertNull(firebase.repository.currentUserId.value)
    verify(exactly = 1) { firebase.auth.signOut() }
  }

  @Test
  fun aSecondSignupCannotReplaceTheFirstPendingCredential() = runTest {
    val firebase = PendingFirebase()
    val first = async { firebase.repository.signUpWithGoogle("first-token") }
    runCurrent()

    assertEquals(
        AuthError.InvalidCredentials,
        firebase.repository.signUpWithGoogle("second-token").exceptionOrNull(),
    )
    verify(exactly = 1) { firebase.auth.signInWithCredential(any()) }

    firebase.completeNewAccount()
    runCurrent()
    assertEquals("new-google-uid", first.await().getOrThrow())
    assertEquals("new-google-uid", firebase.repository.currentUserId.value)
  }

  @Test
  fun firebaseFailureLeavesNoAppSession() = runTest {
    val firebase = PendingFirebase()
    val request = async { firebase.repository.signUpWithGoogle("token") }
    runCurrent()

    firebase.credential.setException(IllegalStateException("Firebase failed"))
    runCurrent()

    assertTrue(request.await().exceptionOrNull() is AuthError.Unknown)
    assertNull(firebase.repository.currentUserId.value)
    verify(exactly = 1) { firebase.auth.signOut() }
  }

  @Test
  fun firebaseResultWithoutAUserCannotStartASession() = runTest {
    val firebase = PendingFirebase()
    val request = async { firebase.repository.signUpWithGoogle("token") }
    runCurrent()

    firebase.completeWithoutUser()
    runCurrent()

    assertTrue(request.await().exceptionOrNull() is AuthError.Unknown)
    assertNull(firebase.repository.currentUserId.value)
    verify(exactly = 1) { firebase.auth.signOut() }
  }
}
