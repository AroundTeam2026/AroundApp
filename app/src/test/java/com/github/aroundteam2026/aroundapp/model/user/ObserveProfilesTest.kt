// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.user

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests [observeProfiles] on a [FakeUserRepository]. */
class ObserveProfilesTest {
  private val ada = User("ada", "Ada Lovelace", Role.EXPLORER, createdAt = 0L)
  private val users = FakeUserRepository().apply { runBlocking { createUser(ada) } }

  /** The first profiles [repository] gives for [uids], or a failure instead of a hang. */
  private suspend fun profilesOf(vararg uids: String, repository: UserRepository = users) =
      withTimeout(TIMEOUT_MS) { repository.observeProfiles(uids.toList()).first() }

  @Test
  fun givesEachProfileByUidAndNullForAMissingOne() = runBlocking {
    assertEquals(mapOf("ada" to ada, "ghost" to null), profilesOf("ada", "ghost", "ada"))
  }

  @Test
  fun emitsAgainWhenSomeoneRenames() =
      runBlocking<Unit> {
        val subscribed = CompletableDeferred<Unit>()
        val emissions = async {
          users.observeProfiles(listOf("ada")).onEach { subscribed.complete(Unit) }.take(2).toList()
        }
        withTimeout(TIMEOUT_MS) { subscribed.await() }

        users.updateProfile("ada", "Ada", pictureUrl = "gs://bucket/ada").getOrThrow()

        val (before, after) = withTimeout(TIMEOUT_MS) { emissions.await() }
        assertEquals("Ada Lovelace", before.getValue("ada")?.displayName)
        assertEquals("Ada", after.getValue("ada")?.displayName)
        assertEquals("gs://bucket/ada", after.getValue("ada")?.pictureUrl)
      }

  @Test
  fun givesAnEmptyMapAtOnceWithoutUids() = runBlocking {
    assertEquals(emptyMap<String, User?>(), profilesOf())
  }

  // A profile only names someone: one that can't be read mustn't hide the others
  @Test
  fun aProfileThatFailsIsNullAndTheOthersStillCome() = runBlocking {
    val broken =
        object : UserRepository by users {
          override fun observeUser(uid: String): Flow<User?> =
              if (uid == "ada") users.observeUser(uid)
              else flow { throw IllegalStateException("Permission denied") }
        }

    val profiles = profilesOf("ada", "bea", repository = broken)

    assertEquals(ada, profiles["ada"])
    assertNull(profiles["bea"])
  }

  private companion object {
    const val TIMEOUT_MS = 5_000L
  }
}
