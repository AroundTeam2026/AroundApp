// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FakeNotificationTokenSourceTest {
  private val source = FakeNotificationTokenSource(TOKEN)

  @Test fun givesItsToken() = runTest { assertEquals(TOKEN, source.currentToken().getOrThrow()) }

  @Test
  fun givesTheTokenItWasChangedTo() = runTest {
    source.token = NEW_TOKEN

    assertEquals(NEW_TOKEN, source.currentToken().getOrThrow())
  }

  @Test
  fun failsWithItsFailureWhenSet() = runTest {
    val failure = IOException()
    source.failure = failure

    assertSame(failure, source.currentToken().exceptionOrNull())
  }

  private companion object {
    const val TOKEN = "token-1"
    const val NEW_TOKEN = "token-2"
  }
}
