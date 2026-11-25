// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import java.io.IOException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTokenRegistrarTest {
  private val tokens = FakeNotificationTokenRepository()
  private val source = FakeNotificationTokenSource(TOKEN)

  /** A registrar for [auth] whose saves run in the test's background scope. */
  private fun TestScope.registrar(auth: FakeAuthRepository) =
      NotificationTokenRegistrar(auth, tokens, source, backgroundScope)

  @Test
  fun newTokenIsSavedForTheSignedInUser() = runTest {
    registrar(FakeAuthRepository(UID)).onNewToken(NEW_TOKEN)
    runCurrent()

    assertEquals(setOf(NEW_TOKEN), tokens.tokensOf(UID))
  }

  @Test
  fun newTokenIsNotSavedForTheUserWhoSignedOut() = runTest {
    val auth = FakeAuthRepository(UID)
    auth.signOut()

    registrar(auth).onNewToken(NEW_TOKEN)
    runCurrent()

    assertTrue(tokens.tokensOf(UID).isEmpty())
  }

  @Test
  fun signingInSavesTheCurrentToken() = runTest {
    val auth = FakeAuthRepository()
    val uid = auth.addAccount(EMAIL, PASSWORD)
    registrar(auth).start()
    runCurrent()

    auth.signInWithEmail(EMAIL, PASSWORD).getOrThrow()
    runCurrent()

    assertEquals(setOf(TOKEN), tokens.tokensOf(uid))
  }

  @Test
  fun restoredSessionSavesAtStart() = runTest {
    registrar(FakeAuthRepository(UID)).start()
    runCurrent()

    assertEquals(setOf(TOKEN), tokens.tokensOf(UID))
  }

  @Test
  fun signingInAgainAfterSignOutSavesAgain() = runTest {
    val auth = FakeAuthRepository()
    val uid = auth.addAccount(EMAIL, PASSWORD)
    registrar(auth).start()
    auth.signInWithEmail(EMAIL, PASSWORD).getOrThrow()
    runCurrent()
    // What the sign-out of #636 does
    tokens.delete(uid, TOKEN).getOrThrow()
    auth.signOut()
    runCurrent()

    auth.signInWithEmail(EMAIL, PASSWORD).getOrThrow()
    runCurrent()

    assertEquals(setOf(TOKEN), tokens.tokensOf(uid))
  }

  @Test
  fun startingTwiceStillSavesOnce() = runTest {
    val auth = FakeAuthRepository(UID)
    val registrar = registrar(auth)
    registrar.start()
    runCurrent()
    // A second watcher would save the changed token too
    source.token = NEW_TOKEN

    registrar.start()
    runCurrent()

    assertEquals(setOf(TOKEN), tokens.tokensOf(UID))
  }

  @Test
  fun tokenSourceFailureSavesNothing() = runTest {
    source.failure = IOException()

    registrar(FakeAuthRepository(UID)).start()
    runCurrent()

    assertTrue(tokens.tokensOf(UID).isEmpty())
  }

  @Test
  fun failedSaveDoesNotStopTheNextOne() = runTest {
    val registrar = registrar(FakeAuthRepository(UID))
    tokens.failure = IOException()
    registrar.onNewToken(TOKEN)
    runCurrent()
    tokens.failure = null

    registrar.onNewToken(NEW_TOKEN)
    runCurrent()

    assertEquals(setOf(NEW_TOKEN), tokens.tokensOf(UID))
  }

  private companion object {
    const val UID = "user-1"
    const val EMAIL = "ada@test.com"
    const val PASSWORD = "password123"
    const val TOKEN = "token-1"
    const val NEW_TOKEN = "token-2"
  }
}
