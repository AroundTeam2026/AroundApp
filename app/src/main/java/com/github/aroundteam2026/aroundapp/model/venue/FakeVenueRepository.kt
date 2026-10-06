// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.venue

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map

/**
 * In-memory [VenueRepository] for unit tests and for running the app without Firestore.
 *
 * Creating a venue is atomic: of several concurrent calls with the same id, exactly one succeeds.
 * [observeVenue] emits again only when that venue changes, not on every write.
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
}
