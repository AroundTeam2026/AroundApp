// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.venue

import com.github.aroundteam2026.aroundapp.model.common.Location
import kotlinx.coroutines.flow.Flow

/** Reads, creates and updates [Venue]s. ViewModels depend on this interface only. */
interface VenueRepository {
  /** Emits the current venue and subsequent changes, or null when [venueId] does not exist. */
  fun observeVenue(venueId: String): Flow<Venue?>

  /** Returns the venue for [venueId], or null when it does not exist. */
  suspend fun getVenue(venueId: String): Venue?

  /**
   * Creates [venue]. Fails with [IllegalStateException] if a venue with its id already exists,
   * without replacing it.
   */
  suspend fun createVenue(venue: Venue): Result<Unit>

  /**
   * Sets the area of the venue identified by [venueId]: replaces its [Venue.location] and
   * [Venue.radiusMeters] and leaves every other field unchanged.
   *
   * The existence check and the write are one atomic step. Fails with [IllegalArgumentException] if
   * the venue does not exist, without creating it.
   *
   * [radiusMeters] is not checked against [VenueLimits]; the ViewModel enforces the limits before
   * calling.
   *
   * @param venueId Id of the venue to update.
   * @param location The marker the venue placed on the map.
   * @param radiusMeters Distance from [location] within which a visit counts.
   */
  suspend fun setArea(venueId: String, location: Location, radiusMeters: Int): Result<Unit>
}
