// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.venue

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.testing.SETUP_TIMEOUT_MS
import com.github.aroundteam2026.aroundapp.testing.TIMEOUT_MS
import com.github.aroundteam2026.aroundapp.testing.emulatorClient
import com.github.aroundteam2026.aroundapp.testing.signInNewUser
import com.github.aroundteam2026.aroundapp.testing.withTimeoutBlocking
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.GeoPoint
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
 * Tests [VenueRepositoryFirestore] on the Auth and Firestore emulators, started with `firebase
 * emulators:start --only auth,firestore --project demo-project`, so firestore.rules apply.
 *
 * Two fresh users take part, each on their own [FirebaseApp] from [emulatorClient], never the
 * default app: the owner and a reader. The rules make a venue's id its owner's uid, so each user
 * has at most one venue and tests never see each other's. Each client has its own empty cache, so
 * what one user reads of the other's venue has been accepted by the server. Each test's KDoc names
 * the production bug it catches.
 */
@RunWith(AndroidJUnit4::class)
class VenueRepositoryFirestoreTest {
  /**
   * Fails any test that runs longer than its setUp, body and tearDown timeouts added together, so
   * one of those reports where the test was stuck before this rule cuts it off.
   */
  @get:Rule val timeout: Timeout = Timeout.millis(RULE_TIMEOUT_MS)

  private lateinit var ownerDb: FirebaseFirestore
  private lateinit var readerDb: FirebaseFirestore
  private lateinit var ownerRepository: VenueRepositoryFirestore
  private lateinit var readerRepository: VenueRepositoryFirestore

  /** Uid of the owner signed in by [setUp], and so the id of the owner's venue. */
  private lateinit var ownerUid: String

  /** Uid of the reader signed in by [setUp], and so the id of the reader's venue. */
  private lateinit var readerUid: String

  @Before
  fun setUp() =
      runBlocking<Unit> {
        withTimeout(SETUP_TIMEOUT_MS) {
          ownerUid = signInNewUser(OWNER_APP)
          readerUid = signInNewUser(READER_APP)
        }
        ownerDb = emulatorClient(OWNER_APP)
        readerDb = emulatorClient(READER_APP)
        ownerRepository = VenueRepositoryFirestore(ownerDb)
        readerRepository = VenueRepositoryFirestore(readerDb)
      }

  /** Terminates both clients, so no listener or cached data leaks between tests. */
  @After
  fun tearDown() =
      runBlocking<Unit> {
        withTimeout(SETUP_TIMEOUT_MS) {
          ownerDb.terminate().await()
          readerDb.terminate().await()
        }
      }

  /**
   * Bug: a field is dropped or mis-mapped on write or read (e.g. lat and lng swapped, createdAt
   * losing its milliseconds, the id not taken from the document id), or a venue without a location,
   * address or featured quest, as onboarding creates it, cannot be read back.
   */
  @Test
  fun createVenue_thenGetVenue_roundTripsFullAndMinimalVenues() = withTimeoutBlocking {
    val full = venue(ownerUid)
    val minimal = venue(readerUid).copy(location = null, address = null, featuredQuestId = null)

    ownerRepository.createVenue(full).getOrThrow()
    readerRepository.createVenue(minimal).getOrThrow()

    assertEquals(full, readerRepository.getVenue(ownerUid))
    assertEquals(minimal, ownerRepository.getVenue(readerUid))
  }

  /**
   * Bug: the document does not follow the schema (the location stored as a map, createdAt as a
   * Long), the id is also stored as a field, or null fields are stored as nulls instead of being
   * omitted. A round trip cannot see any of these.
   */
  @Test
  fun createVenue_storesSchemaTypes() = withTimeoutBlocking {
    ownerRepository.createVenue(venue(ownerUid)).getOrThrow()
    readerRepository
        .createVenue(venue(readerUid).copy(location = null, address = null, featuredQuestId = null))
        .getOrThrow()

    val full = storedVenue(ownerUid)
    val minimal = storedVenue(readerUid)

    assertEquals(
        setOf("name", "location", "radiusMeters", "address", "createdAt", "featuredQuestId"),
        full.data!!.keys,
    )
    assertTrue(full.get("location") is GeoPoint)
    assertTrue(full.get("createdAt") is Timestamp)
    assertEquals(setOf("name", "radiusMeters", "createdAt"), minimal.data!!.keys)
  }

