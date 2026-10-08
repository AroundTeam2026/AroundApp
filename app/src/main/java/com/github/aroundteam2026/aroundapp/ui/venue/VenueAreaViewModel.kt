// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.model.common.distanceTo
import com.github.aroundteam2026.aroundapp.model.location.LocationRepository
import com.github.aroundteam2026.aroundapp.model.location.LocationRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits
import com.github.aroundteam2026.aroundapp.ui.map.DEFAULT_MAP_CENTER
import com.github.aroundteam2026.aroundapp.ui.map.afterFraming
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the venue area screen shows.
 *
 * @property marker Where the venue placed its marker, or null until it places one.
 * @property radiusMeters Radius of the circle around [marker], always within [VenueLimits].
 * @property areaToFrame The area the camera must move to, or null once the map framed it.
 * @property showsUserLocation Whether to draw the device's position, which needs the permission.
 * @property address The street address the venue last picked from the search, or null until it
 *   picks one. Moving the marker up to [VenueAreaViewModel.ADDRESS_DRIFT_METERS] from it keeps it,
 *   as the marker only refines the entrance; moving it farther forgets it, as it no longer says
 *   where the marker is.
 * @property addressSearch What the address search found.
 */
data class VenueAreaUiState(
    val marker: Location? = null,
    val radiusMeters: Int = VenueLimits.DEFAULT_RADIUS_METERS,
    val areaToFrame: GeoBounds? =
        DEFAULT_MAP_CENTER.boundsWithin(VenueAreaViewModel.FRAMED_RADIUS_METERS),
    val showsUserLocation: Boolean = false,
    val address: String? = null,
    val addressSearch: AddressSearchUiState = AddressSearchUiState(),
)

/**
 * What the address search found. The field's text is [VenueAreaViewModel.addressQuery].
 *
 * @property suggestions The addresses found for the latest search, best first. They stay while the
 *   next search runs, so the list doesn't flicker as the venue types.
 * @property status Whether a search is running, or why there is nothing to show.
 */
data class AddressSearchUiState(
    val suggestions: List<AddressSuggestion> = emptyList(),
    val status: AddressSearchStatus = AddressSearchStatus.IDLE,
)

/** Where the address search stands. */
enum class AddressSearchStatus {
  /** Nothing to search, or the latest search found [AddressSearchUiState.suggestions]. */
  IDLE,
  /** A search is waiting for typing to pause, or running. */
  SEARCHING,
  /** The latest search found nothing. */
  NO_RESULTS,
  /** The latest search failed; trying again may work. */
  FAILED,
  /** This device can't search addresses. */
  UNAVAILABLE,
}

/**
 * Holds the marker a venue places on the map and the radius in which a visit counts. The map starts
 * on [DEFAULT_MAP_CENTER], then frames the device once its position is known, unless a marker is
 * already placed. The venue can also search its address, which places the marker there.
 */
