// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where a signed-in venue starts: the first onboarding step it hasn't done, or its home. */
sealed interface VenueEntryStart {
  /** It has no venue yet, so it starts by giving its business name. */
  data object Initialization : VenueEntryStart

  /** It named the venue [venueId] but hasn't placed it, so it resumes on that venue's area. */
  data class Area(val venueId: String) : VenueEntryStart

  /** Its venue is set up, so it opens on the venue home. */
  data object Home : VenueEntryStart
}

/** What the venue entry shows while it works out where the venue starts. */
sealed interface VenueEntryUiState {
  /** The venue is being read. */
  data object Loading : VenueEntryUiState

  /** The venue couldn't be read; trying again may work. */
  data object Failed : VenueEntryUiState

  /** The venue starts at [start]. */
  data class Ready(val start: VenueEntryStart) : VenueEntryUiState
}

/**
 * Reads the venue of the signed-in user [userId] once, to choose where it starts (see [startFor]).
 * A venue that leaves mid-onboarding resumes where it stopped, since its progress is read back from
 * [venueRepository] each time it signs in.
 *
 * @param userId uid of the signed-in venue user, which is also its venue's id.
 * @param venueRepository where the venue is read from.
 */
class VenueEntryViewModel(
    private val userId: String,
    private val venueRepository: VenueRepository,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow<VenueEntryUiState>(VenueEntryUiState.Loading)
  /** What the entry shows now; the entry observes it. */
  val uiState: StateFlow<VenueEntryUiState> = mutableUiState.asStateFlow()

  init {
    load()
  }

  /** Reads the venue again after a failure; does nothing otherwise. */
  fun onRetry() {
    if (mutableUiState.value != VenueEntryUiState.Failed) return
    mutableUiState.value = VenueEntryUiState.Loading
    load()
  }

  private fun load() {
    viewModelScope.launch {
      mutableUiState.value =
          try {
            VenueEntryUiState.Ready(startFor(venueRepository.getVenue(userId)))
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (e: Exception) {
            Log.w(TAG, "Reading venue $userId failed", e)
            VenueEntryUiState.Failed
          }
    }
  }

  companion object {
    private const val TAG = "VenueEntryViewModel"

    /** Builds the [VenueEntryViewModel] of the venue user [userId], reading [venueRepository]. */
    fun factory(userId: String, venueRepository: VenueRepository): ViewModelProvider.Factory =
        viewModelFactory {
          initializer { VenueEntryViewModel(userId, venueRepository) }
        }
  }
}

/**
 * Where a venue user starts: [VenueEntryStart.Initialization] without a [venue], the area screen
 * while it has no location, and its home once it has one.
 */
internal fun startFor(venue: Venue?): VenueEntryStart =
    when {
      venue == null -> VenueEntryStart.Initialization
      venue.location == null -> VenueEntryStart.Area(venue.id)
      else -> VenueEntryStart.Home
    }
