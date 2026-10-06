package com.github.aroundteam2026.aroundapp.model.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
  fun newAccountsNeverReuseTheInitialUserId() = runBlocking {
    val startedSignedIn = FakeAuthRepository(signedInUserId = "fake-uid-1")
    val emailUid = startedSignedIn.signUpWithEmail("ada@around.test", PASSWORD).getOrThrow()
    val googleUid = startedSignedIn.signInWithGoogle("token-a").getOrThrow()
    val addedUid = startedSignedIn.addAccount("bob@around.test", PASSWORD)
    assertEquals(4, setOf("fake-uid-1", emailUid, googleUid, addedUid).size)
  }

  @Test
  fun signUpAcceptsAPasswordOfExactlyTheMinimumLength() = runBlocking {
    assertTrue(repo.signUpWithEmail("ada@around.test", "123456").isSuccess)
  }

  @Test
  fun signInIgnoresEmailCaseAndSurroundingSpaces() = runBlocking {
    val uid = repo.addAccount("ada@around.test", PASSWORD)
    assertEquals(uid, repo.signInWithEmail("  ADA@Around.Test ", PASSWORD).getOrThrow())
  }

  @Test
  fun signInTreatsPasswordsAsCaseSensitive() = runBlocking {
    repo.addAccount("ada@around.test", "Password123")
    val result = repo.signInWithEmail("ada@around.test", "password123")
    assertEquals(AuthError.InvalidCredentials, result.exceptionOrNull())
  }

  @Test
  fun signInRejectsMalformedEmail() = runBlocking {
    assertEquals(
        AuthError.InvalidEmail,
        repo.signInWithEmail("not-an-email", PASSWORD).exceptionOrNull(),
    )
  }

  @Test
  fun failedSignUpDoesNotCreateAnAccount() = runBlocking {
    repo.signUpWithEmail("ada@around.test", "12345")
    val result = repo.signInWithEmail("ada@around.test", "12345")
    assertEquals(AuthError.InvalidCredentials, result.exceptionOrNull())
  }

  @Test
  fun failedSignUpKeepsTheCurrentSession() = runBlocking {
    val uid = repo.signUpWithEmail("ada@around.test", PASSWORD).getOrThrow()
    repo.signUpWithEmail("ada@around.test", PASSWORD)
    repo.signUpWithEmail("bob@around.test", "12345")
    repo.signUpWithEmail("not-an-email", PASSWORD)
    assertEquals(uid, repo.currentUserId.value)
  }

  @Test
  fun failedSignInKeepsTheCurrentSession() = runBlocking {
    val uid = repo.signUpWithEmail("ada@around.test", PASSWORD).getOrThrow()
    repo.addAccount("bob@around.test", PASSWORD)
    repo.signInWithEmail("bob@around.test", "wrong-password")
    repo.signInWithEmail("nobody@around.test", PASSWORD)
    repo.signInWithGoogle(" ")
    assertEquals(uid, repo.currentUserId.value)
  }

  @Test
  fun forcedErrorChangesNothing() = runBlocking {
    val uid = repo.signUpWithEmail("ada@around.test", PASSWORD).getOrThrow()
    repo.nextError = AuthError.Network
    repo.signUpWithEmail("bob@around.test", PASSWORD)
    assertEquals(uid, repo.currentUserId.value)
    // Bob's sign-up was rejected before anything was stored, so the email is still free.
    assertTrue(repo.signUpWithEmail("bob@around.test", PASSWORD).isSuccess)
  }

  @Test
  fun concurrentSignUpsWithTheSameEmailCreateOnlyOneAccount() = runBlocking {
    val results =
        (1..CONCURRENT_CALLS)
            .map {
              async(Dispatchers.Default) { repo.signUpWithEmail("ada@around.test", PASSWORD) }
            }
            .awaitAll()
    assertEquals(1, results.count { it.isSuccess })
    assertTrue(
        results.filter { it.isFailure }.all { it.exceptionOrNull() == AuthError.EmailAlreadyInUse }
    )
  }

  @Test
  fun concurrentSignUpsGetDistinctUids() = runBlocking {
    val uids =
        (1..CONCURRENT_CALLS)
            .map { i ->
              async(Dispatchers.Default) {
                repo.signUpWithEmail("user$i@around.test", PASSWORD).getOrThrow()
              }
            }
            .awaitAll()
    assertEquals(CONCURRENT_CALLS, uids.toSet().size)
  }

  @Test
  fun canStartSignedIn() {
    assertEquals("uid-1", FakeAuthRepository(signedInUserId = "uid-1").currentUserId.value)
  }

  private companion object {
    const val PASSWORD = "password123"
    const val CONCURRENT_CALLS = 100
  }
}
