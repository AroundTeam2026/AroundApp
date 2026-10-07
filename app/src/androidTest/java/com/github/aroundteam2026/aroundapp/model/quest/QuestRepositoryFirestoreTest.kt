// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryFirestoreTest.Companion.TIMEOUT_MS
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
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
 * Tests [QuestRepositoryFirestore] on the Firestore emulator, started with `firebase
 * emulators:start --only firestore --project demo-project`.
 *
 * Clients belong to named [FirebaseApp]s on the `demo-project` id, never to the default app, so the
 * tests cannot reach the real project. Each comment above a test names the production bug it
 * catches.
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

  /** Connects a fresh client to the emulator and deletes every quest left by earlier tests. */
  @Before
  fun setUp() =
      runBlocking<Unit> {
        db = emulatorClient(MAIN_APP)
        repository = QuestRepositoryFirestore(db) { FIXED_NOW }
        withTimeout(SETUP_TIMEOUT_MS) {
          // A batch is capped at 500 writes, so delete page by page until nothing is left.
          while (true) {
            val page = db.collection(QUESTS).limit(BATCH_LIMIT).get(Source.SERVER).await()
            if (page.isEmpty) break
            val batch = db.batch()
            page.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
          }
        }
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
    val bareRewardId =
        repository
            .createQuest(
                fullQuest().copy(reward = Reward("Sticker", terms = null, expiresAt = null))
            )
            .getOrThrow()
    val noRewardId = repository.createQuest(fullQuest().copy(reward = null)).getOrThrow()

    // Only the stored representation is checked here; the values are checked by the round trip.
    val full = db.collection(QUESTS).document(fullId).get().await()
    assertTrue(full.get("location") is GeoPoint)
    assertTrue(full.get("createdAt") is Timestamp)
    assertTrue(full.get("updatedAt") is Timestamp)
    assertEquals("DRAFT", full.get("status"))
    assertEquals("TEXT", full.get("proofType"))
    val reward = full.get("reward") as Map<*, *>
    assertEquals(setOf("description", "terms", "expiresAt"), reward.keys)
    assertTrue(reward["expiresAt"] is Timestamp)
    assertFalse("id must not be stored", full.contains("id"))
    val bareReward = db.collection(QUESTS).document(bareRewardId).get().await()
    assertEquals(setOf("description"), (bareReward.get("reward") as Map<*, *>).keys)
    val noReward = db.collection(QUESTS).document(noRewardId).get().await()
    assertFalse("a null reward must be omitted", noReward.contains("reward"))
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

    assertEquals(listOf(activeId), active.map { it.id })
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
    val v1Ids =
        QuestStatus.entries
            .map { repository.createQuest(quest(venueId = "v1", status = it)).getOrThrow() }
            .toSet()
    repository.createQuest(quest(venueId = "v2")).getOrThrow()

    val quests =
        repository.observeQuestsByVenue("v1").first { list ->
          list.map { it.id }.containsAll(v1Ids)
        }

    assertEquals(v1Ids, quests.map { it.id }.toSet())
  }

  // Bug: a document that cannot be mapped crashes the listener or ends the flow instead of being
  // skipped. getQuest goes through the same mapper.
  @Test
  fun observeActiveQuests_skipsMalformedDocuments() = withTimeoutBlocking {
    db.collection(QUESTS)
        .document("malformed")
        .set(mapOf("venueId" to "v1", "status" to "ACTIVE")) // no title, location, ...
        .await()
    val validId = repository.createQuest(quest()).getOrThrow()

    val active = repository.observeActiveQuests().first { list -> list.any { it.id == validId } }

    assertEquals(listOf(validId), active.map { it.id })
  }

  /** A quest whose fields all differ from the defaults of [quest] and of [Quest]. */
  private fun fullQuest() =
      Quest(
          id = "",
          venueId = "venue-42",
          venueName = "Le Café",
          location = Location(46.5191, -6.5668),
          radiusMeters = 120,
          title = "Find the mural",
          description = "A mural hides behind the bar.",
          requirements = "Photograph it.",
          proofType = ProofType.TEXT,
          minPartySize = 3,
          reward = Reward("Free coffee", terms = "One per visit", expiresAt = REWARD_EXPIRES_AT),
          status = QuestStatus.DRAFT,
          createdAt = 1L,
          updatedAt = 2L,
      )

  /** A minimal valid quest, varied only where a test needs it. */
  private fun quest(
      id: String = "",
      venueId: String = "v1",
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

  /**
   * Waits until [db] has sent its pending writes, then reads [id] through a fresh client with an
   * empty cache, so the quest must come from the server.
   */
  private suspend fun getQuestFromServer(id: String): Quest? {
    // ensure the write is sent to the emulator
    db.waitForPendingWrites().await()
    // fresh client with empty cache, so every read comes from emulator
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
    const val BATCH_LIMIT = 500L
    const val TIMEOUT_MS = 10_000L

    /** Bounds setUp and tearDown; the first connection to the emulator can be slow on CI. */
    const val SETUP_TIMEOUT_MS = 15_000L

    /** The time the test clock returns, in epoch ms; the non-zero ms catch precision loss. */
    const val FIXED_NOW = 1_700_000_000_123L
    const val REWARD_EXPIRES_AT = 1_800_000_000_456L

    const val MAIN_APP = "quest-test"
    const val TERMINATED_APP = "quest-test-terminated"
    const val EMULATOR_HOST = "10.0.2.2"
    const val EMULATOR_PORT = 8080

    /**
     * Returns a client of the [FirebaseApp] named [appName] on the `demo-project` id, pointed at
     * the emulator and caching in memory only. The app is created on first use.
     */
    fun emulatorClient(appName: String): FirebaseFirestore {
      val context = InstrumentationRegistry.getInstrumentation().targetContext // get app context
      val app =
          FirebaseApp.getApps(context).firstOrNull { it.name == appName }
              ?: FirebaseApp.initializeApp(
                  context,
                  FirebaseOptions.Builder()
                      .setProjectId("demo-project")
                      .setApplicationId("1:1:android:1")
                      .setApiKey("fake-api-key")
                      .build(),
                  appName,
              )
      return FirebaseFirestore.getInstance(app).apply {
        useEmulator(EMULATOR_HOST, EMULATOR_PORT)
        firestoreSettings =
            FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build() // keep local cache in ram only
      }
    }
  }
}
