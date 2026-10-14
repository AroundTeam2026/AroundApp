// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.testing.SETUP_TIMEOUT_MS
import com.github.aroundteam2026.aroundapp.testing.emulatorClient
import com.github.aroundteam2026.aroundapp.testing.signInNewUser
import com.github.aroundteam2026.aroundapp.testing.withTimeoutBlocking
import com.github.aroundteam2026.aroundapp.testing.writeBypassingRules
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Source
import java.util.Date
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/**
 * Tests [QuestRepositoryFirestore] on the Auth and Firestore emulators, started with `firebase
 * emulators:start --only auth,firestore --project demo-project`, so firestore.rules apply.
 *
 * Clients come from [emulatorClient], so they belong to named [FirebaseApp]s on the `demo-project`
 * id, never to the default app, and the tests cannot reach the real project. Each test signs in a
 * fresh venue account and uses its uid as the venue id, as the rules require, so tests never see
 * each other's quests. Each test's KDoc names the production bug it catches.
 */
@RunWith(AndroidJUnit4::class)
class QuestRepositoryFirestoreTest {
  /**
   * Fails any test, including its setUp and tearDown, that runs longer than this instead of
   * blocking CI forever, and reports where it was stuck.
   */
  @get:Rule val timeout: Timeout = Timeout.seconds(30)

  /** The signed-in venue's client, created by [setUp] and terminated by [tearDown]. */
  private lateinit var db: FirebaseFirestore

  /** The repository under test, on [db], with a clock that always returns [FIXED_NOW]. */
  private lateinit var repository: QuestRepositoryFirestore

  /** Uid of the user signed in by [setUp], used as the venue id of the quests it creates. */
  private lateinit var venueId: String

  /** Signs in a fresh venue user and connects a fresh client to the emulator. */
  @Before
  fun setUp() =
      runBlocking<Unit> {
        withTimeout(SETUP_TIMEOUT_MS) { venueId = signInNewUser(MAIN_APP, Role.VENUE) }
        db = emulatorClient(MAIN_APP)
        repository = QuestRepositoryFirestore(db) { FIXED_NOW }
      }

  /** Terminates the client [setUp] created, so no listener or cached data leaks between tests. */
  @After
  fun tearDown() = runBlocking<Unit> { withTimeout(SETUP_TIMEOUT_MS) { db.terminate().await() } }

  /**
   * Bug: a field is dropped or mis-mapped on write or read (e.g. lat/lng swapped, sign lost, reward
   * not nested, wrong enum, id not taken from the document id), or write never reaches the server
   * (e.g. offline, or a synchronous Firestore exception escapes).
   */
  @Test
  fun createQuest_thenGetQuest_roundTripsEveryField() = withTimeoutBlocking {
    val input = fullQuest()
    val id = repository.createQuest(input).getOrThrow()
    val stored = getQuestFromServer(id)!!
    // Timestamps are checked by createQuest_setsBothTimestampsFromOneClockRead.
    assertEquals(
        input.copy(id = id, createdAt = stored.createdAt, updatedAt = stored.updatedAt),
        stored,
    )
  }

  /**
   * Bug: the document does not follow the schema (Location stored as a {lat, lng} map, a timestamp
   * stored as a Long, enums stored as ordinals) or null fields are stored as "", 0 or null instead
   * of being omitted. A round trip cannot see any of these.
   */
  @Test
  fun createQuest_storesSchemaTypesAndOmitsNullFields() = withTimeoutBlocking {
    val fullId = repository.createQuest(fullQuest()).getOrThrow()
    val freeItemId =
        repository
            .createQuest(fullQuest().copy(reward = Reward.FreeItem("Coffee", specifics = null)))
            .getOrThrow()
    val otherId =
        repository.createQuest(fullQuest().copy(reward = Reward.Other("Sticker"))).getOrThrow()
    val noRewardId = repository.createQuest(fullQuest().copy(reward = null)).getOrThrow()

    // Only the stored representation is checked here; the values are checked by the round trip.
    // The memory cache drops a document once the server has saved it and no listener holds it,
    // so the reads go to the server, after it has saved every write.
    db.waitForPendingWrites().await()
    val full = db.collection(QUESTS).document(fullId).get(Source.SERVER).await()
    assertTrue(full.get("location") is GeoPoint)
    assertTrue(full.get("createdAt") is Timestamp)
    assertTrue(full.get("updatedAt") is Timestamp)
    assertEquals("DRAFT", full.get("status"))
    assertEquals("TEXT", full.get("proofType"))
    assertEquals(true, full.get("exactPartySize"))
    val reward = full.get("reward") as Map<*, *>
    assertEquals(setOf("type", "amount", "unit", "specifics", "expiresAt"), reward.keys)
    assertEquals("DISCOUNT", reward["type"])
    assertEquals("PERCENT", reward["unit"])
    assertTrue(reward["expiresAt"] is Timestamp)
    assertFalse("id must not be stored", full.contains("id"))
    val freeItem =
        db.collection(QUESTS).document(freeItemId).get(Source.SERVER).await().get("reward")
    assertEquals(mapOf("type" to "FREE_ITEM", "name" to "Coffee"), freeItem)
    val other = db.collection(QUESTS).document(otherId).get(Source.SERVER).await().get("reward")
    assertEquals(mapOf("type" to "OTHER", "description" to "Sticker"), other)
    val noReward = db.collection(QUESTS).document(noRewardId).get(Source.SERVER).await()
    assertFalse("a null reward must be omitted", noReward.contains("reward"))
  }

