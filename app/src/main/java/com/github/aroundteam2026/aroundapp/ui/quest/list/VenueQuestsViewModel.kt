// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.quest.RefusedQuestWrites
import com.github.aroundteam2026.aroundapp.model.quest.RefusedQuestWritesProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
 *
 * It also tells when the server refused a quest write of this device, also one made before the app
 * was killed: [refusalNotice] is set until [onRefusalNoticeShown]. [refusedWrites] gives the writes
 * of a venue by its id.
 */
class VenueQuestsViewModel(
    authRepository: AuthRepository,
    private val questRepository: QuestRepository,
    private val refusedWrites: (venueId: String) -> RefusedQuestWrites,
) : ViewModel() {
  private val mutableRefusalNotice = MutableStateFlow(false)

  /** Whether the venue should be told that the server refused a change to its quests. */
  val refusalNotice: StateFlow<Boolean> = mutableRefusalNotice.asStateFlow()

  /** The writes of the venue being listed, to record that the venue was told. */
  private var writes: RefusedQuestWrites? = null

  /** Bumped by [retry]: each new value restarts the listener, even for the same venue. */
  private val retries = MutableStateFlow(0)

  /** What the tab shows now. */
  @OptIn(ExperimentalCoroutinesApi::class)
  val uiState: StateFlow<VenueQuestsUiState> =
      combine(authRepository.currentUserId, retries) { venueId, _ -> venueId }
          .flatMapLatest { venueId ->
            if (venueId == null) flowOf(VenueQuestsUiState.Loading)
            else {
              val venueWrites = refusedWrites(venueId).also { writes = it }
              questRepository
                  .observeQuestsByVenue(venueId)
                  .map<List<Quest>, VenueQuestsUiState> { quests ->
                    venueWrites.hear(quests)
                    mutableRefusalNotice.value = venueWrites.hasUnreportedRefusal
                    VenueQuestsUiState.Loaded(quests.sortedByDescending(Quest::createdAt))
                  }
                  // The repository's flow throws if the backend stops the listener
                  .catch { emit(VenueQuestsUiState.Failed) }
            }
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

  /** Records that the venue was told about the refusals, so they aren't told again. */
  fun onRefusalNoticeShown() {
    writes?.markReported()
    mutableRefusalNotice.value = false
  }

  /**
   * Archives [quest], hiding it from explorers while keeping its reservations and proofs, or if
   * [archived] is false makes it active again. The listener then lists it with its new status.
   */
  fun setArchived(quest: Quest, archived: Boolean) {
    val status = if (archived) QuestStatus.ARCHIVED else QuestStatus.ACTIVE
    viewModelScope.launch { questRepository.updateQuest(quest.copy(status = status)) }
  }

  companion object {
    /** How long the listener outlives the tab, so a quick rotation doesn't restart it. */
    private const val STOP_TIMEOUT_MS = 5_000L

    /** Builds the [VenueQuestsViewModel] of the signed-in venue with the app's repositories. */
    fun factory(): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val application = this[APPLICATION_KEY]!!
        VenueQuestsViewModel(
            AuthRepositoryProvider.repository,
            QuestRepositoryProvider.repository,
        ) {
          RefusedQuestWritesProvider.refusedWrites(application, it)
        }
      }
    }
  }
}
