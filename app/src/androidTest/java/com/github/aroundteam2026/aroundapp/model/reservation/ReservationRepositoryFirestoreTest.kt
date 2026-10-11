// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.aroundteam2026.aroundapp.testing.FirebaseEmulator
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/**
 * Tests [ReservationRepositoryFirestore] on the Auth and Firestore emulators, started with
 * `firebase emulators:start --only auth,firestore --project demo-project`, so firestore.rules
 * apply.
 *
 * Two clients take part, each on its own named [FirebaseApp] on the `demo-project` id, never the
 * default app, so the tests cannot reach the real project: the explorer creates reservations and
 * the venue answers them. Each client has its own empty cache, so what the venue reads has been
 * accepted by the server. Each test signs in fresh users and uses the venue's uid as the venue id,
 * as the rules require, so tests never see each other's reservations. Each comment above a test
 * names the production bug it catches.
 */
@RunWith(AndroidJUnit4::class)
class ReservationRepositoryFirestoreTest {
  /**
   * Fails any test, including its setUp and tearDown, that runs longer than this instead of
   * blocking CI forever. It is longer than the setUp, test body and tearDown timeouts added
   * together, so one of those reports where the test was stuck before this rule cuts it off.
   */
  @get:Rule val timeout: Timeout = Timeout.millis(RULE_TIMEOUT_MS)

  private lateinit var explorerDb: FirebaseFirestore
  private lateinit var venueDb: FirebaseFirestore

  /** Repository used by the signed-in explorer. */
  private lateinit var explorerRepository: ReservationRepositoryFirestore

  /** Repository used by the signed-in venue, which owns the reservations the tests create. */
  private lateinit var venueRepository: ReservationRepositoryFirestore

  private lateinit var explorerUid: String
  private lateinit var venueId: String

  /** Signs in a fresh explorer and a fresh venue, each on their own client of the emulator. */
  @Before
  fun setUp() =
      runBlocking<Unit> {
        withTimeout(SETUP_TIMEOUT_MS) {
          explorerUid = signInNewUser(EXPLORER_APP)
          venueId = signInNewUser(VENUE_APP)
        }
        explorerDb = emulatorClient(EXPLORER_APP)
        venueDb = emulatorClient(VENUE_APP)
        explorerRepository = ReservationRepositoryFirestore(explorerDb) { FIXED_NOW }
        venueRepository = ReservationRepositoryFirestore(venueDb) { FIXED_NOW }
      }

  /** Terminates both clients, so no listener or cached data leaks between tests. */
  @After
  fun tearDown() =
      runBlocking<Unit> {
        withTimeout(SETUP_TIMEOUT_MS) {
          explorerDb.terminate().await()
          venueDb.terminate().await()
        }
      }

  // Bug: a field is dropped or mis-mapped on write or read (e.g. slotStart and createdAt swapped,
  // a party member lost, the status mapped to the wrong enum, the id not taken from the document
  // id).
  @Test
  fun createReservation_thenObserveForVenue_roundTripsEveryField() = withTimeoutBlocking {
    val id = createAndSync()

    val stored = fetchAsVenue(id)

    assertEquals(
        Reservation(
            id = id,
            questId = QUEST_ID,
            venueId = venueId,
            explorerUids = listOf(explorerUid, FRIEND_UID),
            slotStart = SLOT_START,
            status = ReservationStatus.PENDING,
            createdAt = FIXED_NOW,
        ),
        stored,
    )
  }

  // Bug: the document does not follow the schema (a time stored as a Long, the status stored as
  // an ordinal, the party stored as a map) or the id is also stored as a field. A round trip
  // cannot see any of these.
  @Test
  fun createReservation_storesSchemaTypes() = withTimeoutBlocking {
    val id = createAndSync()

    val document = venueDb.collection(RESERVATIONS).document(id).get(Source.SERVER).await()

    assertEquals(
        setOf("questId", "venueId", "explorerUids", "slotStart", "status", "createdAt"),
        document.data!!.keys,
    )
    assertTrue(document.get("slotStart") is Timestamp)
    assertTrue(document.get("createdAt") is Timestamp)
    assertEquals("PENDING", document.get("status"))
    assertEquals(listOf(explorerUid, FRIEND_UID), document.get("explorerUids"))
  }