  /**
   * Bug: a reward type other than the one in fullQuest is mis-mapped on write or read (e.g. read
   * back as another subtype, specifics or expiresAt dropped).
   */
  @Test
  fun createQuest_thenGetQuest_roundTripsEveryRewardType() = withTimeoutBlocking {
    val rewards =
        listOf(
            Reward.Discount(5.0, DiscountUnit.CHF, specifics = null),
            Reward.FreeItem("Coffee", "Any size", expiresAt = REWARD_EXPIRES_AT),
            Reward.Other("A hug", expiresAt = REWARD_EXPIRES_AT),
        )
    for (reward in rewards) {
      val id = repository.createQuest(quest().copy(reward = reward)).getOrThrow()
      assertEquals(reward, getQuestFromServer(id)!!.reward)
    }
  }

  /**
   * Bug: the amount is cast with `as Double`, so a whole amount stored as an integer (as the
   * console or the web SDK would) is read back as a Long and the quest is skipped.
   */
  @Test
  fun getQuest_readsAWholeAmountStoredAsAnInteger() = withTimeoutBlocking {
    val id = repository.createQuest(quest()).getOrThrow()
    db.collection(QUESTS)
        .document(id)
        .update("reward", mapOf("type" to "DISCOUNT", "amount" to 5L, "unit" to "CHF"))
        .await()

    assertEquals(
        Reward.Discount(5.0, DiscountUnit.CHF, specifics = null),
        getQuestFromServer(id)!!.reward,
    )
  }

  /**
   * Bug: exactPartySize is read as required, so every quest saved before the field existed is
   * skipped, or a missing value is read as exactly instead of at least.
   */
  @Test
  fun getQuest_readsAMissingExactPartySizeAsAtLeast() = withTimeoutBlocking {
    val id = repository.createQuest(quest().copy(exactPartySize = true)).getOrThrow()
    db.collection(QUESTS).document(id).update("exactPartySize", FieldValue.delete()).await()

    assertEquals(false, getQuestFromServer(id)?.exactPartySize)
  }

  /**
   * Bug: a reward with a missing or unknown type, or a missing field, is read as no reward, so a
   * quest whose reward has already expired never expires and shows up on the map again.
   */
  @Test
  fun getQuest_skipsAQuestWithAnUnreadableReward() = withTimeoutBlocking {
    val expired = Timestamp(Date(FIXED_NOW - 1))
    val unreadableRewards =
        listOf(
            mapOf("type" to "MYSTERY", "description" to "Surprise", "expiresAt" to expired),
            mapOf("description" to "No type", "expiresAt" to expired),
            mapOf("type" to "DISCOUNT", "unit" to "CHF", "expiresAt" to expired),
        )
    for (reward in unreadableRewards) {
      val id = repository.createQuest(quest()).getOrThrow()
      // The rules refuse these rewards, so they go past them, once the quest is on the server
      db.waitForPendingWrites().await()
      writeBypassingRules("$QUESTS/$id", mapOf("reward" to reward))

      assertNull("reward $reward", getQuestFromServer(id))
    }
  }

  /**
   * Bug: the caller's timestamps are kept, the clock is read once per field, or millis are
   * converted to and from Timestamp with lost precision.
   */
  @Test
  fun createQuest_setsBothTimestampsFromOneClockRead() = withTimeoutBlocking {
    var time = FIXED_NOW
    val advancingClockRepository = QuestRepositoryFirestore(db) { time++ }

    val id =
        advancingClockRepository
            .createQuest(fullQuest().copy(createdAt = 1L, updatedAt = 2L))
            .getOrThrow()
    val stored = getQuestFromServer(id)!!

    assertEquals(FIXED_NOW, stored.createdAt)
    assertEquals(FIXED_NOW, stored.updatedAt)
  }

