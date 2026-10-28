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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

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
 * Holds the quests of the signed-in venue, whatever their status, newest first. It follows the
 * signed-in venue as it changes, and keeps up as its quests change, so a quest saved from the form
 * is listed when the venue returns to the tab. After [VenueQuestsUiState.Failed], [retry] listens
 * again. It only listens while the tab is watched, so a hidden tab costs no reads.
 */
class VenueQuestsViewModel(authRepository: AuthRepository, questRepository: QuestRepository) :
    ViewModel() {
  /** Bumped by [retry]: each new value restarts the listener, even for the same venue. */
  private val retries = MutableStateFlow(0)

  /** What the tab shows now. */
  @OptIn(ExperimentalCoroutinesApi::class)
  val uiState: StateFlow<VenueQuestsUiState> =
      combine(authRepository.currentUserId, retries) { venueId, _ -> venueId }
          .flatMapLatest { venueId ->
            if (venueId == null) flowOf(VenueQuestsUiState.Loading)
            else
                questRepository
                    .observeQuestsByVenue(venueId)
                    .map<List<Quest>, VenueQuestsUiState> { quests ->
                      VenueQuestsUiState.Loaded(quests.sortedByDescending(Quest::createdAt))
                    }
                    // The repository's flow throws if the backend stops the listener
                    .catch { emit(VenueQuestsUiState.Failed) }
          }
          .stateIn(
              viewModelScope,
              SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
              VenueQuestsUiState.Loading,
          )

  /** Listens to the venue's quests again, after listening failed. */
  fun retry() {
    retries.update { it + 1 }
  }

  companion object {
    /** How long the listener outlives the tab, so a quick rotation doesn't restart it. */
    private const val STOP_TIMEOUT_MS = 5_000L

    /** Builds the [VenueQuestsViewModel] of the signed-in venue with the app's repositories. */
    fun factory(): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        VenueQuestsViewModel(AuthRepositoryProvider.repository, QuestRepositoryProvider.repository)
      }
    }
  }
}