  // Bug: the caller's id, status or creation time are kept, so a second reservation overwrites the
  // first, a caller can create an already approved one, or the clock is not used.
  @Test
  fun createReservation_ignoresTheCallersIdStatusAndCreatedAt() = withTimeoutBlocking {
    val input =
        reservation().copy(id = "caller", status = ReservationStatus.APPROVED, createdAt = 1L)

    val first = createAndSync(input)
    val second = createAndSync(input)

    assertNotEquals("caller", first)
    assertNotEquals(first, second)
    val stored = fetchAsVenue(first)
    assertEquals(ReservationStatus.PENDING, stored.status)
    assertEquals(FIXED_NOW, stored.createdAt)
    assertEquals(second, fetchAsVenue(second).id)
  }

  // Bug: a reservation the rules refuse is stored anyway, or createReservation reports the refusal
  // as a failure although, like createQuest, it must not wait for the server. The rules only let an
  // explorer reserve for a party they belong to.
  @Test
  fun createReservation_whenTheSignedInUserIsNotInTheParty_succeedsButTheServerRejectsTheWrite() =
      withTimeoutBlocking {
        val id = createAndSync(reservation(party = listOf(FRIEND_UID)))

        val stored = venueRepository.observeForVenue(venueId).first()

        assertTrue(stored.none { it.id == id })
      }

  // Bug: createReservation waits for the server, so it hangs or fails while the device is offline.
  @Test
  fun createReservation_whileOffline_succeedsAndIsSentOnceBackOnline() = withTimeoutBlocking {
    explorerDb.disableNetwork().await()
    val id: String
    try {
      id = explorerRepository.createReservation(reservation()).getOrThrow()
    } finally {
      explorerDb.enableNetwork().await()
    }
    explorerDb.waitForPendingWrites().await()

    assertEquals(id, fetchAsVenue(id).id)
  }

  // Bug: an exception escapes createReservation instead of becoming a failure.
  @Test
  fun createReservation_returnsFailureWhenTheClientIsTerminated() = withTimeoutBlocking {
    val terminated = emulatorClient(TERMINATED_APP)
    terminated.terminate().await()

    val result = ReservationRepositoryFirestore(terminated).createReservation(reservation())

    assertTrue(result.isFailure)
  }

  // Bug: observeForVenue does not filter on the venue, or also filters on the status.
  @Test
  fun observeForVenue_returnsEveryReservationOfThatVenueWhateverTheirStatus() =
      withTimeoutBlocking {
        val pending = createAndSync()
        val approved = createAndSync()
        venueRepository.updateStatus(approved, ReservationStatus.APPROVED).getOrThrow()
        val ownIds = setOf(pending, approved)

        val reservations =
            venueRepository.observeForVenue(venueId).first { list ->
              list.map { it.id }.containsAll(ownIds)
            }

        assertEquals(ownIds, reservations.map { it.id }.toSet())
        assertEquals(
            ReservationStatus.APPROVED,
            reservations.first { it.id == approved }.status,
        )
      }

  // Bug: the flow reads once (get()) instead of listening, so later reservations never arrive.
  @Test
  fun observeForVenue_emitsAReservationCreatedAfterSubscribing() = withTimeoutBlocking {
    val emissions = Channel<List<Reservation>>(Channel.UNLIMITED)
    val collector =
        launch(start = CoroutineStart.UNDISPATCHED) {
          venueRepository.observeForVenue(venueId).collect(emissions::send)
        }
    try {
      // The first emission proves the listener is attached before the write.
      emissions.receive()

      val id = createAndSync()

      // No assertion needed: a one-shot flow never emits again, so this loop never ends and the
      // test fails on TIMEOUT_MS.
      do {
        val latest = emissions.receive()
      } while (latest.none { it.id == id })
    } finally {
      collector.cancel()
    }
  }