  /** Bug: creating a venue whose id is taken replaces the stored one instead of failing. */
  @Test
  fun createVenue_whenTheIdIsTaken_failsAndKeepsTheStoredVenue() = withTimeoutBlocking {
    val original = venue(ownerUid)
    ownerRepository.createVenue(original).getOrThrow()

    val result = ownerRepository.createVenue(original.copy(name = "Other name"))

    assertTrue(result.exceptionOrNull() is IllegalStateException)
    assertEquals(original, readerRepository.getVenue(ownerUid))
  }

  /**
   * Bug: createVenue checks and writes in separate steps, so of concurrent creations, e.g. a double
   * tap on onboarding's save, several succeed and the last overwrites the others.
   */
  @Test
  fun createVenue_concurrently_exactlyOneSucceeds() = withTimeoutBlocking {
    val venues = (1..CONCURRENT_CALLS).map { venue(ownerUid).copy(name = "Venue $it") }

    val results = venues.map { async { ownerRepository.createVenue(it) } }.awaitAll()

    val winners = results.withIndex().filter { it.value.isSuccess }
    assertEquals(1, winners.size)
    assertTrue(results.all { it.isSuccess || it.exceptionOrNull() is IllegalStateException })
    assertEquals(venues[winners.single().index], readerRepository.getVenue(ownerUid))
  }

  /** Bug: an exception escapes createVenue, here the rules refusing a venue of another account. */
  @Test
  fun createVenue_forAnotherAccount_failsWithPermissionDenied() = withTimeoutBlocking {
    val result = ownerRepository.createVenue(venue(readerUid))

    assertPermissionDenied(result)
  }

  /**
   * Bug: observeVenue reads once instead of listening, so a venue created after subscribing never
   * arrives, or it fails instead of emitting null for a missing venue.
   */
  @Test
  fun observeVenue_emitsNullThenTheVenueOnceCreated() = withTimeoutBlocking {
    val emissions = Channel<Venue?>(Channel.UNLIMITED)
    val collector =
        launch(start = CoroutineStart.UNDISPATCHED) {
          readerRepository.observeVenue(ownerUid).collect(emissions::send)
        }
    try {
      assertNull(emissions.receive())

      ownerRepository.createVenue(venue(ownerUid)).getOrThrow()

      // A one-shot flow never emits again, so this loop never ends and the test times out.
      var latest: Venue?
      do {
        latest = emissions.receive()
      } while (latest == null)
      assertEquals(venue(ownerUid), latest)
    } finally {
      collector.cancel()
    }
  }

  /**
   * Bug: a malformed document makes getVenue throw, or ends the observeVenues flow, instead of
   * being read as missing.
   */
  @Test
  fun malformedDocument_isReadAsMissing() = withTimeoutBlocking {
    // No name or createdAt; the rules only check the owner.
    ownerDb.collection(VENUES).document(ownerUid).set(mapOf("radiusMeters" to 50)).await()
    readerRepository.createVenue(venue(readerUid)).getOrThrow()

    val observed =
        readerRepository.observeVenues(setOf(ownerUid, readerUid)).first { list ->
          list.any { it.id == readerUid }
        }

    assertNull(readerRepository.getVenue(ownerUid))
    assertEquals(listOf(readerUid), observed.map { it.id })
  }

  /**
   * Bug: an id Firestore refuses makes observeVenue or observeVenues throw when called, before the
   * caller's catch on the flow can see it, instead of failing the flow.
   */
  @Test
  fun observe_withAnInvalidId_failsTheFlowInsteadOfThrowing() = withTimeoutBlocking {
    val venue = readerRepository.observeVenue(INVALID_ID)
    val venues = readerRepository.observeVenues(setOf(INVALID_ID))

    assertTrue(runCatching { venue.first() }.exceptionOrNull() is IllegalArgumentException)
    assertTrue(runCatching { venues.first() }.exceptionOrNull() is IllegalArgumentException)
  }

  /** Bug: observeVenues queries Firestore with no ids, which it refuses, instead of emitting. */
  @Test
  fun observeVenues_withNoIds_emitsAnEmptyList() = withTimeoutBlocking {
    assertEquals(emptyList<Venue>(), readerRepository.observeVenues(emptySet()).first())
  }

