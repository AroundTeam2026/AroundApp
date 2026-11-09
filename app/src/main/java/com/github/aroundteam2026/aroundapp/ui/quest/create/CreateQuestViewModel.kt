// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepositoryProvider
import com.github.aroundteam2026.aroundapp.ui.SaveState
import java.time.Clock
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the quest form of the signed-in venue, to create a quest, or to edit the quest [questId].
 *
 * Values are kept as typed and trimmed on save, so editing never moves the cursor; edits are
 * ignored while saving. The radius starts at the venue's own once loaded, unless already chosen,
 * and is [QuestLimits.DEFAULT_RADIUS_METERS] until then. The venue's location centres the radius
 * map and places the saved quest; if the venue can't be read, the form says so and [retryVenue]
 * reads it again. [clock] rejects a reward expiry that has passed.
 *
 * To edit, the form reads the quest and fills itself in with it, keeping its radius over the
 * venue's. Saving then updates that quest, keeping its id and status. If the quest can't be read or
 * no longer exists, [CreateQuestUiState.questUnavailable] tells the screen to close.
 */
class CreateQuestViewModel(
    private val authRepository: AuthRepository,
    private val venueRepository: VenueRepository,
    private val questRepository: QuestRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val questId: String? = null,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow(CreateQuestUiState(editing = questId != null))
  /** The form as the venue has filled it in so far. */
  val uiState: StateFlow<CreateQuestUiState> = mutableUiState.asStateFlow()

  /** Whether the venue has chosen a radius, which the venue's own radius must not overwrite. */
  private var radiusChosen = false

  /** The signed-in venue, once loaded; the saved quest copies its id, name and location. */
  private var venue: Venue? = null

  /** The quest being edited, once read; its id and status are kept when it is saved. */
  private var editedQuest: Quest? = null

  init {
    // In parallel: the quest's radius wins whichever comes first, as it sets radiusChosen
    if (questId != null) viewModelScope.launch { loadQuest(questId) }
    viewModelScope.launch { loadVenue() }
  }

  /** Sets the title to [value] and marks it as edited. */
  fun updateTitle(value: String) {
    edit { it.copy(title = value, titleTouched = true) }
  }

  /** Sets the description to [value] and marks it as edited. */
  fun updateDescription(value: String) {
    edit { it.copy(description = value, descriptionTouched = true) }
  }

  /** Sets the requirements to [value] and marks them as edited. */
  fun updateRequirements(value: String) {
    edit { it.copy(requirements = value, requirementsTouched = true) }
  }

  /** Sets the kind of proof explorers submit to [type]. */
  fun updateProofType(type: ProofType) {
    edit { it.copy(proofType = type) }
  }

  /** Sets the radius to the preset [meters] and hides the slider. */
  fun selectRadiusPreset(meters: Int) {
    radiusChosen = true
    edit { it.copy(radiusMeters = clampRadius(meters), customRadius = false) }
  }

  /** Shows the slider, keeping the current radius as its starting point. */
  fun selectCustomRadius() {
    radiusChosen = true
    edit { it.copy(customRadius = true) }
  }

  /** Sets the radius to [meters], clamped to [QuestLimits]. */
  fun updateRadius(meters: Int) {
    radiusChosen = true
    edit { it.copy(radiusMeters = clampRadius(meters)) }
  }

  /** Raises the party size by one. */
  fun incrementPartySize() {
    edit { it.copy(minPartySize = it.minPartySize + 1) }
  }

  /** Lowers the party size by one, never below [QuestLimits.MIN_PARTY_SIZE]. */
  fun decrementPartySize() {
    edit { it.copy(minPartySize = (it.minPartySize - 1).coerceAtLeast(QuestLimits.MIN_PARTY_SIZE)) }
  }

  /** Sets whether the party must have exactly, rather than at least, the party size. */
  fun updateExactPartySize(exact: Boolean) {
    edit { it.copy(exactPartySize = exact) }
  }

  /** Picks [type] as the reward, or no reward if it is null. */
  fun selectRewardType(type: RewardType?) {
    updateReward { it.copy(type = type) }
  }

  /** Sets the discount amount to [value], as typed, and marks it as edited. */
  fun updateDiscountAmount(value: String) {
    updateReward { it.copy(discountAmount = value, discountAmountTouched = true) }
  }

  /** Sets whether the discount is in percent or francs to [unit]. */
  fun updateDiscountUnit(unit: DiscountUnit) {
    updateReward { it.copy(discountUnit = unit) }
  }

  /** Sets the free item to [value] and marks it as edited. */
  fun updateRewardItemName(value: String) {
    updateReward { it.copy(itemName = value, itemNameTouched = true) }
  }

  /** Sets the specifics of the picked reward type to [value]. */
  fun updateRewardSpecifics(value: String) {
    updateReward { it.withSpecifics(value) }
  }

  /** Sets the description of an other reward to [value] and marks it as edited. */
  fun updateRewardDescription(value: String) {
    updateReward { it.copy(description = value, descriptionTouched = true) }
  }

  /** Sets the last day the reward can be used to [date], or removes the expiry if it is null. */
  fun updateExpiryDate(date: LocalDate?) {
    updateReward { it.copy(expiryDate = date, expiryInPast = isPast(date)) }
  }

  /**
   * Saves the form as a new active quest of the venue, or over the quest being edited, if it
   * [CreateQuestUiState.canSave]. A reward expiry that has passed since it was picked shows its
   * error instead. The form keeps its values whether the save succeeds or fails.
   */
  fun submit() {
    val form = mutableUiState.value
    val venue = venue
    val edited = editedQuest
    if (!form.canSave || venue == null || (form.editing && edited == null)) return
    if (form.reward.type != null && isPast(form.reward.expiryDate)) {
      updateReward { it.copy(expiryInPast = true) }
      return
    }
    // Built outside the try, so a quest that can't be built is a bug, not a failed save
    val quest =
        form.toQuest(venue, clock.zone).let {
          if (edited == null) it else it.copy(id = edited.id, status = edited.status)
        }
    mutableUiState.update { it.copy(saveState = SaveState.Saving) }
    viewModelScope.launch {
      val result =
          try {
            if (edited == null) questRepository.createQuest(quest)
            else questRepository.updateQuest(quest).map { edited.id }
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (e: Exception) {
            Result.failure(e)
          }
      val saveState = result.fold({ SaveState.Saved(it) }, { SaveState.Failed })
      mutableUiState.update { it.copy(saveState = saveState) }
    }
  }

  /** Forgets a failed save once the venue has been told about it, so it is told only once. */
  fun clearSaveError() {
    mutableUiState.update {
      if (it.saveState == SaveState.Failed) it.copy(saveState = SaveState.Idle) else it
    }
  }

  /**
   * Reads the venue again after it couldn't be read, keeping the form as filled in. Does nothing
   * otherwise, or while a retry is already under way.
   */
  fun retryVenue() {
    val form = mutableUiState.value
    if (!form.venueLoadFailed || form.retryingVenue) return
    mutableUiState.update { it.copy(retryingVenue = true) }
    viewModelScope.launch { loadVenue() }
  }

  /**
   * Reads the quest [id] and fills the form in with it, or flags it as unavailable if it can't be
   * read or doesn't exist.
   */
  private suspend fun loadQuest(id: String) {
    val quest =
        try {
          questRepository.getQuest(id)
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (e: Exception) {
          Log.w(TAG, "Reading quest $id failed", e)
          null
        }
    if (quest == null) {
      mutableUiState.update { it.copy(questUnavailable = true) }
      return
    }
    editedQuest = quest
    radiusChosen = true
    mutableUiState.update { it.filledWith(quest, clock.zone, LocalDate.now(clock)) }
  }

  private fun isPast(date: LocalDate?) = date != null && date.isBefore(LocalDate.now(clock))

  private fun updateReward(transform: (RewardFormState) -> RewardFormState) {
    edit { it.copy(reward = transform(it.reward)) }
  }

  /**
   * Applies [transform] to the form, unless it is being saved: the quest being saved is the one
   * submitted, so later edits would be lost. Edits are also ignored until the quest to edit is
   * read, as its values would then overwrite them.
   */
  private fun edit(transform: (CreateQuestUiState) -> CreateQuestUiState) {
    if (questId != null && editedQuest == null) return
    mutableUiState.update { if (it.saveState == SaveState.Saving) it else transform(it) }
  }

  /**
   * Reads the signed-in venue: its location for the radius map, and its radius as the starting one,
   * unless the venue has already chosen one. A read that fails is shown as
   * [CreateQuestUiState.venueLoadFailed] until a read succeeds.
   */
  private suspend fun loadVenue() {
    val venueId = authRepository.currentUserId.value ?: return
    val venue =
        try {
          venueRepository.getVenue(venueId)
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (e: Exception) {
          Log.w(TAG, "Reading venue $venueId failed", e)
          // The form stays filled in; without the venue's location it can't be saved until a retry
          mutableUiState.update { it.copy(venueLoadFailed = true, retryingVenue = false) }
          return
        }
    mutableUiState.update { it.copy(venueLoadFailed = false, retryingVenue = false) }
    if (venue == null) return
    this.venue = venue
    val radius = clampRadius(venue.radiusMeters)
    mutableUiState.update {
      if (radiusChosen) it.copy(venueLocation = venue.location)
      else
          it.copy(
              venueLocation = venue.location,
              radiusMeters = radius,
              customRadius = !RadiusPreset.isPreset(radius),
          )
    }
  }

  private fun clampRadius(meters: Int) =
      meters.coerceIn(QuestLimits.MIN_RADIUS_METERS, QuestLimits.MAX_RADIUS_METERS)

  companion object {
    private const val TAG = "CreateQuestViewModel"

    /**
     * Builds the [CreateQuestViewModel] of the signed-in venue with the app's repositories, to edit
     * the quest [questId], or to create one if it is null.
     */
    fun factory(questId: String? = null): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        CreateQuestViewModel(
            AuthRepositoryProvider.repository,
            VenueRepositoryProvider.repository,
            QuestRepositoryProvider.repository,
            questId = questId,
        )
      }
    }
  }
}