  // Bug: observeForExplorer matches the party with equality instead of array-contains, so a
  // reservation whose party has two members is missed.
  @Test
  fun observeForExplorer_returnsEveryReservationWhosePartyIncludesTheExplorer() =
      withTimeoutBlocking {
        val solo = createAndSync(reservation(party = listOf(explorerUid)))
        val withFriend = createAndSync(reservation(party = listOf(FRIEND_UID, explorerUid)))
        val expected = setOf(solo, withFriend)

        val reservations =
            explorerRepository.observeForExplorer(explorerUid).first { list ->
              list.map { it.id }.containsAll(expected)
            }

        assertEquals(expected, reservations.map { it.id }.toSet())
      }

  // Bug: observeForVenue or observeForExplorer lets a malformed document crash the listener or
  // end the flow instead of skipping it.
  @Test
  fun observe_skipsMalformedDocuments() = withTimeoutBlocking {
    // No questId, slotStart or createdAt; the rules only check the party and the status.
    explorerDb
        .collection(RESERVATIONS)
        .document()
        .set(
            mapOf(
                "venueId" to venueId,
                "explorerUids" to listOf(explorerUid),
                "status" to "PENDING",
            )
        )
        .await()
    val validId = createAndSync()

    val forVenue =
        venueRepository.observeForVenue(venueId).first { list -> list.any { it.id == validId } }
    val forExplorer =
        explorerRepository.observeForExplorer(explorerUid).first { list ->
          list.any { it.id == validId }
        }

    assertEquals(listOf(validId), forVenue.map { it.id })
    assertEquals(listOf(validId), forExplorer.map { it.id })
  }

  // Bug: updateStatus writes more than the status (the rules refuse that), or writes a different
  // status than asked.
  @Test
  fun updateStatus_byTheVenue_changesOnlyTheStatus() = withTimeoutBlocking {
    val id = createAndSync()
    val before = fetchAsVenue(id)

    venueRepository.updateStatus(id, ReservationStatus.APPROVED).getOrThrow()

    val after = fetchAsVenue(id) { it.status == ReservationStatus.APPROVED }
    assertEquals(before.copy(status = ReservationStatus.APPROVED), after)
  }

  // Bug: updateStatus does not check canTransition, so a final status can be reopened, or it
  // reports the refusal with the wrong exception.
  @Test
  fun updateStatus_refusesAForbiddenTransitionAndLeavesTheStatusUnchanged() = withTimeoutBlocking {
    val id = createAndSync()
    venueRepository.updateStatus(id, ReservationStatus.CANCELLED).getOrThrow()

    val result = venueRepository.updateStatus(id, ReservationStatus.APPROVED)

    assertTrue(result.exceptionOrNull() is IllegalStateException)
    assertEquals("CANCELLED", storedStatus(id))
  }

  // Bug: updateStatus throws or hangs for an id with no document instead of returning a failure.
  // The read rule cannot evaluate a missing document, so the server answers PERMISSION_DENIED
  // rather than the transaction seeing a missing reservation.
  @Test
  fun updateStatus_forAnUnknownId_failsWithPermissionDenied() = withTimeoutBlocking {
    val result = venueRepository.updateStatus("unknown", ReservationStatus.APPROVED)

    assertPermissionDenied(result)
  }

  // Bug: updateStatus changes a document that cannot be mapped to a Reservation, or reports it
  // with another exception than NoSuchElementException.
  @Test
  fun updateStatus_forAMalformedDocument_failsWithNoSuchElement() = withTimeoutBlocking {
    val document = explorerDb.collection(RESERVATIONS).document()
    // No questId, slotStart or createdAt; the rules only check the party and the status, and the
    // venue may read it because the venueId matches.
    document
        .set(
            mapOf(
                "venueId" to venueId,
                "explorerUids" to listOf(explorerUid),
                "status" to "PENDING",
            )
        )
        .await()

    val result = venueRepository.updateStatus(document.id, ReservationStatus.APPROVED)

    assertTrue(result.exceptionOrNull() is NoSuchElementException)
    assertEquals("PENDING", storedStatus(document.id))
  }