class VenueAreaViewModel(
    private val locationRepository: LocationRepository,
    private val addressSearchRepository: AddressSearchRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(VenueAreaUiState())
  /** What the screen shows now; the screen observes it. */
  val uiState: StateFlow<VenueAreaUiState> = _uiState.asStateFlow()

  private var openingLookup: Job? = null
  private var myLocationLookup: Job? = null
  private var located = false
  private var addressLookup: Job? = null
  private var visibleArea: GeoBounds? = null
  private var addressLocation: Location? = null

  /**
   * What the address field shows. It is Compose state rather than part of [uiState], so the field
   * reads each keystroke at once: a field that waits for a flow to deliver its text can drop
   * characters or move the cursor when the venue types fast.
   */
  var addressQuery by mutableStateOf("")
    private set

  /**
   * Called when the venue taps the map, or drops the dragged marker, at [location]. A marker moved
   * more than [ADDRESS_DRIFT_METERS] from the picked address forgets it, and empties the field if
   * it still shows it.
   */
  fun onMarkerPlaced(location: Location) {
    val drifted = addressLocation?.let { it.distanceTo(location) > ADDRESS_DRIFT_METERS } == true
    if (drifted) {
      addressLocation = null
      // The field mustn't keep showing an address that won't be saved; a new search stays
      if (addressQuery == _uiState.value.address) addressQuery = ""
    }
    _uiState.update {
      if (drifted) it.copy(marker = location, address = null) else it.copy(marker = location)
    }
  }

  /**
   * Called when the venue moves the slider. [radiusMeters] is clamped to
   * [VenueLimits.MIN_RADIUS_METERS]..[VenueLimits.MAX_RADIUS_METERS], never rejected.
   */
  fun onRadiusChanged(radiusMeters: Int) {
    _uiState.update {
      it.copy(
          radiusMeters =
              radiusMeters.coerceIn(VenueLimits.MIN_RADIUS_METERS, VenueLimits.MAX_RADIUS_METERS)
      )
    }
  }

  /**
   * Called with the answer to the location permission request, or with `true` when it was already
   * granted. The device is located once per visit, and framed only while no marker is placed, so
   * the venue's marker stays in view. A refusal or revocation stops any lookup still running, so
   * the camera doesn't move after the device's position was hidden.
   */
  fun onLocationPermissionResult(granted: Boolean) {
    _uiState.update { it.copy(showsUserLocation = granted) }
    if (!granted) {
      openingLookup?.cancel()
      myLocationLookup?.cancel()
      return
    }
    if (located || openingLookup?.isActive == true) return
    openingLookup = frameDevice(overMarker = false)
  }

  /**
   * Frames the device's position, even when a marker is placed. The marker does not move: the
   * position is only accurate to about a city block. Does nothing while a previous call is still
   * locating, or when the position is unknown. It replaces the lookup started when the screen
   * opened, so that one can't move the camera again after this one framed the device.
   */
  fun onUseMyLocation() {
    if (myLocationLookup?.isActive == true) return
    openingLookup?.cancel()
    myLocationLookup = frameDevice(overMarker = true)
  }

  /** Called each time the map stops moving, with the part of the world it shows. */
  fun onVisibleAreaChanged(area: GeoBounds) {
    visibleArea = area
  }

  /**
   * Called as the venue types [query] in the address field. Once the trimmed query has at least
   * [MIN_QUERY_LENGTH] characters, it is searched after [SEARCH_DELAY_MILLIS] without typing,
   * around the area the map shows; each new query replaces the search still pending or running.
   */
  fun onAddressQueryChanged(query: String) {
    addressLookup?.cancel()
    addressQuery = query
    val searches = query.trim().length >= MIN_QUERY_LENGTH
    _uiState.update {
      it.copy(
          addressSearch =
              if (searches) it.addressSearch.copy(status = AddressSearchStatus.SEARCHING)
              else AddressSearchUiState()
      )
    }
    if (searches) addressLookup = searchAddress(query, delayMillis = SEARCH_DELAY_MILLIS)
  }

  /**
   * Called when the venue presses the keyboard's search key: searches the query at once, however
   * short, unless it is blank.
   */
  fun onAddressSearch() {
    val query = addressQuery
    if (query.isBlank()) return
    addressLookup?.cancel()
    _uiState.update {
      it.copy(addressSearch = it.addressSearch.copy(status = AddressSearchStatus.SEARCHING))
    }
    addressLookup = searchAddress(query, delayMillis = 0)
  }

  /**
   * Called when the venue picks [suggestion]: the marker moves there and the map frames it, as the
   * venue's latest choice, so a position lookup still running can't move the camera afterwards. The
   * field shows the address, which is kept to save with the venue.
   */
  fun onAddressPicked(suggestion: AddressSuggestion) {
    addressLookup?.cancel()
    openingLookup?.cancel()
    myLocationLookup?.cancel()
    addressQuery = suggestion.address
    addressLocation = suggestion.location
    _uiState.update {
      it.copy(
          marker = suggestion.location,
          areaToFrame = suggestion.location.boundsWithin(FRAMED_RADIUS_METERS),
          address = suggestion.address,
          addressSearch = AddressSearchUiState(),
      )
    }
  }

  /** Called when the venue clears the field; the marker and the picked address stay. */
  fun onAddressCleared() {
    addressQuery = ""
    onAddressSearchDismissed()
  }

  /**
   * Called when the venue turns to the map, as by tapping it: the suggestions close and a search
   * still pending or running is dropped. The query stays, to search again.
   */
  fun onAddressSearchDismissed() {
    addressLookup?.cancel()
    _uiState.update { it.copy(addressSearch = AddressSearchUiState()) }
  }

  private fun searchAddress(query: String, delayMillis: Long): Job = viewModelScope.launch {
    delay(delayMillis)
    val result = addressSearchRepository.search(query, visibleArea)
    _uiState.update { it.copy(addressSearch = it.addressSearch.showing(result)) }
  }

  /**
   * Called once the camera shows [area]. A newer area that arrived while the camera moved is kept,
   * so it still gets framed.
   */
  fun onAreaFramed(area: GeoBounds) {
    _uiState.update { it.copy(areaToFrame = it.areaToFrame.afterFraming(area)) }
  }

  private fun frameDevice(overMarker: Boolean): Job = viewModelScope.launch {
    val here = locationRepository.currentLocation() ?: return@launch
    located = true
    val area = here.boundsWithin(FRAMED_RADIUS_METERS)
    _uiState.update { if (overMarker || it.marker == null) it.copy(areaToFrame = area) else it }
  }

  companion object {
    /**
     * The map frames this distance around a point: the largest visit radius, close enough to tell
     * an entrance from its neighbours.
     */
    const val FRAMED_RADIUS_METERS = VenueLimits.MAX_RADIUS_METERS.toDouble()

    /** How long typing must pause before the address is searched. */
    const val SEARCH_DELAY_MILLIS = 300L

    /** How many characters, spaces aside, a query needs before it is searched while typing. */
    const val MIN_QUERY_LENGTH = 3

    /**
     * How far the marker can move from the picked address and keep it: the largest visit radius,
     * enough to reach an entrance around the corner.
     */
    const val ADDRESS_DRIFT_METERS = VenueLimits.MAX_RADIUS_METERS.toDouble()

    /** Builds the [VenueAreaViewModel] with the app's location and address repositories. */
    val factory: ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val application = this[APPLICATION_KEY]!!
        VenueAreaViewModel(
            LocationRepositoryProvider.repository(application),
            AddressSearchRepositoryProvider.repository(application),
        )
      }
    }
  }
}

/** This search once [result] arrived: its suggestions, or why there are none. */
private fun AddressSearchUiState.showing(result: AddressSearchResult): AddressSearchUiState =
    when (result) {
      is AddressSearchResult.Found ->
          copy(
              suggestions = result.suggestions,
              status =
                  if (result.suggestions.isEmpty()) AddressSearchStatus.NO_RESULTS
                  else AddressSearchStatus.IDLE,
          )
      AddressSearchResult.Failed ->
          copy(suggestions = emptyList(), status = AddressSearchStatus.FAILED)
      AddressSearchResult.Unavailable ->
          copy(suggestions = emptyList(), status = AddressSearchStatus.UNAVAILABLE)
    }
