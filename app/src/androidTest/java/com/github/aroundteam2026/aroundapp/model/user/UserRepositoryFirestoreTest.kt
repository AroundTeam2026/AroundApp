// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.user

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.testing.SETUP_TIMEOUT_MS
import com.github.aroundteam2026.aroundapp.testing.TIMEOUT_MS
import com.github.aroundteam2026.aroundapp.testing.emulatorClient
import com.github.aroundteam2026.aroundapp.testing.signInNewUser
import com.github.aroundteam2026.aroundapp.testing.withTimeoutBlocking
import com.github.aroundteam2026.aroundapp.testing.writeBypassingRules
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/**
 * Tests [UserRepositoryFirestore] on the Auth and Firestore emulators, started with `firebase
 * emulators:start --only auth,firestore --project demo-project`, so firestore.rules apply.
 *
 * Each test signs in a fresh user on its own [FirebaseApp] from [emulatorClient], never the default
 * app, and works on that user's profile, the only one the rules let them read. Writes go through
 * transactions, which the server commits before returning, so what a test reads back has been
 * accepted by the server. Each test's KDoc names the production bug it catches.
 */
@RunWith(AndroidJUnit4::class)
class UserRepositoryFirestoreTest {
  /**
   * Fails any test that runs longer than its setUp, body and tearDown timeouts added together, so
   * one of those reports where the test was stuck before this rule cuts it off.
   */
  @get:Rule val timeout: Timeout = Timeout.millis(RULE_TIMEOUT_MS)

  private lateinit var db: FirebaseFirestore
  private lateinit var repository: UserRepositoryFirestore

  /** Uid of the user signed in by [setUp]. */
  private lateinit var uid: String

  @Before
  fun setUp() =
      runBlocking<Unit> {
        withTimeout(SETUP_TIMEOUT_MS) { uid = signInNewUser(APP) }
        db = emulatorClient(APP)
        repository = UserRepositoryFirestore(db)
      }

  /** Terminates the client, so no listener or cached data leaks between tests. */
  @After
  fun tearDown() = runBlocking<Unit> { withTimeout(SETUP_TIMEOUT_MS) { db.terminate().await() } }

  /**
   * Bug: a field is dropped or mis-mapped on write or read (e.g. the role mapped to the wrong enum,
   * createdAt losing its milliseconds, the uid not taken from the document id).
   */
  @Test
  fun createUser_thenGetUser_roundTripsEveryField() = withTimeoutBlocking {
    val user = user().copy(role = Role.VENUE)

    repository.createUser(user).getOrThrow()

    assertEquals(user, repository.getUser(uid))
  }

  /**
   * Bug: the document does not follow the schema (createdAt stored as a Long, the role as an
   * ordinal) or the uid is also stored as a field. A round trip cannot see any of these.
   */
  @Test
  fun createUser_storesSchemaTypes() = withTimeoutBlocking {
    repository.createUser(user().copy(role = Role.EXPLORER)).getOrThrow()

    val document = storedProfile()

    assertEquals(setOf("displayName", "role", "createdAt"), document.data!!.keys)
    assertEquals("EXPLORER", document.get("role"))
    assertTrue(document.get("createdAt") is Timestamp)
  }

  /** Bug: creating a profile whose uid is taken replaces the stored one instead of failing. */
  @Test
  fun createUser_whenTheUidIsTaken_failsAndKeepsTheStoredProfile() = withTimeoutBlocking {
    val original = user()
    repository.createUser(original).getOrThrow()

    val result = repository.createUser(original.copy(displayName = "Other"))

    assertTrue(result.exceptionOrNull() is IllegalStateException)
    assertEquals(original, repository.getUser(uid))
  }

  /**
   * Bug: createUser checks and writes in separate steps, so of concurrent creations, e.g. from two
   * devices finishing sign-up, several succeed and the last overwrites the others, or a loser gets
   * the rules' refusal of a changed role instead of IllegalStateException.
   */
  @Test
  fun createUser_concurrently_exactlyOneSucceeds() = withTimeoutBlocking {
    val users =
        (1..CONCURRENT_CALLS).map {
          user().copy(displayName = "User $it", role = Role.entries[it % Role.entries.size])
        }

    val results = users.map { async { repository.createUser(it) } }.awaitAll()

    val winners = results.withIndex().filter { it.value.isSuccess }
    assertEquals(1, winners.size)
    assertTrue(results.all { it.isSuccess || it.exceptionOrNull() is IllegalStateException })
    assertEquals(users[winners.single().index], repository.getUser(uid))
  }

  /**
   * Bug: an exception escapes createUser, here the rules refusing a profile of another account,
   * instead of becoming a failure that is not mistaken for "already created".
   */
  @Test
  fun createUser_forAnotherAccount_failsWithPermissionDenied() = withTimeoutBlocking {
    val result = repository.createUser(user().copy(uid = "someone-else"))

    assertPermissionDenied(result)
  }

