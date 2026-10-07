// Co-authored-by: OpenAI Codex
package com.github.aroundteam2026.aroundapp.ui.venue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VenueInitializationError {
  EMPTY_NAME,
  SIGN_IN_REQUIRED,
  SAVE_FAILED,
}

/** [venueId] signals that initialization is complete and location setup can begin. */
data class VenueInitializationUiState(
    val businessName: String = "",
    val isSaving: Boolean = false,
    val error: VenueInitializationError? = null,
    val venueId: String? = null,
) {
  val canContinue: Boolean
    get() = !isSaving && venueId == null
}

/** Creates the signed-in venue's profile once, before its location is configured. */
class VenueInitializationViewModel(
    private val authRepository: AuthRepository,
    private val venueRepository: VenueRepository,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow(VenueInitializationUiState())
  val uiState = mutableUiState.asStateFlow()

  /** Consumes the navigation signal so Back from location does not immediately reopen it. */
  fun onLocationNavigationHandled() {
    mutableUiState.value = uiState.value.copy(venueId = null)
  }

  fun updateBusinessName(name: String) {
    if (!uiState.value.canContinue) return
    mutableUiState.value = uiState.value.copy(businessName = name, error = null)
  }

  fun submit() {
    val state = uiState.value
    if (!state.canContinue) return
    val name = state.businessName.trim()
    if (name.isEmpty()) {
      mutableUiState.value = state.copy(error = VenueInitializationError.EMPTY_NAME)
      return
    }
    val uid = authRepository.currentUserId.value
    if (uid.isNullOrBlank()) {
      mutableUiState.value = state.copy(error = VenueInitializationError.SIGN_IN_REQUIRED)
      return
    }

    mutableUiState.value = state.copy(isSaving = true, error = null)
    viewModelScope.launch {
      try {
        if (venueRepository.getVenue(uid) == null) {
          val venue =
              Venue(
                  id = uid,
                  name = name,
                  location = null,
                  radiusMeters = VenueLimits.DEFAULT_RADIUS_METERS,
                  address = null,
                  createdAt = currentTimeMillis(),
              )
          val result = venueRepository.createVenue(venue)
          // Another initialization may have won the atomic create in the meantime.
          if (result.isFailure && venueRepository.getVenue(uid) == null) {
            throw result.exceptionOrNull()!!
          }
        }
        mutableUiState.value = uiState.value.copy(isSaving = false, venueId = uid)
      } catch (cancelled: CancellationException) {
        mutableUiState.value = uiState.value.copy(isSaving = false)
        throw cancelled
      } catch (_: Exception) {
        mutableUiState.value =
            uiState.value.copy(isSaving = false, error = VenueInitializationError.SAVE_FAILED)
      }
    }
  }
}
