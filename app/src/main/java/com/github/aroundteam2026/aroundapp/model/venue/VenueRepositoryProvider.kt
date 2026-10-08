// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.venue

import com.github.aroundteam2026.aroundapp.model.demo.MapDemoData

/**
 * Builds the app's [VenueRepository], so ViewModels never depend on an implementation.
 *
 * Venues aren't in Firestore yet, so the app keeps them in memory, starting from the demo venues,
 * and shares them with every screen: they last until it closes.
 */
object VenueRepositoryProvider {
  /** The app's venues. */
  val repository: VenueRepository by lazy { create() }

  /** A new in-memory repository holding the demo venues. */
  fun create(): VenueRepository = FakeVenueRepository(MapDemoData.venues())
}
