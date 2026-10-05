package com.github.aroundteam2026.aroundapp.data.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAuthRepositoryTest {

  private val repo = FakeAuthRepository()

  @Test
  fun startsSignedOut() {
    assertNull(repo.currentUserId.value)
  }

  @Test
  fun signUpSignsTheUserIn() = runBlocking {
    val uid = repo.signUpWithEmail("ada@around.test", PASSWORD).getOrThrow()
    assertEquals(uid, repo.currentUserId.value)
  }

  @Test
  fun signUpRejectsInvalidEmail() = runBlocking {
    val result = repo.signUpWithEmail("not-an-email", PASSWORD)
    assertEquals(AuthError.InvalidEmail, result.exceptionOrNull())
    assertNull(repo.currentUserId.value)
  }

  @Test
  fun signUpRejectsShortPassword() = runBlocking {
    val result = repo.signUpWithEmail("ada@around.test", "12345")
    assertEquals(AuthError.WeakPassword, result.exceptionOrNull())
  }

  @Test
  fun signUpRejectsTakenEmailIgnoringCase() = runBlocking {
    repo.addAccount("ada@around.test", PASSWORD)
    val result = repo.signUpWithEmail("  ADA@around.test ", PASSWORD)
    assertEquals(AuthError.EmailAlreadyInUse, result.exceptionOrNull())
  }

  @Test
  fun signInWithCorrectPasswordReturnsTheAccountUid() = runBlocking {
    val uid = repo.addAccount("ada@around.test", PASSWORD)
    assertEquals(uid, repo.signInWithEmail("ada@around.test", PASSWORD).getOrThrow())
    assertEquals(uid, repo.currentUserId.value)
  }

  @Test
  fun signInWithWrongPasswordFails() = runBlocking {
    repo.addAccount("ada@around.test", PASSWORD)
    val result = repo.signInWithEmail("ada@around.test", "wrong-password")
    assertEquals(AuthError.InvalidCredentials, result.exceptionOrNull())
    assertNull(repo.currentUserId.value)
  }

  @Test
  fun signInWithUnknownEmailFailsLikeWrongPassword() = runBlocking {
    val result = repo.signInWithEmail("nobody@around.test", PASSWORD)
    assertEquals(AuthError.InvalidCredentials, result.exceptionOrNull())
  }

  @Test
  fun googleSignInKeepsTheSameUidPerToken() = runBlocking {
    val first = repo.signInWithGoogle("token-a").getOrThrow()
    val again = repo.signInWithGoogle("token-a").getOrThrow()
    val other = repo.signInWithGoogle("token-b").getOrThrow()
    assertEquals(first, again)
    assertNotEquals(first, other)
  }

  @Test
  fun googleSignInRejectsBlankToken() = runBlocking {
    assertEquals(AuthError.InvalidCredentials, repo.signInWithGoogle(" ").exceptionOrNull())
  }

  @Test
  fun signOutClearsTheCurrentUser() = runBlocking {
    repo.signUpWithEmail("ada@around.test", PASSWORD)
    repo.signOut()
    assertNull(repo.currentUserId.value)
  }

  @Test
  fun nextErrorFailsOneCallThenClears() = runBlocking {
    repo.nextError = AuthError.Network
    assertEquals(
        AuthError.Network,
        repo.signUpWithEmail("ada@around.test", PASSWORD).exceptionOrNull(),
    )
    assertTrue(repo.signUpWithEmail("ada@around.test", PASSWORD).isSuccess)
  }

  @Test
  fun canStartSignedIn() {
    assertEquals("uid-1", FakeAuthRepository(signedInUserId = "uid-1").currentUserId.value)
  }

  private companion object {
    const val PASSWORD = "password123"
  }
}
