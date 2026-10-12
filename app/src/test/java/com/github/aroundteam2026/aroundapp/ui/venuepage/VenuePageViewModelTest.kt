// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import org.junit.Assert.assertEquals
import org.junit.Test

class VenuePageViewModelTest {
  private val viewModel = VenuePageViewModel()

  @Test
  fun startsLoading() {
    assertEquals(VenuePageUiState(), viewModel.uiState.value)
  }

  @Test
  fun selectingAQuestThePageDoesNotListChangesNothing() {
    viewModel.onQuestSelected("unknown")
    assertEquals(VenuePageUiState(), viewModel.uiState.value)
  }
}
