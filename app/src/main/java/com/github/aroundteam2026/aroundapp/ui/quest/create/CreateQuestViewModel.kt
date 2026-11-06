// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.address.AddressSearchRepository
import com.github.aroundteam2026.aroundapp.model.address.AddressSearchRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.address.AddressSearchResult
import com.github.aroundteam2026.aroundapp.model.address.AddressSuggestion
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepositoryProvider
import com.github.aroundteam2026.aroundapp.ui.SaveState
import com.github.aroundteam2026.aroundapp.ui.venue.AddressSearchStatus
import com.github.aroundteam2026.aroundapp.ui.venue.AddressSearchUiState
import com.github.aroundteam2026.aroundapp.ui.venue.VenueAreaViewModel
import com.github.aroundteam2026.aroundapp.ui.venue.showing
import java.time.Clock
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the quest form of the signed-in venue.
 *
 * Values are kept as typed and trimmed on save, so editing never moves the cursor; edits are
 * ignored while saving. The radius starts at the venue's own once loaded, unless already chosen,
 * and is [QuestLimits.DEFAULT_RADIUS_METERS] until then. The venue's location centres the radius
 * map and places the saved quest; if the venue can't be read, the form says so and [retryVenue]
 * reads it again. The venue can place the quest somewhere else instead, by searching its address
 * with [addressSearchRepository] and moving its pin. [clock] rejects a reward expiry that has
 * passed.
 */
class CreateQuestViewModel(
    private val authRepository: AuthRepository,
    private val venueRepository: VenueRepository,
    private val questRepository: QuestRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val addressSearchRepository: AddressSearchRepository = NoAddressSearch,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow(CreateQuestUiState())
  /** The form as the venue has filled it in so far. */
  val uiState: StateFlow<CreateQuestUiState> = mutableUiState.asStateFlow()

  /** Whether the venue has chosen a radius, which the venue's own radius must not overwrite. */
  private var radiusChosen = false

  /** The signed-in venue, once loaded; the saved quest copies its id, name and location. */
  private var venue: Venue? = null

  /** The address search waiting for typing to pause, or running. */
  private var addressLookup: Job? = null

  init {
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

  /** Places the quest at the venue's own location, forgetting the one chosen and its search. */
  fun placeAtVenue() {
    addressLookup?.cancel()
    edit {
      it.copy(questLocation = null, addressQuery = "", addressSearch = AddressSearchUiState())
    }
  }

  /** Places the quest somewhere else, starting from the venue's location until it is moved. */
  fun placeElsewhere() {
    edit { it.copy(questLocation = it.questLocation ?: it.venueLocation) }
  }

  /**
   * Moves the quest to [location], where the venue tapped the map or dropped the pin, and closes
   * the address suggestions. Does nothing while the quest is at the venue.
   */
  fun moveQuest(location: Location) {
    addressLookup?.cancel()
    edit {
      if (it.atVenue) it
      else it.copy(questLocation = location, addressSearch = AddressSearchUiState())
    }
  }

  /**
   * Sets the address field to [query], as typed. Once the trimmed query has at least
   * [VenueAreaViewModel.MIN_QUERY_LENGTH] characters, it is searched after
   * [VenueAreaViewModel.SEARCH_DELAY_MILLIS] without typing, around the quest; each new query
   * replaces the search still pending or running.
   */
  fun updateAddressQuery(query: String) {
    addressLookup?.cancel()
    val searches = query.trim().length >= VenueAreaViewModel.MIN_QUERY_LENGTH
    edit {
      it.copy(
          addressQuery = query,
          addressSearch =
              if (searches) it.addressSearch.copy(status = AddressSearchStatus.SEARCHING)
              else AddressSearchUiState(),
      )
    }
    if (searches) {
      addressLookup = lookUpAddress(query, delayMillis = VenueAreaViewModel.SEARCH_DELAY_MILLIS)
    }
  }

  /** Searches the address field at once, however short, unless it is blank: the search key. */
  fun searchAddress() {
    val query = mutableUiState.value.addressQuery
    if (query.isBlank()) return
    addressLookup?.cancel()
    edit { it.copy(addressSearch = it.addressSearch.copy(status = AddressSearchStatus.SEARCHING)) }
    addressLookup = lookUpAddress(query, delayMillis = 0)
  }

  /** Moves the quest to [suggestion], whose address the field then shows. */
  fun pickAddress(suggestion: AddressSuggestion) {
    addressLookup?.cancel()
    edit {
      it.copy(
          questLocation = suggestion.location,
          addressQuery = suggestion.address,
          addressSearch = AddressSearchUiState(),
      )
    }
  }

  /** Empties the address field and drops its search; the quest stays where it is. */
  fun clearAddress() {
    addressLookup?.cancel()
    edit { it.copy(addressQuery = "", addressSearch = AddressSearchUiState()) }
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
   * Saves the form as a new active quest of the venue, if it [CreateQuestUiState.canSave]. A reward
   * expiry that has passed since it was picked shows its error instead. The form keeps its values
   * whether the save succeeds or fails.
   */
  fun submit() {
    val form = mutableUiState.value
    val venue = venue
    if (!form.canSave || venue == null) return
    if (form.reward.type != null && isPast(form.reward.expiryDate)) {
      updateReward { it.copy(expiryInPast = true) }
      return
    }
    // Built outside the try, so a quest that can't be built is a bug, not a failed save
    val quest = form.toQuest(venue, clock.zone)
    mutableUiState.update { it.copy(saveState = SaveState.Saving) }
    viewModelScope.launch {
      val result =
          try {
            questRepository.createQuest(quest)
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

  /** Searches [query] after [delayMillis], preferring addresses around where the quest is. */
  private fun lookUpAddress(query: String, delayMillis: Long): Job = viewModelScope.launch {
    delay(delayMillis)
    val near = mutableUiState.value.location?.boundsWithin(VenueAreaViewModel.FRAMED_RADIUS_METERS)
    val result = addressSearchRepository.search(query, near)
    edit { it.copy(addressSearch = it.addressSearch.showing(result)) }
  }

  private fun isPast(date: LocalDate?) = date != null && date.isBefore(LocalDate.now(clock))

  private fun updateReward(transform: (RewardFormState) -> RewardFormState) {
    edit { it.copy(reward = transform(it.reward)) }
  }

  /**
   * Applies [transform] to the form, unless it is being saved: the quest being saved is the one
   * submitted, so later edits would be lost.
   */
  private fun edit(transform: (CreateQuestUiState) -> CreateQuestUiState) {
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
      val placed = it.copy(venueName = venue.name, venueLocation = venue.location)
      if (radiusChosen) placed
      else placed.copy(radiusMeters = radius, customRadius = !RadiusPreset.isPreset(radius))
    }
  }

  private fun clampRadius(meters: Int) =
      meters.coerceIn(QuestLimits.MIN_RADIUS_METERS, QuestLimits.MAX_RADIUS_METERS)

  companion object {
    private const val TAG = "CreateQuestViewModel"

    /** Builds the [CreateQuestViewModel] of the signed-in venue with the app's repositories. */
    fun factory(): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        CreateQuestViewModel(
            AuthRepositoryProvider.repository,
            VenueRepositoryProvider.repository,
            QuestRepositoryProvider.repository,
            addressSearchRepository =
                AddressSearchRepositoryProvider.repository(this[APPLICATION_KEY]!!),
        )
      }
    }
  }
}

/** The search of a form built without one, as in tests that don't search: it is unavailable. */
private object NoAddressSearch : AddressSearchRepository {
  override suspend fun search(query: String, near: GeoBounds?) = AddressSearchResult.Unavailable
}
