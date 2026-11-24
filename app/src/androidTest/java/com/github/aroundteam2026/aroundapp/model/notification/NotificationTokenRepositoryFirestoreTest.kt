// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.testing.SETUP_TIMEOUT_MS
import com.github.aroundteam2026.aroundapp.testing.TIMEOUT_MS
import com.github.aroundteam2026.aroundapp.testing.emulatorClient
import com.github.aroundteam2026.aroundapp.testing.readBypassingRules
import com.github.aroundteam2026.aroundapp.testing.signInNewUser
import com.github.aroundteam2026.aroundapp.testing.withTimeoutBlocking
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestoreException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/**
 * Tests [NotificationTokenRepositoryFirestore] on the Auth and Firestore emulators, started with
 * `firebase emulators:start --only auth,firestore --project demo-project`, so firestore.rules
 * apply.
 *
 * Each test signs in a fresh user on its own [FirebaseApp] from [emulatorClient], never the default
 * app. The rules let nobody read tokens from the app, so tests read them back with
 * [readBypassingRules], as the Cloud Functions do with the admin SDK. Each test's KDoc names the
 * production bug it catches.
 */
@RunWith(AndroidJUnit4::class)
class NotificationTokenRepositoryFirestoreTest {
  /**
   * Fails any test that runs longer than its setUp and body timeouts added together, so one of
   * those reports where the test was stuck before this rule cuts it off.
   */
  @get:Rule val timeout: Timeout = Timeout.millis(RULE_TIMEOUT_MS)

  private lateinit var repository: NotificationTokenRepositoryFirestore

  /** Uid of the user signed in by [setUp]. */
  private lateinit var uid: String

  @Before
  fun setUp() =
      runBlocking<Unit> {
        withTimeout(SETUP_TIMEOUT_MS) { uid = signInNewUser(APP) }
        repository = NotificationTokenRepositoryFirestore(emulatorClient(APP))
      }

  /**
   * Bug: the token is saved somewhere the functions don't look (not as the id under
   * users/{uid}/tokens), or without the server's time in updatedAt.
   */
  @Test
  fun save_storesTheTokenAsTheIdUnderItsUserWithTheServersTime() = withTimeoutBlocking {
    val before = Instant.now()

    repository.save(uid, TOKEN).getOrThrow()

    val fields = checkNotNull(stored(TOKEN)) { "The token was not saved" }
    assertEquals(setOf(UPDATED_AT), fields.keys().asSequence().toSet())
    val updatedAt = Instant.parse(fields.getJSONObject(UPDATED_AT).getString("timestampValue"))
    assertTrue(
        "updatedAt $updatedAt is not the server's time of the save",
        Duration.between(before, updatedAt).abs() < CLOCK_TOLERANCE,
    )
  }

  /** Bug: the rules allow only a create, so saving a token the phone already saved fails. */
  @Test
  fun save_sameTokenAgainSucceeds() = withTimeoutBlocking {
    repository.save(uid, TOKEN).getOrThrow()

    repository.save(uid, TOKEN).getOrThrow()

    assertNotNull(stored(TOKEN))
  }

  /** Bug: delete removes the user's other phones' tokens too, or doesn't reach the server. */
  @Test
  fun delete_removesOnlyThatToken() = withTimeoutBlocking {
    repository.save(uid, TOKEN).getOrThrow()
    repository.save(uid, OTHER_TOKEN).getOrThrow()

    repository.delete(uid, TOKEN).getOrThrow()

    assertNull(stored(TOKEN))
    assertNotNull(stored(OTHER_TOKEN))
  }

  /** Bug: signing out of a phone whose token was never saved, or already deleted, fails. */
  @Test
  fun delete_tokenThatIsNotSavedSucceeds() = withTimeoutBlocking {
    repository.delete(uid, TOKEN).getOrThrow()

    assertNull(stored(TOKEN))
  }

  /** Bug: the rules let a user save or delete another user's tokens. */
  @Test
  fun anotherUsersTokensAreRefused() = withTimeoutBlocking {
    repository.save(uid, TOKEN).getOrThrow()
    val owner = uid
    uid = signInNewUser(APP)

    assertPermissionDenied(repository.save(owner, OTHER_TOKEN))
    assertPermissionDenied(repository.delete(owner, TOKEN))
    assertNull(stored(OTHER_TOKEN, owner))
    assertNotNull(stored(TOKEN, owner))
  }

  /**
   * Bug: a slash in an id makes the write land on another document, e.g. a token "a/tokens/b"
   * written under a uid of the user's choosing, instead of being refused before any write.
   */
  @Test
  fun invalidIdsAreRefusedBeforeWriting() = withTimeoutBlocking {
    for ((badUid, badToken) in listOf(uid to INVALID_ID, INVALID_ID to TOKEN, uid to " ")) {
      assertTrue(repository.save(badUid, badToken).exceptionOrNull() is IllegalArgumentException)
      assertTrue(repository.delete(badUid, badToken).exceptionOrNull() is IllegalArgumentException)
    }
  }

  /** The fields of [token]'s document under [owner], or null if it isn't saved. */
  private fun stored(token: String, owner: String = uid) =
      readBypassingRules("users/$owner/tokens/$token")

  private fun assertPermissionDenied(result: Result<Unit>) {
    val error = result.exceptionOrNull()
    assertTrue("Expected PERMISSION_DENIED, got $error", error is FirebaseFirestoreException)
    assertEquals(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        (error as FirebaseFirestoreException).code,
    )
  }

  private companion object {
    const val APP = "notification-token-test"

    /** Bounds a whole test: setUp and the test body, plus a margin. */
    const val RULE_TIMEOUT_MS = SETUP_TIMEOUT_MS + TIMEOUT_MS + 5_000L

    const val TOKEN = "fcm-token-1"
    const val OTHER_TOKEN = "fcm-token-2"
    const val UPDATED_AT = "updatedAt"

    /** An id the repository refuses: a slash makes it a path instead of a document id. */
    const val INVALID_ID = "a/b"

    /** How far the emulator's clock may be from the device's. */
    val CLOCK_TOLERANCE: Duration = Duration.ofMinutes(1)
  }
}
