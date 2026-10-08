// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryFirestoreTest.Companion.TIMEOUT_MS
import com.github.aroundteam2026.aroundapp.testing.FirebaseEmulator
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.MemoryCacheSettings
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
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
 * Clients belong to named [FirebaseApp]s on the `demo-project` id, never to the default app, so the
 * tests cannot reach the real project. Each test signs in a fresh user and uses their uid as the
 * venue id, as the rules require, so tests never see each other's quests. Each comment above a test
 * names the production bug it catches.
 */
@RunWith(AndroidJUnit4::class)
class QuestRepositoryFirestoreTest {
  /**
   * Fails any test, including its setUp and tearDown, that runs longer than this instead of
   * blocking CI forever, and reports where it was stuck.
   */
  @get:Rule val timeout: Timeout = Timeout.seconds(30)

  private lateinit var db: FirebaseFirestore
  private lateinit var repository: QuestRepositoryFirestore

  /** Uid of the user signed in by [setUp], used as the venue id of the quests it creates. */
  private lateinit var venueId: String

  /** Signs in a fresh venue user and connects a fresh client to the emulator. */
  @Before
  fun setUp() =
      runBlocking<Unit> {
        withTimeout(SETUP_TIMEOUT_MS) { venueId = signInNewUser(MAIN_APP) }
        db = emulatorClient(MAIN_APP)
        repository = QuestRepositoryFirestore(db) { FIXED_NOW }
      }

  /** Terminates the client [setUp] created, so no listener or cached data leaks between tests. */
  @After
  fun tearDown() = runBlocking<Unit> { withTimeout(SETUP_TIMEOUT_MS) { db.terminate().await() } }

  // Bug: a field is dropped or mis-mapped on write or read (e.g. lat/lng swapped, sign lost,
  // reward not nested, wrong enum, id not taken from the document id), or write never reaches the
  // server (e.g. offline, or a synchronous Firestore exception escapes).
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

