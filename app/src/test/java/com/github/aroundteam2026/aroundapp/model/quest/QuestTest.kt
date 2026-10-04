// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

import com.github.aroundteam2026.aroundapp.model.common.Location
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests the defaults of [Quest]. */
class QuestTest {
  /** Builds a quest that leaves [Quest.minPartySize] at its default. */
  private fun quest() =
      Quest(
          id = "q1",
          venueId = "v1",
          venueName = "Cafe",
          location = Location(46.52, 6.57),
          radiusMeters = 50,
          title = "Title",
          description = "Description",
          requirements = "Requirements",
          proofType = ProofType.PHOTO,
          reward = null,
          status = QuestStatus.ACTIVE,
          createdAt = 1_000L,
          updatedAt = 1_000L,
      )

  @Test
  fun minPartySize_defaultsToOne() {
    assertEquals(1, quest().minPartySize)
  }
}