  /** Bug: the caller's id is used as the document id, so a second quest overwrites the first. */
  @Test
  fun createQuest_generatesAFreshIdIgnoringTheCallers() = withTimeoutBlocking {
    val first = repository.createQuest(quest(id = "caller", title = "First")).getOrThrow()
    val second = repository.createQuest(quest(id = "caller", title = "Second")).getOrThrow()

    assertNotEquals("caller", first)
    assertNotEquals(first, second)
    assertEquals("First", getQuestFromServer(first)?.title)
    assertEquals("Second", getQuestFromServer(second)?.title)
  }

  /**
   * Bug: createQuest awaits the server acknowledgement, so it hangs while the device is offline, or
   * the offline write is lost instead of synced later.
   */
  @Test
  fun createQuest_succeedsWhileOffline() = withTimeoutBlocking {
    db.disableNetwork().await()
    val id = repository.createQuest(quest(title = "Offline")).getOrThrow()

    // check it's already in local cache
    assertEquals("Offline", repository.getQuest(id)?.title)

    // check it syncs to the server when back online
    db.enableNetwork().await()
    assertEquals("Offline", getQuestFromServer(id)?.title)
  }

  /** Bug: an exception escapes createQuest instead of becoming a failure. */
  @Test
  fun createQuest_returnsFailureWhenTheWriteCannotBeIssued() = withTimeoutBlocking {
    // an exception thrown while building the write.
    val result1 = repository.createQuest(quest().copy(location = Location(200.0, 0.0)))
    assertTrue(result1.isFailure)
    //  a synchronous Firestore exception occurs.
    val terminated = emulatorClient(TERMINATED_APP)
    terminated.terminate().await()
    val result2 = QuestRepositoryFirestore(terminated).createQuest(quest())
    assertTrue(result2.isFailure)
  }

  /** Bug: getQuest throws for an id with no document instead of returning null as KDoc promises. */
  @Test
  fun getQuest_returnsNullForUnknownId() = withTimeoutBlocking {
    assertNull(repository.getQuest("unknown"))
  }

  /**
   * Bug: observeActiveQuests does not filter on status, or filters on a string that differs from
   * the stored enum name.
   */
  @Test
  fun observeActiveQuests_excludesDraftAndArchived() = withTimeoutBlocking {
    repository.createQuest(quest(status = QuestStatus.DRAFT)).getOrThrow()
    repository.createQuest(quest(status = QuestStatus.ARCHIVED)).getOrThrow()
    val activeId = repository.createQuest(quest()).getOrThrow()

    val active = repository.observeActiveQuests().first { list -> list.any { it.id == activeId } }

    // Other tests' active quests are also listed, so only this venue's quests are compared.
    assertEquals(listOf(activeId), active.ownedBy(venueId).map { it.id })
  }

  /** Bug: the flow reads once (get()) instead of listening, so later quests never arrive. */
  @Test
  fun observeActiveQuests_emitsAQuestCreatedAfterSubscribing() = withTimeoutBlocking {
    val emissions = Channel<List<Quest>>(Channel.UNLIMITED)
    val collector =
        launch(start = CoroutineStart.UNDISPATCHED) {
          repository.observeActiveQuests().collect(emissions::send)
        }
    try {
      // The first emission proves the listener is attached before the write.
      emissions.receive()

      val id = repository.createQuest(quest()).getOrThrow()

      // No assertion needed: a one-shot flow never emits again, so this loop
      // never ends and the test fails on TIMEOUT_MS.
      do {
        val latest = emissions.receive()
      } while (latest.none { it.id == id })
    } finally {
      collector.cancel()
    }
  }

  /** Bug: observeQuestsByVenue does not filter on venueId, or also filters on status. */
  @Test
  fun observeQuestsByVenue_returnsAllOfThatVenuesQuestsWhateverTheirStatus() = withTimeoutBlocking {
    val ownIds =
        QuestStatus.entries.map { repository.createQuest(quest(status = it)).getOrThrow() }.toSet()
    createQuestAsAnotherVenue()

    val quests =
        repository.observeQuestsByVenue(venueId).first { list ->
          list.map { it.id }.containsAll(ownIds)
        }

    assertEquals(ownIds, quests.map { it.id }.toSet())
  }

