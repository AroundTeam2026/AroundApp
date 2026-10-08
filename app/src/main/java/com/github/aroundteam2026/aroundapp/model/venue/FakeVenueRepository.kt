// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.venue

import com.github.aroundteam2026.aroundapp.model.common.Location
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map

/**
 * In-memory [VenueRepository] for unit tests and for running the app without Firestore.
 *
 * Creating a venue is atomic: of several concurrent calls with the same id, exactly one succeeds.
 * Setting an area is atomic too: the stored venue always holds the location and radius of a single
 * call. [observeVenue] emits again only when that venue changes, not on every write.
 */
class FakeVenueRepository : VenueRepository {
  /** All stored venues, keyed by id. */
  private val venues = MutableStateFlow<Map<String, Venue>>(emptyMap())

  override fun observeVenue(venueId: String): Flow<Venue?> =
      venues.map { it[venueId] }.distinctUntilChanged()

  override suspend fun getVenue(venueId: String): Venue? = venues.value[venueId]

  override suspend fun createVenue(venue: Venue): Result<Unit> {
    val previous = venues.getAndUpdate { current ->
      if (venue.id in current) current else current + (venue.id to venue)
    }
    return if (venue.id in previous) {
      Result.failure(IllegalStateException("Venue already exists"))
    } else {
      Result.success(Unit)
    }
  }

  /**
   * Replaces the location and radius of the venue with [venueId] in one atomic step, deciding the
   * result from the state before the write. Fails with [IllegalArgumentException] if the venue does
   * not exist, without creating it. Does not check [radiusMeters] against [VenueLimits].
   */
  override suspend fun setArea(
      venueId: String,
      location: Location,
      radiusMeters: Int,
  ): Result<Unit> {
    val previous = venues.getAndUpdate { current ->
      val venue = current[venueId]
      if (venue == null) current
      else current + (venueId to venue.copy(location = location, radiusMeters = radiusMeters))
    }
    return if (venueId in previous) {
      Result.success(Unit)
    } else {
      Result.failure(IllegalArgumentException("Venue not found"))
    }
  }
}
