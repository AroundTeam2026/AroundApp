// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeNotificationRegistrationTest {
  private val registration = FakeNotificationRegistration()

  @Test
  fun countsEachRegistration() = runTest {
    registration.register().getOrThrow()
    registration.register().getOrThrow()

    assertEquals(2, registration.started)
    assertEquals(2, registration.registrations)
  }

  @Test
  fun failsWithItsFailureAndDoesntCountIt() = runTest {
    val failure = IOException()
    registration.failure = failure

    assertSame(failure, registration.register().exceptionOrNull())
    assertEquals(1, registration.started)
    assertEquals(0, registration.registrations)
  }

  @Test
  fun waitsForItsGateBeforeAnswering() = runTest {
    val gate = CompletableDeferred<Unit>()
    registration.gate = gate

    val call = async { registration.register() }
    runCurrent()
    assertFalse(call.isCompleted)
    assertEquals(1, registration.started)

    gate.complete(Unit)
    assertTrue(call.await().isSuccess)
  }

  @Test
  fun givesItsPhoneId() = runTest {
    registration.phoneId = OTHER_ID

    assertEquals(OTHER_ID, registration.id().getOrThrow())
  }

  @Test
  fun givesTheDefaultIdUntilOneIsSet() = runTest {
    assertEquals(FakeNotificationRegistration.DEFAULT_PHONE_ID, registration.id().getOrThrow())
  }

  @Test
  fun failsToGiveAnIdWithItsIdFailure() = runTest {
    val failure = IOException()
    registration.idFailure = failure

    assertSame(failure, registration.id().exceptionOrNull())
  }

  private companion object {
    const val OTHER_ID = "other-phone"
  }
}
