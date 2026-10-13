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
import kotlinx.coroutines.channels.Channel
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
   * Long) or the id is also stored as a field. A round trip cannot see any of these.
   */
  @Test
  fun createVenue_storesSchemaTypes() = withTimeoutBlocking {
    ownerRepository.createVenue(venue(ownerUid)).getOrThrow()

    val document = readerDb.collection(VENUES).document(ownerUid).get(Source.SERVER).await()

    assertEquals(
        setOf("name", "location", "radiusMeters", "address", "createdAt", "featuredQuestId"),
        document.data!!.keys,
    )
    assertTrue(document.get("location") is GeoPoint)
    assertTrue(document.get("createdAt") is Timestamp)
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

  /** Bug: an exception escapes createVenue, here the rules refusing a venue of another account. */
  @Test
  fun createVenue_forAnotherAccount_failsWithPermissionDenied() = withTimeoutBlocking {
    val result = ownerRepository.createVenue(venue(readerUid))

    val error = result.exceptionOrNull()
    assertTrue("Expected a Firestore exception but got $error", error is FirebaseFirestoreException)
    assertEquals(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        (error as FirebaseFirestoreException).code,
    )
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
      do {
        val latest = emissions.receive()
      } while (latest == null)
    } finally {
      collector.cancel()
    }
  }

  /** Bug: a malformed document makes getVenue throw instead of reading it as missing. */
  @Test
  fun getVenue_readsAMalformedDocumentAsMissing() = withTimeoutBlocking {
    // No name or createdAt; the rules only check the owner.
    ownerDb.collection(VENUES).document(ownerUid).set(mapOf("radiusMeters" to 50)).await()

    assertNull(readerRepository.getVenue(ownerUid))
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
    assertFalse(readerDb.collection(VENUES).document(ownerUid).get(Source.SERVER).await().exists())
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

    /** Where setArea moves the venue to. */
    val NEW_LOCATION = Location(46.5225, 6.6328)
  }
}