  /**
   * Bug: a document that cannot be mapped crashes the listener or ends the flow instead of being
   * skipped. getQuest goes through the same mapper.
   */
  @Test
  fun observeActiveQuests_skipsMalformedDocuments() = withTimeoutBlocking {
    // No title, location, ...: the rules refuse it, so it goes past them
    writeBypassingRules(
        "$QUESTS/${db.collection(QUESTS).document().id}",
        mapOf("venueId" to venueId, "status" to "ACTIVE"),
    )
    val validId = repository.createQuest(quest()).getOrThrow()
    // Once the server has the valid quest, the memory cache drops it, so the listener's first list
    // comes from the server, which has the malformed quest too.
    db.waitForPendingWrites().await()

    val active = repository.observeActiveQuests().first { list -> list.any { it.id == validId } }

    assertEquals(listOf(validId), active.ownedBy(venueId).map { it.id })
  }

  /**
   * A quest whose fields all differ from the defaults of [quest] and of [Quest], except [venueId],
   * which the rules require to be the signed-in user's uid.
   */
  private fun fullQuest() =
      Quest(
          id = "",
          venueId = venueId,
          venueName = "Le Café",
          location = Location(46.5191, -6.5668),
          radiusMeters = 120,
          title = "Find the mural",
          description = "A mural hides behind the bar.",
          requirements = "Photograph it.",
          proofType = ProofType.TEXT,
          minPartySize = 3,
          exactPartySize = true,
          reward =
              Reward.Discount(
                  12.5,
                  DiscountUnit.PERCENT,
                  "On drinks",
                  expiresAt = REWARD_EXPIRES_AT,
              ),
          status = QuestStatus.DRAFT,
          createdAt = 1L,
          updatedAt = 2L,
      )

  /** A minimal valid quest, varied only where a test needs it. */
  private fun quest(
      id: String = "",
      status: QuestStatus = QuestStatus.ACTIVE,
      title: String = "Title",
  ) =
      Quest(
          id = id,
          venueId = venueId,
          venueName = "Cafe",
          location = Location(46.52, 6.57),
          radiusMeters = 50,
          title = title,
          description = "Description",
          requirements = "Requirements",
          proofType = ProofType.PHOTO,
          reward = null,
          status = status,
          createdAt = 1_000L,
          updatedAt = 1_000L,
      )

  /** The quests of this list that belong to [venueId]. */
  private fun List<Quest>.ownedBy(venueId: String) = filter { it.venueId == venueId }

  /**
   * Has another signed-in venue create an active quest and waits until the server has it, so a
   * query that ignores the venue would see it.
   */
  private suspend fun createQuestAsAnotherVenue() {
    val otherVenueId = signInNewUser(OTHER_VENUE_APP, Role.VENUE)
    val other = emulatorClient(OTHER_VENUE_APP)
    try {
      QuestRepositoryFirestore(other).createQuest(quest().copy(venueId = otherVenueId)).getOrThrow()
      other.waitForPendingWrites().await()
    } finally {
      other.terminate().await()
    }
  }

  /**
   * Waits until [db] has sent its pending writes, then reads [id] through a fresh client with an
   * empty cache, so the quest must come from the server.
   */
  private suspend fun getQuestFromServer(id: String): Quest? {
    // ensure the write is sent to the emulator
    db.waitForPendingWrites().await()
    // fresh client with empty cache, so every read comes from emulator; reads need a signed-in user
    signInNewUser(READER_APP)
    val reader = emulatorClient(READER_APP)
    try {
      return QuestRepositoryFirestore(reader).getQuest(id)
    } finally {
      reader.terminate().await()
    }
  }

  private companion object {
    /** The Firestore collection the repository stores quests in; some tests use it directly. */
    const val QUESTS = "quests"

    /** The app of the reader that [getQuestFromServer] signs in, with an empty cache. */
    const val READER_APP = "quest-test-reader"

    /** The app of the second venue that [createQuestAsAnotherVenue] signs in. */
    const val OTHER_VENUE_APP = "quest-test-other-venue"

    /** The time the test clock returns, in epoch ms; the non-zero ms catch precision loss. */
    const val FIXED_NOW = 1_700_000_000_123L

    /** When [fullQuest]'s reward expires, in epoch ms; the non-zero ms catch precision loss. */
    const val REWARD_EXPIRES_AT = 1_800_000_000_456L

    /** The app of the venue that [setUp] signs in and every test acts as. */
    const val MAIN_APP = "quest-test"

    /** The app whose client a test terminates on purpose, to make the write fail. */
    const val TERMINATED_APP = "quest-test-terminated"
  }
}
