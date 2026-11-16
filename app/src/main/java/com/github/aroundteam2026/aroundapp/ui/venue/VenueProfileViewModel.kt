// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepositoryProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Where loading the venue's profile stands. */
enum class VenueProfileStatus {
  LOADING,
  LOADED,
  /** The venue couldn't be read, or doesn't exist; [VenueProfileViewModel.retry] reads it again. */
  FAILED,
}

/**
 * What the venue's Profile tab shows.
 *
 * @property name The venue's name, once [VenueProfileStatus.LOADED].
 * @property address The street address the venue picked for its marker, or null for none.
 */
data class VenueProfileUiState(
    val status: VenueProfileStatus = VenueProfileStatus.LOADING,
    val name: String = "",
    val address: String? = null,
)

/**
 * The signed-in venue's profile: its name and address, kept up to date as the venue changes them.
 * The venue is the signed-in account, as on the dashboard.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VenueProfileViewModel(
    authRepository: AuthRepository,
    private val venueRepository: VenueRepository,
) : ViewModel() {
  private val retries = MutableStateFlow(0)

  /** What the Profile tab shows now. */
  val uiState: StateFlow<VenueProfileUiState> =
      combine(authRepository.currentUserId, retries) { venueId, _ -> venueId }
          .flatMapLatest { venueId ->
            if (venueId == null) flowOf(VenueProfileUiState()) else listen(venueId)
          }
          .stateIn(
              viewModelScope,
              SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
              VenueProfileUiState(),
          )

  private fun listen(venueId: String) =
      venueRepository
          .observeVenue(venueId)
          .map { venue ->
            if (venue == null) VenueProfileUiState(VenueProfileStatus.FAILED)
            else VenueProfileUiState(VenueProfileStatus.LOADED, venue.name, venue.address)
          }
          // A retry shows the spinner again while the venue is read
          .onStart { emit(VenueProfileUiState()) }
          .catch {
            Log.w(TAG, "Listening to venue $venueId failed", it)
            emit(VenueProfileUiState(VenueProfileStatus.FAILED))
          }

  /** Reads the venue again after a failure. */
  fun retry() {
    retries.update { it + 1 }
  }

  companion object {
    private const val TAG = "VenueProfile"
    private const val STOP_TIMEOUT_MS = 5_000L

    /** Builds the ViewModel on the app's repositories. */
    fun factory(): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        VenueProfileViewModel(AuthRepositoryProvider.repository, VenueRepositoryProvider.repository)
      }
    }
  }
}
