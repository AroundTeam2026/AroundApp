// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.model.location

import com.github.aroundteam2026.aroundapp.model.common.Location
import kotlinx.coroutines.CompletableDeferred

/** Answers with [location], after [gate] opens if there is one, and counts the lookups. */
class FakeLocationRepository(var location: Location?) : LocationRepository {
  var calls = 0
  var gate: CompletableDeferred<Unit>? = null
  var preciseLocation: Location? = location
  var preciseCalls = 0
  var preciseGate: CompletableDeferred<Unit>? = null

  override suspend fun currentLocation(): Location? {
    calls++
    gate?.await()
    return location
  }

  override suspend fun currentPreciseLocation(): Location? {
    preciseCalls++
    preciseGate?.await()
    return preciseLocation
  }
}
