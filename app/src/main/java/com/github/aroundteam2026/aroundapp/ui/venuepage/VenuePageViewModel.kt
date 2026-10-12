// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds the page of one venue.
 *
 * For now it only fixes the contract the page renders: it stays [VenuePageStatus.LOADING] until it
 * loads the venue and its quests.
 */
class VenuePageViewModel : ViewModel() {
  private val mutableUiState = MutableStateFlow(VenuePageUiState())
  /** What the page shows now; the page observes it. */
  val uiState: StateFlow<VenuePageUiState> = mutableUiState.asStateFlow()

  /** Called when the explorer taps the quest [questId]; see [VenuePageUiState.withQuestTapped]. */
  fun onQuestSelected(questId: String) {
    mutableUiState.update { it.withQuestTapped(questId) }
  }
}
