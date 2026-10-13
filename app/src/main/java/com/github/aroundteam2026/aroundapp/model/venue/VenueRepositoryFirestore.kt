// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.venue

import android.util.Log
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Transaction
import java.util.Date
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * [VenueRepository] backed by the Firestore `venues` collection.
 *
 * Firestore types stay inside this class. [Location] is stored as a `GeoPoint` and
 * [Venue.createdAt] as a `Timestamp`. Null fields are omitted, and the venue id, its owner's uid,
 * is the document id rather than a stored field. Documents that cannot be mapped back to a [Venue]
 * are logged and read as missing.
 *
 * The security rules let any signed-in user read a venue and only its owner write it, so writing a
 * venue whose id is not the signed-in uid fails with the Firestore exception.
 *
 * @param db Firestore client to read from and write to.
 */
class VenueRepositoryFirestore(private val db: FirebaseFirestore) : VenueRepository {
  private val venues
    get() = db.collection(VENUES)

  override fun observeVenue(venueId: String): Flow<Venue?> = venues.document(venueId).observe()

  override suspend fun getVenue(venueId: String): Venue? =
      venues.document(venueId).get().await().toVenue()

  /**
   * Reads and writes in one transaction, so of two creations with the same id only one succeeds.
   * Needs a connection: a transaction fails while the device is offline.
   */
  override suspend fun createVenue(venue: Venue): Result<Unit> = transaction {
    val document = venues.document(venue.id)
    if (it.get(document).exists()) {
      IllegalStateException("Venue ${venue.id} already exists")
    } else {
      it.set(document, venue.toFirestore())
      null
    }
  }

  /**
   * Writes only the area fields, deleting `address` when [address] is null. Needs a connection: a
   * transaction fails while the device is offline.
   */
  override suspend fun setArea(
      venueId: String,
      location: Location,
      radiusMeters: Int,
      address: String?,
  ): Result<Unit> = transaction {
    val document = venues.document(venueId)
    if (!it.get(document).exists()) {
      IllegalArgumentException("No venue with id $venueId")
    } else {
      it.update(
          document,
          mapOf(
              LOCATION to GeoPoint(location.lat, location.lng),
              RADIUS_METERS to radiusMeters,
              ADDRESS to (address ?: FieldValue.delete()),
          ),
      )
      null
    }
  }

  /**
   * Runs [block] in a transaction. [block] returns the reason for refusing instead of throwing it,
   * so the caller gets that exact exception rather than one wrapped by Firestore. Other exceptions,
   * e.g. the rules refusing the write, are returned as a failure too.
   */
  private suspend fun transaction(block: (Transaction) -> Exception?): Result<Unit> =
      try {
        val refusal = db.runTransaction<Exception?> { block(it) }.await()
        if (refusal == null) Result.success(Unit) else Result.failure(refusal)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }

  /** Turns Firestore's callback-style listener into a Flow of this document's venue. */
  private fun DocumentReference.observe(): Flow<Venue?> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
      if (error != null) {
        close(error)
      } else if (snapshot != null) {
        trySend(snapshot.toVenue())
      }
    }
    awaitClose { registration.remove() }
  }

  /** The document fields for this venue, without its id and its null fields. */
  private fun Venue.toFirestore(): Map<String, Any> = buildMap {
    put(NAME, name)
    location?.let { put(LOCATION, GeoPoint(it.lat, it.lng)) }
    put(RADIUS_METERS, radiusMeters)
    address?.let { put(ADDRESS, it) }
    put(CREATED_AT, Timestamp(Date(createdAt)))
    featuredQuestId?.let { put(FEATURED_QUEST_ID, it) }
  }

  /** Maps this document to a [Venue], or returns null if it is missing or malformed. */
  private fun DocumentSnapshot.toVenue(): Venue? {
    if (!exists()) return null
    return try {
      Venue(
          id = id,
          name = required(NAME) { getString(it) },
          location = getGeoPoint(LOCATION)?.let { Location(it.latitude, it.longitude) },
          radiusMeters = required(RADIUS_METERS) { getLong(it) }.toInt(),
          address = getString(ADDRESS),
          createdAt = required(CREATED_AT) { getTimestamp(it) }.toDate().time,
          featuredQuestId = getString(FEATURED_QUEST_ID),
      )
    } catch (e: Exception) {
      Log.w(TAG, "Skipping malformed venue document $id", e)
      null
    }
  }

  /** Reads [field] with [read], throwing if it is absent. */
  private fun <T : Any> required(field: String, read: (String) -> T?): T =
      read(field) ?: throw IllegalArgumentException("Missing field $field")

  private companion object {
    const val TAG = "VenueRepositoryFirestore"
    const val VENUES = "venues"

    const val NAME = "name"
    const val LOCATION = "location"
    const val RADIUS_METERS = "radiusMeters"
    const val ADDRESS = "address"
    const val CREATED_AT = "createdAt"
    const val FEATURED_QUEST_ID = "featuredQuestId"
  }
}
