// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.venue

import com.github.aroundteam2026.aroundapp.model.common.Location
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow

/**
 * A [VenueRepository] keeping [venues] in a [FakeVenueRepository], whose reads and area writes wait
 * for their gate to open if there is one, and fail when the test says so. It counts both.
 */
class GatedVenueRepository(venues: List<Venue> = emptyList()) : VenueRepository {
  private val stored = FakeVenueRepository(venues)

  /** When set, [getVenue] waits for it to complete. */
  var readGate: CompletableDeferred<Unit>? = null

  /** When set, [setArea] waits for it to complete. */
  var writeGate: CompletableDeferred<Unit>? = null

  /** When set, [getVenue] throws it. */
  var readError: Exception? = null

  /** When set, [setArea] returns it as a failure without writing. */
  var writeFailure: Exception? = null

  /** When set, [setArea] throws it without writing. */
  var writeError: Exception? = null

  var reads = 0
  var writes = 0

  override fun observeVenue(venueId: String): Flow<Venue?> = stored.observeVenue(venueId)

  override suspend fun getVenue(venueId: String): Venue? {
    reads++
    readGate?.await()
    readError?.let { throw it }
    return stored.getVenue(venueId)
  }

  override suspend fun createVenue(venue: Venue): Result<Unit> = stored.createVenue(venue)

  override suspend fun setArea(
      venueId: String,
      location: Location,
      radiusMeters: Int,
  ): Result<Unit> {
    writes++
    writeGate?.await()
    writeError?.let { throw it }
    writeFailure?.let {
      return Result.failure(it)
    }
    return stored.setArea(venueId, location, radiusMeters)
  }
}
