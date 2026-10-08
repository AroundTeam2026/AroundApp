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
  NAME_TOO_LONG,
  SIGN_IN_REQUIRED,
  SAVE_FAILED,
}

/** [venueId] signals that initialization is complete and location setup can begin. */
data class VenueInitializationUiState(
    val businessName: String = "",
    val isSaving: Boolean = false,
    val error: VenueInitializationError? = null,
    val venueId: String? = null,
    val isLoading: Boolean = false,
    val hasVenue: Boolean = false,
) {
  val canContinue: Boolean
    get() = !isLoading && !isSaving && venueId == null

  val canEditName: Boolean
    get() = canContinue && !hasVenue
}

/** Creates the signed-in venue's profile once, before its location is configured. */
class VenueInitializationViewModel(
    private val authRepository: AuthRepository,
    private val venueRepository: VenueRepository,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow(VenueInitializationUiState(isLoading = true))
  val uiState = mutableUiState.asStateFlow()

  init {
    viewModelScope.launch {
      try {
        val uid = authRepository.currentUserId.value
        val venue = if (uid.isNullOrBlank()) null else venueRepository.getVenue(uid)
        if (venue != null) {
          mutableUiState.value = uiState.value.copy(businessName = venue.name, hasVenue = true)
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        mutableUiState.value = uiState.value.copy(error = VenueInitializationError.SAVE_FAILED)
      } finally {
        mutableUiState.value = uiState.value.copy(isLoading = false)
      }
    }
  }

  /** Consumes the navigation signal so Back from location does not immediately reopen it. */
  fun onLocationNavigationHandled() {
    mutableUiState.value = uiState.value.copy(venueId = null)
  }

  fun updateBusinessName(name: String) {
    if (!uiState.value.canEditName) return
    mutableUiState.value = uiState.value.copy(businessName = name, error = null)
  }

  fun submit() {
    val state = uiState.value
    if (!state.canContinue) return
    val name = state.businessName.trim()
    if (!state.hasVenue && name.isEmpty()) {
      mutableUiState.value = state.copy(error = VenueInitializationError.EMPTY_NAME)
      return
    }
    if (!state.hasVenue && name.length > VenueLimits.MAX_NAME_LENGTH) {
      mutableUiState.value = state.copy(error = VenueInitializationError.NAME_TOO_LONG)
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
        val savedVenue = getOrCreateVenue(uid, name)
        mutableUiState.value =
            uiState.value.copy(
                isSaving = false,
                venueId = uid,
                businessName = savedVenue.name,
                hasVenue = true,
            )
      } catch (cancelled: CancellationException) {
        mutableUiState.value = uiState.value.copy(isSaving = false)
        throw cancelled
      } catch (_: Exception) {
        mutableUiState.value =
            uiState.value.copy(isSaving = false, error = VenueInitializationError.SAVE_FAILED)
      }
    }
  }

  private suspend fun getOrCreateVenue(uid: String, name: String): Venue {
    venueRepository.getVenue(uid)?.let {
      return it
    }
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
    if (result.isSuccess) return venue
    // Another initialization may have won the atomic create in the meantime.
    return venueRepository.getVenue(uid) ?: throw result.exceptionOrNull()!!
  }
}
