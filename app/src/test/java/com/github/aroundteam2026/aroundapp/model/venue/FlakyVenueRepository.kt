// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.venue

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred

/**
 * A [FakeVenueRepository] holding [venue] whose first [failures] reads fail as if offline. A read
 * that doesn't fail waits for [gate] when given, and returns the venue that completes it, or the
 * stored venue when the gate completes with null.
 */
class FlakyVenueRepository
private constructor(
    private val saved: FakeVenueRepository,
    private var failures: Int,
    private val gate: CompletableDeferred<Venue?>?,
) : VenueRepository by saved {
  constructor(
      failures: Int,
      venue: Venue?,
      gate: CompletableDeferred<Venue?>? = null,
  ) : this(FakeVenueRepository(listOfNotNull(venue)), failures, gate)

  /** How many times [getVenue] was called, failed reads included. */
  var reads = 0
    private set

  override suspend fun getVenue(venueId: String): Venue? {
    reads++
    if (failures > 0) {
      failures--
      throw IOException("offline")
    }
    return gate?.await() ?: saved.getVenue(venueId)
  }
}