  // Bug: the document does not follow the schema (Location stored as a {lat, lng} map, a timestamp
  // stored as a Long, enums stored as ordinals) or null fields are stored as "", 0 or null instead
  // of being omitted. A round trip cannot see any of these.
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
    val full = db.collection(QUESTS).document(fullId).get().await()
    assertTrue(full.get("location") is GeoPoint)
    assertTrue(full.get("createdAt") is Timestamp)
    assertTrue(full.get("updatedAt") is Timestamp)
    assertEquals("DRAFT", full.get("status"))
    assertEquals("TEXT", full.get("proofType"))
    val reward = full.get("reward") as Map<*, *>
    assertEquals(setOf("type", "amount", "unit", "specifics", "expiresAt"), reward.keys)
    assertEquals("DISCOUNT", reward["type"])
    assertEquals("PERCENT", reward["unit"])
    assertTrue(reward["expiresAt"] is Timestamp)
    assertFalse("id must not be stored", full.contains("id"))
    val freeItem = db.collection(QUESTS).document(freeItemId).get().await().get("reward")
    assertEquals(mapOf("type" to "FREE_ITEM", "name" to "Coffee"), freeItem)
    val other = db.collection(QUESTS).document(otherId).get().await().get("reward")
    assertEquals(mapOf("type" to "OTHER", "description" to "Sticker"), other)
    val noReward = db.collection(QUESTS).document(noRewardId).get().await()
    assertFalse("a null reward must be omitted", noReward.contains("reward"))
  }

  // Bug: a reward type other than the one in fullQuest is mis-mapped on write or read (e.g. read
  // back as another subtype, specifics or expiresAt dropped, a whole CHF amount read as a Long).
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

  // Bug: a reward with a missing or unknown type, or a missing field, throws while mapping, so the
  // whole quest is skipped instead of being read without a reward.
  @Test
  fun getQuest_readsAnUnreadableRewardAsNoReward() = withTimeoutBlocking {
    val unreadableRewards =
        listOf(
            mapOf("type" to "MYSTERY", "description" to "Surprise"),
            mapOf("description" to "No type"),
            mapOf("type" to "DISCOUNT", "unit" to "CHF"),
        )
    for (reward in unreadableRewards) {
      val id = repository.createQuest(quest()).getOrThrow()
      db.collection(QUESTS).document(id).update("reward", reward).await()

      val stored = repository.getQuest(id)
      assertEquals(quest().copy(id = id, createdAt = FIXED_NOW, updatedAt = FIXED_NOW), stored)
    }
  }

  // Bug: the caller's timestamps are kept, the clock is read once per field, or millis are
  // converted to and from Timestamp with lost precision.
  @Test
  fun createQuest_setsBothTimestampsFromOneClockRead() = withTimeoutBlocking {
    var time = FIXED_NOW
    val advancingClockRepository = QuestRepositoryFirestore(db) { time++ }

    val id =
        advancingClockRepository
            .createQuest(fullQuest().copy(createdAt = 1L, updatedAt = 2L))
            .getOrThrow()
    val stored = advancingClockRepository.getQuest(id)!!

    assertEquals(FIXED_NOW, stored.createdAt)
    assertEquals(FIXED_NOW, stored.updatedAt)
  }

  // Bug: the caller's id is used as the document id, so a second quest overwrites the first.
  @Test
  fun createQuest_generatesAFreshIdIgnoringTheCallers() = withTimeoutBlocking {
    val first = repository.createQuest(quest(id = "caller", title = "First")).getOrThrow()
    val second = repository.createQuest(quest(id = "caller", title = "Second")).getOrThrow()

    assertNotEquals("caller", first)
    assertNotEquals(first, second)
    assertEquals("First", repository.getQuest(first)?.title)
    assertEquals("Second", repository.getQuest(second)?.title)
  }

  // Bug: createQuest awaits the server acknowledgement, so it hangs while the device is offline,
  // or the offline write is lost instead of synced later.
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

  // Bug: an exception escapes createQuest instead of becoming a failure.
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

  // Bug: getQuest throws for an id with no document instead of returning null as KDoc promises.
  @Test
  fun getQuest_returnsNullForUnknownId() = withTimeoutBlocking {
    assertNull(repository.getQuest("unknown"))
  }

  // Bug: observeActiveQuests does not filter on status, or filters on a string that differs from
  // the stored enum name.
  @Test
  fun observeActiveQuests_excludesDraftAndArchived() = withTimeoutBlocking {
    repository.createQuest(quest(status = QuestStatus.DRAFT)).getOrThrow()
    repository.createQuest(quest(status = QuestStatus.ARCHIVED)).getOrThrow()
    val activeId = repository.createQuest(quest(status = QuestStatus.ACTIVE)).getOrThrow()

    val active = repository.observeActiveQuests().first { list -> list.any { it.id == activeId } }

    // Other tests' active quests are also listed, so only this venue's quests are compared.
    assertEquals(listOf(activeId), active.ownedBy(venueId).map { it.id })
  }

  // Bug: the flow reads once (get()) instead of listening, so later quests never arrive.
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

  // Bug: observeQuestsByVenue does not filter on venueId, or also filters on status.
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

  // Bug: a document that cannot be mapped crashes the listener or ends the flow instead of being
  // skipped. getQuest goes through the same mapper.
  @Test
  fun observeActiveQuests_skipsMalformedDocuments() = withTimeoutBlocking {
    db.collection(QUESTS)
        .document()
        .set(mapOf("venueId" to venueId, "status" to "ACTIVE")) // no title, location, ...
        .await()
    val validId = repository.createQuest(quest()).getOrThrow()

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

  /** Runs [block] blocking, failing after [TIMEOUT_MS] instead of hanging on a missing emission. */
  private fun withTimeoutBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking {
    withTimeout(TIMEOUT_MS, block)
  }

  /** The quests of this list that belong to [venueId]. */
  private fun List<Quest>.ownedBy(venueId: String) = filter { it.venueId == venueId }

  /**
   * Has another signed-in venue create an active quest and waits until the server has it, so a
   * query that ignores the venue would see it.
   */
  private suspend fun createQuestAsAnotherVenue() {
    val otherVenueId = signInNewUser(OTHER_VENUE_APP)
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
    const val QUESTS = "quests"
    const val READER_APP = "quest-test-reader"
    const val OTHER_VENUE_APP = "quest-test-other-venue"
    const val PASSWORD = "password123"
    const val TIMEOUT_MS = 10_000L

    /** Bounds setUp and tearDown; the first connection to the emulator can be slow on CI. */
    const val SETUP_TIMEOUT_MS = 15_000L

    /** The time the test clock returns, in epoch ms; the non-zero ms catch precision loss. */
    const val FIXED_NOW = 1_700_000_000_123L
    const val REWARD_EXPIRES_AT = 1_800_000_000_456L

    const val MAIN_APP = "quest-test"
    const val TERMINATED_APP = "quest-test-terminated"

    /**
     * Returns a client of the [FirebaseApp] named [appName] on the `demo-project` id, pointed at
     * the emulator and caching in memory only. The app is created on first use.
     *
     * [FirebaseEmulator.connect] only configures the default app, while these tests need separate
     * named apps (one is terminated on purpose, another acts as a second device), so this reuses
     * its host and port instead.
     */
    fun emulatorClient(appName: String): FirebaseFirestore =
        FirebaseFirestore.getInstance(emulatorApp(appName)).apply {
          useEmulator(FirebaseEmulator.HOST, FirebaseEmulator.FIRESTORE_PORT)
          firestoreSettings =
              FirebaseFirestoreSettings.Builder()
                  .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                  .build() // keep local cache in ram only
        }

    /**
     * Creates a fresh user on the Auth emulator and signs them in on the app named [appName], so
     * that app's Firestore client acts as them.
     *
     * @return the new user's uid.
     */
    suspend fun signInNewUser(appName: String): String {
      val email = "quest-test-${UUID.randomUUID()}@around.test"
      val auth = FirebaseAuth.getInstance(emulatorApp(appName))
      return auth.createUserWithEmailAndPassword(email, PASSWORD).await().user!!.uid
    }

    /**
     * Returns the [FirebaseApp] named [appName] on the `demo-project` id. On first use it creates
     * the app and points its Auth at the emulator; Firestore is pointed per client, since a
     * terminated client is replaced by a new one.
     */
    private fun emulatorApp(appName: String): FirebaseApp {
      val context = InstrumentationRegistry.getInstrumentation().targetContext // get app context
      return FirebaseApp.getApps(context).firstOrNull { it.name == appName }
          ?: FirebaseApp.initializeApp(
                  context,
                  FirebaseOptions.Builder()
                      .setProjectId("demo-project")
                      .setApplicationId("1:1:android:1")
                      .setApiKey("fake-api-key")
                      .build(),
                  appName,
              )
              .also {
                FirebaseAuth.getInstance(it)
                    .useEmulator(FirebaseEmulator.HOST, FirebaseEmulator.AUTH_PORT)
              }
    }
  }
}
