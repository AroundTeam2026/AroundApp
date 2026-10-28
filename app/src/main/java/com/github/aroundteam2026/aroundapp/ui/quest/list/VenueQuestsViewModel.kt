// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the venue's Quests tab shows. */
sealed interface VenueQuestsUiState {
  /** The quests haven't arrived yet. */
  data object Loading : VenueQuestsUiState

  /** The venue's [quests], newest first; empty if it has none yet. */
  data class Loaded(val quests: List<Quest>) : VenueQuestsUiState

  /** Listening to the quests failed. */
  data object Failed : VenueQuestsUiState
}

/**
 * Holds the quests of the signed-in venue, whatever their status, newest first. It keeps up as they
 * change, so a quest saved from the form is listed when the venue returns to the tab.
 */
class VenueQuestsViewModel(authRepository: AuthRepository, questRepository: QuestRepository) :
    ViewModel() {
  /** What the tab shows now. */
  val uiState: StateFlow<VenueQuestsUiState> =
      (authRepository.currentUserId.value?.let { venueId ->
            questRepository
                .observeQuestsByVenue(venueId)
                .map<List<Quest>, VenueQuestsUiState> { quests ->
                  VenueQuestsUiState.Loaded(quests.sortedByDescending(Quest::createdAt))
                }
                // The repository's flow throws if the backend stops the listener
                .catch { emit(VenueQuestsUiState.Failed) }
          } ?: emptyFlow())
          .stateIn(viewModelScope, SharingStarted.Eagerly, VenueQuestsUiState.Loading)

  companion object {
    /** Builds the [VenueQuestsViewModel] of the signed-in venue with the app's repositories. */
    fun factory(): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        VenueQuestsViewModel(AuthRepositoryProvider.repository, QuestRepositoryProvider.repository)
      }
    }
  }
}
