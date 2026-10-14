// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import java.time.Clock
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the quest form of the signed-in venue. Values are kept as typed, untrimmed, so editing
 * never moves the cursor; they are trimmed when the quest is saved.
 *
 * The radius starts at the venue's own radius once it is loaded, unless the venue has already
 * chosen one; until then, or if the venue can't be read, it is [QuestLimits.DEFAULT_RADIUS_METERS].
 * The venue's location, once loaded, centres the radius map. [clock] tells which day it is, to
 * reject a reward expiry that has already passed.
 */
class CreateQuestViewModel(
    private val authRepository: AuthRepository,
    private val venueRepository: VenueRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
  private val mutableUiState = MutableStateFlow(CreateQuestUiState())
  /** The form as the venue has filled it in so far. */
  val uiState: StateFlow<CreateQuestUiState> = mutableUiState.asStateFlow()

  /** Whether the venue has chosen a radius, which the venue's own radius must not overwrite. */
  private var radiusChosen = false

  init {
    viewModelScope.launch { loadVenue() }
  }

  /** Sets the title to [value] and marks it as edited. */
  fun updateTitle(value: String) {
    mutableUiState.update { it.copy(title = value, titleTouched = true) }
  }

  /** Sets the description to [value] and marks it as edited. */
  fun updateDescription(value: String) {
    mutableUiState.update { it.copy(description = value, descriptionTouched = true) }
  }

  /** Sets the requirements to [value] and marks them as edited. */
  fun updateRequirements(value: String) {
    mutableUiState.update { it.copy(requirements = value, requirementsTouched = true) }
  }

  /** Sets the kind of proof explorers submit to [type]. */
  fun updateProofType(type: ProofType) {
    mutableUiState.update { it.copy(proofType = type) }
  }

  /** Sets the radius to the preset [meters] and hides the slider. */
  fun selectRadiusPreset(meters: Int) {
    radiusChosen = true
    mutableUiState.update { it.copy(radiusMeters = clampRadius(meters), customRadius = false) }
  }

  /** Shows the slider, keeping the current radius as its starting point. */
  fun selectCustomRadius() {
    radiusChosen = true
    mutableUiState.update { it.copy(customRadius = true) }
  }

  /** Sets the radius to [meters], clamped to [QuestLimits]. */
  fun updateRadius(meters: Int) {
    radiusChosen = true
    mutableUiState.update { it.copy(radiusMeters = clampRadius(meters)) }
  }

  /** Raises the party size by one. */
  fun incrementPartySize() {
    mutableUiState.update { it.copy(minPartySize = it.minPartySize + 1) }
  }

  /** Lowers the party size by one, never below [QuestLimits.MIN_PARTY_SIZE]. */
  fun decrementPartySize() {
    mutableUiState.update {
      it.copy(minPartySize = (it.minPartySize - 1).coerceAtLeast(QuestLimits.MIN_PARTY_SIZE))
    }
  }

  /** Sets whether the party must have exactly, rather than at least, the party size. */
  fun updateExactPartySize(exact: Boolean) {
    mutableUiState.update { it.copy(exactPartySize = exact) }
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
    val inPast = date != null && date.isBefore(LocalDate.now(clock))
    updateReward { it.copy(expiryDate = date, expiryInPast = inPast) }
  }

  private fun updateReward(transform: (RewardFormState) -> RewardFormState) {
    mutableUiState.update { it.copy(reward = transform(it.reward)) }
  }

  /**
   * Reads the signed-in venue: its location for the radius map, and its radius as the starting one,
   * unless the venue has already chosen one.
   */
  private suspend fun loadVenue() {
    val venueId = authRepository.currentUserId.value ?: return
    val venue =
        try {
          venueRepository.getVenue(venueId)
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (_: Exception) {
          // The default radius still makes a valid quest, so the form stays usable
          null
        } ?: return
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
}
