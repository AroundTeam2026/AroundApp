// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTokenRegistrarTest {
  private val tokens = FakeNotificationTokenRepository()
  private val registration = FakeNotificationRegistration()

  /** A registrar for [auth] whose registrations and saves run in the test's background scope. */
  private fun TestScope.registrar(auth: FakeAuthRepository) =
      NotificationTokenRegistrar(auth, tokens, registration, backgroundScope)

  @Test
  fun registeredIdIsSavedForTheSignedInUser() = runTest {
    registrar(FakeAuthRepository(UID)).onRegistered(ID)
    runCurrent()

    assertEquals(setOf(ID), tokens.tokensOf(UID))
  }

  @Test
  fun registeredIdIsNotSavedForTheUserWhoSignedOut() = runTest {
    val auth = FakeAuthRepository(UID)
    auth.signOut()

    registrar(auth).onRegistered(ID)
    runCurrent()

    // No user at all, not only the one who left: a save under any uid would fail this
    assertTrue(tokens.usersWithTokens.isEmpty())
  }

  @Test
  fun restoredSessionRegistersAtStart() = runTest {
    registrar(FakeAuthRepository(UID)).start()
    runCurrent()

    assertEquals(1, registration.registrations)
  }

  @Test
  fun signedOutAtStartDoesntRegister() = runTest {
    registrar(FakeAuthRepository()).start()
    runCurrent()

    assertEquals(0, registration.registrations)
  }

  @Test
  fun eachSignInRegistersAgain() = runTest {
    val auth = FakeAuthRepository()
    auth.addAccount(EMAIL, PASSWORD)
    registrar(auth).start()
    auth.signInWithEmail(EMAIL, PASSWORD).getOrThrow()
    runCurrent()
    auth.signOut()
    runCurrent()

    auth.signInWithEmail(EMAIL, PASSWORD).getOrThrow()
    runCurrent()

    assertEquals(2, registration.registrations)
  }

  @Test
  fun signingOutStopsWaitingOnTheRegistration() = runTest {
    val gate = CompletableDeferred<Unit>()
    registration.gate = gate
    val auth = FakeAuthRepository(UID)
    registrar(auth).start()
    runCurrent()

    auth.signOut()
    runCurrent()
    gate.complete(Unit)
    runCurrent()

    // Started for the signed-in user, then not waited on once they left
    assertEquals(1, registration.started)
    assertEquals(0, registration.registrations)
  }

  @Test
  fun startingTwiceRegistersOnce() = runTest {
    val registrar = registrar(FakeAuthRepository(UID))
    registrar.start()
    runCurrent()

    registrar.start()
    runCurrent()

    assertEquals(1, registration.registrations)
  }

  @Test
  fun failedRegistrationDoesntStopTheNextSignIn() = runTest {
    val auth = FakeAuthRepository()
    auth.addAccount(EMAIL, PASSWORD)
    registrar(auth).start()
    registration.failure = IOException()
    auth.signInWithEmail(EMAIL, PASSWORD).getOrThrow()
    runCurrent()
    auth.signOut()
    runCurrent()
    registration.failure = null

    auth.signInWithEmail(EMAIL, PASSWORD).getOrThrow()
    runCurrent()

    assertEquals(1, registration.registrations)
  }

  @Test
  fun failedSaveDoesNotStopTheNextOne() = runTest {
    val registrar = registrar(FakeAuthRepository(UID))
    tokens.failure = IOException()
    registrar.onRegistered(ID)
    runCurrent()
    tokens.failure = null

    registrar.onRegistered(NEW_ID)
    runCurrent()

    assertEquals(setOf(NEW_ID), tokens.tokensOf(UID))
  }

  private companion object {
    const val UID = "user-1"
    const val EMAIL = "ada@test.com"
    const val PASSWORD = "password123"
    const val ID = "installation-1"
    const val NEW_ID = "installation-2"
  }
}
