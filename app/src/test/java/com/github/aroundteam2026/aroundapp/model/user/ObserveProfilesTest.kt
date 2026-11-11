// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.user

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests [observeProfiles] on a [FakeUserRepository]. */
class ObserveProfilesTest {
  private val ada = User("ada", "Ada Lovelace", Role.EXPLORER, createdAt = 0L)
  private val users = FakeUserRepository().apply { runBlocking { createUser(ada) } }

  @Test
  fun givesEachProfileByUidAndNullForAMissingOne() = runBlocking {
    val profiles = users.observeProfiles(listOf("ada", "ghost", "ada")).first()

    assertEquals(mapOf("ada" to ada, "ghost" to null), profiles)
  }

  @Test
  fun followsARename() = runBlocking {
    users.updateProfile("ada", "Ada", pictureUrl = "gs://bucket/ada").getOrThrow()

    val profile = users.observeProfiles(listOf("ada")).first().getValue("ada")

    assertEquals("Ada", profile?.displayName)
    assertEquals("gs://bucket/ada", profile?.pictureUrl)
  }

  @Test
  fun givesAnEmptyMapAtOnceWithoutUids() = runBlocking {
    assertEquals(
        emptyMap<String, User?>(),
        withTimeout(TIMEOUT_MS) { users.observeProfiles(emptyList()).first() },
    )
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

    val profiles = broken.observeProfiles(listOf("ada", "bea")).first()

    assertEquals(ada, profiles["ada"])
    assertNull(profiles["bea"])
  }

  private companion object {
    const val TIMEOUT_MS = 1_000L
  }
}
