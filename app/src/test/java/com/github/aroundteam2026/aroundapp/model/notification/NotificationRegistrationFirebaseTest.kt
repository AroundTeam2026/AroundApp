// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRegistrationFirebaseTest {
  private val messaging = mockk<FirebaseMessaging>()
  private val installations = mockk<FirebaseInstallations>()
  private val registration = NotificationRegistrationFirebase(messaging, installations)

  @Test
  fun succeedsWhenFcmRegisters() = runTest {
    every { messaging.register() } returns Tasks.forResult(null)

    assertTrue(registration.register().isSuccess)
  }

  @Test
  fun failsWithFcmsError() = runTest {
    val error = IOException()
    every { messaging.register() } returns Tasks.forException(error)

    assertSame(error, registration.register().exceptionOrNull())
  }

  @Test
  fun cancellingTheCallerCancelsTheWait() = runTest {
    every { messaging.register() } returns TaskCompletionSource<Void>().task
    val call = launch { registration.register() }
    runCurrent()

    call.cancel()
    runCurrent()

    assertTrue(call.isCancelled)
  }

  @Test
  fun givesThePhonesInstallationId() = runTest {
    every { installations.id } returns Tasks.forResult(PHONE_ID)

    assertEquals(PHONE_ID, registration.id().getOrThrow())
  }

  @Test
  fun failsToGiveAnIdWithTheSdksError() = runTest {
    val error = IOException()
    every { installations.id } returns Tasks.forException(error)

    assertSame(error, registration.id().exceptionOrNull())
  }

  @Test
  fun failsToGiveAnIdWhenTheSdkThrows() = runTest {
    val error = IllegalStateException()
    every { installations.id } throws error

    assertSame(error, registration.id().exceptionOrNull())
  }

  @Test
  fun cancellingTheCallerCancelsTheWaitForTheId() = runTest {
    every { installations.id } returns TaskCompletionSource<String>().task
    val call = launch { registration.id() }
    runCurrent()

    call.cancel()
    runCurrent()

    assertTrue(call.isCancelled)
  }

  private companion object {
    const val PHONE_ID = "phone-installation-id"
  }
}
