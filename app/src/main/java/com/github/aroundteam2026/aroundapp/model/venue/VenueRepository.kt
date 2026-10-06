// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.venue

import kotlinx.coroutines.flow.Flow

/** Reads and creates [Venue]s. ViewModels depend on this interface only. */
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
}
