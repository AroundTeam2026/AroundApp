// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeNotificationTokenRepositoryTest {
  private val repository = FakeNotificationTokenRepository()

  @Test
  fun savingATokenAgainKeepsItOnce() = runTest {
    repository.save(UID, TOKEN).getOrThrow()
    repository.save(UID, TOKEN).getOrThrow()

    assertEquals(setOf(TOKEN), repository.tokensOf(UID))
  }

  @Test
  fun eachPhoneOfAUserKeepsItsOwnToken() = runTest {
    repository.save(UID, TOKEN).getOrThrow()
    repository.save(UID, OTHER_TOKEN).getOrThrow()
    repository.save(OTHER_UID, TOKEN).getOrThrow()

    assertEquals(setOf(TOKEN, OTHER_TOKEN), repository.tokensOf(UID))
    assertEquals(setOf(TOKEN), repository.tokensOf(OTHER_UID))
  }

  @Test
  fun deleteRemovesOnlyThatTokenOfThatUser() = runTest {
    repository.save(UID, TOKEN).getOrThrow()
    repository.save(UID, OTHER_TOKEN).getOrThrow()
    repository.save(OTHER_UID, TOKEN).getOrThrow()

    repository.delete(UID, TOKEN).getOrThrow()

    assertEquals(setOf(OTHER_TOKEN), repository.tokensOf(UID))
    assertEquals(setOf(TOKEN), repository.tokensOf(OTHER_UID))
  }

  @Test
  fun usersWithTokensLeavesOutAUserWhoseTokensWereAllDeleted() = runTest {
    repository.save(UID, TOKEN).getOrThrow()
    repository.save(OTHER_UID, TOKEN).getOrThrow()

    repository.delete(OTHER_UID, TOKEN).getOrThrow()

    assertEquals(setOf(UID), repository.usersWithTokens)
  }

  @Test
  fun deletingATokenThatIsNotSavedSucceeds() = runTest {
    assertTrue(repository.delete(UID, TOKEN).isSuccess)
    assertEquals(emptySet<String>(), repository.tokensOf(UID))
  }

  @Test
  fun failureIsReturnedAndChangesNothing() = runTest {
    repository.save(UID, TOKEN).getOrThrow()
    val error = IllegalStateException("offline")
    repository.failure = error

    assertSame(error, repository.save(UID, OTHER_TOKEN).exceptionOrNull())
    assertSame(error, repository.delete(UID, TOKEN).exceptionOrNull())
    assertEquals(setOf(TOKEN), repository.tokensOf(UID))
  }

  @Test
  fun invalidIdsFailWithoutSaving() = runTest {
    for ((uid, token) in INVALID_PATHS) {
      assertTrue(repository.save(uid, token).exceptionOrNull() is IllegalArgumentException)
      assertTrue(repository.delete(uid, token).exceptionOrNull() is IllegalArgumentException)
      assertEquals(emptySet<String>(), repository.tokensOf(uid))
    }
  }

  private companion object {
    const val UID = "user-1"
    const val OTHER_UID = "user-2"
    const val TOKEN = "token-1"
    const val OTHER_TOKEN = "token-2"

    /** A blank or slashed uid or token, each paired with a valid other half. */
    val INVALID_PATHS =
        listOf("" to TOKEN, " " to TOKEN, "a/b" to TOKEN, UID to "", UID to " ", UID to "a/b")
  }
}