  /**
   * Bug: observeUser reads once instead of listening, so a profile created after subscribing never
   * arrives, or it fails instead of emitting null for a missing profile.
   */
  @Test
  fun observeUser_emitsNullThenTheProfileOnceCreated() = withTimeoutBlocking {
    val emissions = Channel<User?>(Channel.UNLIMITED)
    val collector =
        launch(start = CoroutineStart.UNDISPATCHED) {
          repository.observeUser(uid).collect(emissions::send)
        }
    try {
      assertNull(emissions.receive())

      repository.createUser(user()).getOrThrow()

      // A one-shot flow never emits again, so this loop never ends and the test times out.
      do {
        val latest = emissions.receive()
      } while (latest == null)
    } finally {
      collector.cancel()
    }
  }

  /**
   * Bug: an id Firestore refuses makes observeUser throw when called, before the caller's catch on
   * the flow can see it, instead of failing the flow.
   */
  @Test
  fun observeUser_withAnInvalidUid_failsTheFlowInsteadOfThrowing() = withTimeoutBlocking {
    val user = repository.observeUser(INVALID_UID)

    assertTrue(runCatching { user.first() }.exceptionOrNull() is IllegalArgumentException)
  }

  /** Bug: getUser throws for a profile that does not exist instead of returning null. */
  @Test
  fun getUser_whenMissing_returnsNull() = withTimeoutBlocking {
    assertNull(repository.getUser(uid))
  }

  /** Bug: a malformed document makes getUser throw instead of reading it as missing. */
  @Test
  fun getUser_readsAMalformedDocumentAsMissing() = withTimeoutBlocking {
    // No displayName or createdAt; the rules only check the owner and the role.
    // No displayName or createdAt: the rules refuse it, so it goes past them
    writeBypassingRules("$USERS/$uid", mapOf("role" to "EXPLORER"))

    assertNull(repository.getUser(uid))
  }

  /** Bug: setRole writes fields other than the role, or writes another role than asked. */
  @Test
  fun setRole_onAProfileWithoutRole_setsOnlyTheRole() = withTimeoutBlocking {
    repository.createUser(user()).getOrThrow()

    repository.setRole(uid, Role.VENUE).getOrThrow()

    assertEquals(user().copy(role = Role.VENUE), repository.getUser(uid))
  }

  /**
   * Bug: setRole replaces a role already set, or reports it with an exception other than
   * IllegalStateException, which ProfileSetup reads as "set by another device".
   */
  @Test
  fun setRole_whenARoleIsSet_failsAndKeepsIt() = withTimeoutBlocking {
    repository.createUser(user().copy(role = Role.EXPLORER)).getOrThrow()

    val result = repository.setRole(uid, Role.VENUE)

    assertTrue(result.exceptionOrNull() is IllegalStateException)
    assertEquals(Role.EXPLORER, repository.getUser(uid)!!.role)
  }

  /** Bug: setRole on a missing profile creates a partial one, or does not report the failure. */
  @Test
  fun setRole_forAMissingUser_failsWithoutCreatingIt() = withTimeoutBlocking {
    val result = repository.setRole(uid, Role.VENUE)

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    assertFalse(storedProfile().exists())
  }

  /**
   * Bug: setRole checks and writes in separate steps, so of two devices choosing different roles at
   * once, both succeed, or the loser gets the rules' refusal instead of IllegalStateException.
   */
  @Test
  fun setRole_concurrently_exactlyOneSucceeds() = withTimeoutBlocking {
    repository.createUser(user()).getOrThrow()
    val roles = (1..CONCURRENT_CALLS).map { Role.entries[it % Role.entries.size] }

    val results = roles.map { async { repository.setRole(uid, it) } }.awaitAll()

    val winners = results.withIndex().filter { it.value.isSuccess }
    assertEquals(1, winners.size)
    assertTrue(results.all { it.isSuccess || it.exceptionOrNull() is IllegalStateException })
    assertEquals(roles[winners.single().index], repository.getUser(uid)!!.role)
  }

  /** A profile without a role for the signed-in user, as ProfileSetup creates it. */
  private fun user() = User(uid = uid, displayName = "Ada", role = null, createdAt = CREATED_AT)

  /** The signed-in user's profile document, read from the server. */
  private suspend fun storedProfile() =
      db.collection(USERS).document(uid).get(Source.SERVER).await()

  /** Asserts [result] is a failure caused by the security rules refusing the write. */
  private fun assertPermissionDenied(result: Result<*>) {
    val error = result.exceptionOrNull()
    assertTrue("Expected a Firestore exception but got $error", error is FirebaseFirestoreException)
    assertEquals(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        (error as FirebaseFirestoreException).code,
    )
  }

  private companion object {
    /** The Firestore collection the repository stores profiles in; some tests use it directly. */
    const val USERS = "users"

    const val APP = "user-test"

    /** Bounds a whole test: setUp, the test body and tearDown, plus a margin. */
    const val RULE_TIMEOUT_MS = SETUP_TIMEOUT_MS + TIMEOUT_MS + SETUP_TIMEOUT_MS + 5_000L

    /** Creation time of every profile, in epoch ms; the non-zero ms catch precision loss. */
    const val CREATED_AT = 1_700_000_000_123L

    /** How many writes a concurrency test races against each other. */
    const val CONCURRENT_CALLS = 10

    /** An id Firestore refuses: a slash makes it a path instead of a document id. */
    const val INVALID_UID = "a/b"
  }
}