  /**
   * Bug: observeVenues puts more ids in one query than Firestore takes, does not leave out missing
   * venues, or reads once instead of listening, so a later change never arrives.
   */
  @Test
  fun observeVenues_overMoreIdsThanOneQueryTakes_emitsExistingVenuesAndTheirChanges() =
      withTimeoutBlocking {
        val venue = venue(ownerUid)
        ownerRepository.createVenue(venue).getOrThrow()
        // The real venue comes last, so it is in a later query than the missing ones.
        val ids = (1..MAX_IDS_PER_QUERY).map { "missing-$it" }.toSet() + ownerUid
        val emissions = Channel<List<Venue>>(Channel.UNLIMITED)
        val collector =
            launch(start = CoroutineStart.UNDISPATCHED) {
              readerRepository.observeVenues(ids).collect(emissions::send)
            }
        try {
          var first = emissions.receive()
          // The reader's empty cache may answer before the server does.
          while (first.isEmpty()) first = emissions.receive()
          assertEquals(listOf(venue), first)

          ownerRepository.setArea(ownerUid, NEW_LOCATION, 120, null).getOrThrow()

          // A one-shot flow never emits again, so this loop never ends and the test times out.
          do {
            val latest = emissions.receive()
          } while (latest.single().radiusMeters != 120)
        } finally {
          collector.cancel()
        }
      }

  /**
   * Bug: setArea writes fields other than the area, ignores one of its arguments, or keeps the
   * stored address when given null instead of clearing it.
   */
  @Test
  fun setArea_replacesOnlyTheAreaAndANullAddressClearsIt() = withTimeoutBlocking {
    val original = venue(ownerUid).copy(location = null, address = null)
    ownerRepository.createVenue(original).getOrThrow()
    val placed = original.copy(location = NEW_LOCATION, radiusMeters = 120, address = "Place 2")

    ownerRepository.setArea(ownerUid, NEW_LOCATION, 120, "Place 2").getOrThrow()
    assertEquals(placed, readerRepository.getVenue(ownerUid))

    ownerRepository.setArea(ownerUid, NEW_LOCATION, 120, null).getOrThrow()
    assertEquals(placed.copy(address = null), readerRepository.getVenue(ownerUid))
  }

  /** Bug: setArea on a missing venue creates a partial one, or does not report the failure. */
  @Test
  fun setArea_forAMissingVenue_failsWithoutCreatingIt() = withTimeoutBlocking {
    val result = ownerRepository.setArea(ownerUid, NEW_LOCATION, 120, null)

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    assertFalse(storedVenue(ownerUid).exists())
  }

  /** Bug: setArea moves another account's venue, or an exception escapes instead of a failure. */
  @Test
  fun setArea_onAnotherAccountsVenue_failsWithPermissionDeniedAndKeepsIt() = withTimeoutBlocking {
    val original = venue(readerUid)
    readerRepository.createVenue(original).getOrThrow()

    val result = ownerRepository.setArea(readerUid, NEW_LOCATION, 120, null)

    assertPermissionDenied(result)
    assertEquals(original, ownerRepository.getVenue(readerUid))
  }

  /** The venue document [id] as the server stores it, read by the reader. */
  private suspend fun storedVenue(id: String) =
      readerDb.collection(VENUES).document(id).get(Source.SERVER).await()

  /** Asserts [result] is a failure caused by the security rules refusing the write. */
  private fun assertPermissionDenied(result: Result<*>) {
    val error = result.exceptionOrNull()
    assertTrue("Expected a Firestore exception but got $error", error is FirebaseFirestoreException)
    assertEquals(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        (error as FirebaseFirestoreException).code,
    )
  }

  /** A venue with every field set, owned by [id]. */
  private fun venue(id: String) =
      Venue(
          id = id,
          name = "Café Around",
          location = Location(46.5191, 6.5668),
          radiusMeters = 75,
          address = "Rue de Bourg 1, Lausanne",
          createdAt = CREATED_AT,
          featuredQuestId = "quest-1",
      )

  private companion object {
    /** The Firestore collection the repository stores venues in; some tests use it directly. */
    const val VENUES = "venues"

    const val OWNER_APP = "venue-test-owner"
    const val READER_APP = "venue-test-reader"

    /** Bounds a whole test: setUp, the test body and tearDown, plus a margin. */
    const val RULE_TIMEOUT_MS = SETUP_TIMEOUT_MS + TIMEOUT_MS + SETUP_TIMEOUT_MS + 5_000L

    /** Creation time of every venue, in epoch ms; the non-zero ms catch precision loss. */
    const val CREATED_AT = 1_700_000_000_123L

    /** How many creations the concurrency test races against each other. */
    const val CONCURRENT_CALLS = 10

    /** Where setArea moves the venue to. */
    val NEW_LOCATION = Location(46.5225, 6.6328)

    /** The most ids Firestore takes in one `in` query. */
    const val MAX_IDS_PER_QUERY = 30

    /** An id Firestore refuses: a slash makes it a path instead of a document id. */
    const val INVALID_ID = "a/b"
  }
}