  // Bug: the KDoc and the rules disagree: an explorer cancelling their own reservation is refused
  // by the rules, so it must come back as a failure and leave the reservation untouched. This test
  // is the one to change when E21 lets explorers cancel.
  @Test
  fun updateStatus_byTheExplorer_isRefusedByTheRules() = withTimeoutBlocking {
    val id = createAndSync()

    val result = explorerRepository.updateStatus(id, ReservationStatus.CANCELLED)

    assertPermissionDenied(result)
    assertEquals("PENDING", storedStatus(id))
  }

  /**
   * A reservation by the explorer for the venue's quest. [party] must contain the explorer for the
   * rules to accept it. `id`, `status` and `createdAt` are set by the repository and ignored.
   */
  private fun reservation(party: List<String> = listOf(explorerUid, FRIEND_UID)) =
      Reservation(
          id = "",
          questId = QUEST_ID,
          venueId = venueId,
          explorerUids = party,
          slotStart = SLOT_START,
          status = ReservationStatus.PENDING,
          createdAt = 1L,
      )

  /**
   * Creates [reservation] as the explorer and waits until the server has answered the write, so
   * that the venue can read it. [ReservationRepositoryFirestore.createReservation] itself does not
   * wait for the server. This also returns once the server has rejected the write.
   */
  private suspend fun createAndSync(reservation: Reservation = reservation()): String {
    val id = explorerRepository.createReservation(reservation).getOrThrow()
    explorerDb.waitForPendingWrites().await()
    return id
  }

  /** Runs [block] blocking, failing after [TIMEOUT_MS] instead of hanging on a missing emission. */
  private fun withTimeoutBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking {
    withTimeout(TIMEOUT_MS, block)
  }

  /**
   * Reads the reservation with [id] through the venue's client, once [until] accepts it. The venue
   * client has no local copy of the explorer's writes, so the reservation comes from the server.
   * The listener may first deliver an older snapshot, hence [until] for checks after an update.
   */
  private suspend fun fetchAsVenue(
      id: String,
      until: (Reservation) -> Boolean = { true },
  ): Reservation =
      venueRepository
          .observeForVenue(venueId)
          .first { list -> list.any { it.id == id && until(it) } }
          .first { it.id == id }

  /** The status stored on the server for [id], read as the venue so the rules allow it. */
  private suspend fun storedStatus(id: String): String? =
      venueDb.collection(RESERVATIONS).document(id).get(Source.SERVER).await().getString("status")

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
    const val RESERVATIONS = "reservations"
    const val EXPLORER_APP = "reservation-test-explorer"
    const val VENUE_APP = "reservation-test-venue"
    const val TERMINATED_APP = "reservation-test-terminated"
    const val PASSWORD = "password123"
    const val QUEST_ID = "quest-1"

    /** A party member who never signs in; only the explorer's own uid matters to the rules. */
    const val FRIEND_UID = "friend-uid"

    const val TIMEOUT_MS = 10_000L

    /** Bounds setUp and tearDown; the first connection to the emulator can be slow on CI. */
    const val SETUP_TIMEOUT_MS = 15_000L

    /** Bounds a whole test: setUp, the test body and tearDown, plus a margin. */
    const val RULE_TIMEOUT_MS = SETUP_TIMEOUT_MS + TIMEOUT_MS + SETUP_TIMEOUT_MS + 5_000L

    /** The time the test clock returns, in epoch ms; the non-zero ms catch precision loss. */
    const val FIXED_NOW = 1_700_000_000_123L
    const val SLOT_START = 1_800_000_000_456L

    /**
     * Returns a client of the [FirebaseApp] named [appName] on the `demo-project` id, pointed at
     * the emulator and caching in memory only. The app is created on first use.
     *
     * [FirebaseEmulator.connect] only configures the default app, while these tests need separate
     * named apps (an explorer, a venue and one terminated on purpose), so this reuses its host and
     * port instead.
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
      val email = "reservation-test-${UUID.randomUUID()}@around.test"
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
