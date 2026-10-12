// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import org.junit.Assert.assertEquals
import org.junit.Test

class CreateQuestUiStateTest {
  @Test
  fun walkMinutesRoundUpAndNeverFallBelowOne() {
    assertEquals(1, CreateQuestUiState(radiusMeters = 20).walkMinutes)
    assertEquals(1, CreateQuestUiState(radiusMeters = 80).walkMinutes)
    assertEquals(2, CreateQuestUiState(radiusMeters = 81).walkMinutes)
    assertEquals(3, CreateQuestUiState(radiusMeters = 200).walkMinutes)
  }
}
