// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRegistrationFirebaseTest {
  private val messaging = mockk<FirebaseMessaging>()
  private val registration = NotificationRegistrationFirebase(messaging)

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
}
