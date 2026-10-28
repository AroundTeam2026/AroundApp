// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import java.io.IOException

/** Holds [venues] like [FakeVenueRepository], but reading one throws while [offline]. */
internal class OfflineVenueRepository(
    venues: List<Venue>,
    private val delegate: VenueRepository = FakeVenueRepository(venues),
) : VenueRepository by delegate {
  /** Whether reading a venue throws, as Firestore can without a connection. */
  var offline = true

  override suspend fun getVenue(venueId: String): Venue? =
      if (offline) throw IOException() else delegate.getVenue(venueId)
}
