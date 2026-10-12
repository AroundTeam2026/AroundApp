// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.quest.isValidAt
import com.github.aroundteam2026.aroundapp.model.quest.nextExpiryAfter
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepositoryProvider
import com.github.aroundteam2026.aroundapp.ui.map.INITIALS_AVATAR
import com.github.aroundteam2026.aroundapp.ui.map.NEWEST_FIRST
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the page of the venue [venueId]: its header and the quests explorers can take there.
 *
 * It listens to the venue and its quests, and keeps up as they change and as rewards expire, by
 * [clock]. If listening fails, the page shows [VenuePageStatus.FAILED] until [onRetry].
 */
class VenuePageViewModel(
    private val venueId: String,
    private val venueRepository: VenueRepository,
    private val questRepository: QuestRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow(VenuePageUiState())
  /** What the page shows now; the page observes it. */
  val uiState: StateFlow<VenuePageUiState> = mutableUiState.asStateFlow()

  private var listening: Job? = null

  init {
    listen()
  }

  /** Called when the explorer taps the quest [questId]; see [VenuePageUiState.withQuestTapped]. */
  fun onQuestSelected(questId: String) {
    mutableUiState.update { it.withQuestTapped(questId) }
  }

  /** Called when the explorer asks to try again after a failure; does nothing otherwise. */
  fun onRetry() {
    if (mutableUiState.value.status != VenuePageStatus.FAILED) return
    mutableUiState.value = VenuePageUiState()
    listen()
  }

  /**
   * Listens to the venue and its quests until the ViewModel is cleared or listening fails. The
   * quests are checked again each time a reward expires, even when nothing else changes.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun listen() {
    listening?.cancel()
    listening = viewModelScope.launch {
      combine(
              venueRepository.observeVenue(venueId),
              questRepository.observeQuestsByVenue(venueId),
          ) { venue, quests ->
            venue to quests
          }
          .transformLatest { (venue, quests) ->
            while (true) {
              val now = clock()
              emit(venue to venuePageQuests(quests, venue?.featuredQuestId, now))
              val nextExpiry = quests.nextExpiryAfter(now) ?: break
              delay(nextExpiry - now)
            }
          }
          .catch { mutableUiState.value = VenuePageUiState(status = VenuePageStatus.FAILED) }
          .collect { (venue, quests) -> mutableUiState.update { it.showing(venue, quests) } }
    }
  }

  companion object {
    /** Builds the [VenuePageViewModel] of the venue [venueId] with the app's repositories. */
    fun factory(venueId: String): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        VenuePageViewModel(
            venueId,
            VenueRepositoryProvider.repository,
            QuestRepositoryProvider.repository,
        )
      }
    }
  }
}

/**
 * The rows for the quests among [quests] that are valid at [now]: the quest [featuredQuestId] first
 * when it is one of them, then the others newest first, ties going to the smallest id.
 */
internal fun venuePageQuests(
    quests: List<Quest>,
    featuredQuestId: String?,
    now: Long,
): List<VenuePageQuest> =
    quests
        .filter { it.isValidAt(now) }
        .sortedWith(compareBy<Quest> { it.id != featuredQuestId }.then(NEWEST_FIRST))
        .map { it.toVenuePageQuest() }

/** This page showing [venue] and its valid [quests], or "not found" when [venue] is null. */
private fun VenuePageUiState.showing(
    venue: Venue?,
    quests: List<VenuePageQuest>,
): VenuePageUiState =
    if (venue == null) {
      VenuePageUiState(status = VenuePageStatus.NOT_FOUND)
    } else {
      copy(
              status = VenuePageStatus.LOADED,
              venueName = venue.name,
              avatar = INITIALS_AVATAR(venue, venue.name),
              address = venue.address,
          )
          .withQuests(quests)
    }

private fun Quest.toVenuePageQuest() =
    VenuePageQuest(
        questId = id,
        title = title,
        description = description,
        requirements = requirements,
        proofType = proofType,
        minPartySize = minPartySize,
        reward = reward,
    )
