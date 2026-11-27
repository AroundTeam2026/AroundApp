// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.presence

import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.common.Location
import kotlinx.coroutines.flow.Flow

/** Which explorers other explorers can see, and until when. */
interface PresenceRepository {
  /** Shows [uid] to other explorers at [location] until [availableUntil]. */
  suspend fun show(uid: String, location: Location, availableUntil: Long): Result<Unit>

  /** Hides [uid]: deletes their document. */
  suspend fun hide(uid: String): Result<Unit>

  /** The explorer's own document, or null while they are hidden. */
  fun observeOwn(uid: String): Flow<ExplorerPresence?>

  /** Explorers in [bounds] still available at [now]. */
  fun observeNearby(bounds: GeoBounds, now: Long): Flow<List<ExplorerPresence>>
}
