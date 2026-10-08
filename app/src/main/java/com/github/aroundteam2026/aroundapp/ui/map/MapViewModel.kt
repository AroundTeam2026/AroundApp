// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.model.common.contains
import com.github.aroundteam2026.aroundapp.model.location.LocationRepository
import com.github.aroundteam2026.aroundapp.model.location.LocationRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.quest.nextExpiryAfter
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepositoryProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the map shows.
 *
 * @property areaToFrame The area the camera must move to, or null once the map framed it.
 * @property showsUserLocation Whether to draw the explorer's position, which needs the permission.
 * @property pins The venues to mark: those on screen with valid quests, and the open one wherever
 *   it is.
 * @property selectedVenueId The venue whose card is open, or null when every pin is closed.
 */
data class MapUiState(
    val areaToFrame: GeoBounds?,
    val showsUserLocation: Boolean = false,
    val pins: List<VenuePin> = emptyList(),
    val selectedVenueId: String? = null,
)

/**
 * Holds the map's state. The map starts on [DEFAULT_MAP_CENTER], then frames [NEARBY_RADIUS_METERS]
 * around the explorer once their position is known.
 *
 * It marks every venue on screen that has valid quests, keeping up as quests and venues change and
 * as rewards expire, by [clock]. At most one pin is open into its card at a time.
 */
class MapViewModel(
    private val locationRepository: LocationRepository,
    private val questRepository: QuestRepository,
    private val venueRepository: VenueRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
  private val _uiState =
      MutableStateFlow(
          MapUiState(areaToFrame = DEFAULT_MAP_CENTER.boundsWithin(NEARBY_RADIUS_METERS))
      )
  /** What the map shows now; the screen observes it. */
  val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

  private var lookup: Job? = null
  private var located = false

  /** The part of the world on screen, or null until the map reports it. */
  private val visibleArea = MutableStateFlow<GeoBounds?>(null)
  /** Where the explorer was located, which pins measure their distance from; null until then. */
  private val explorer = MutableStateFlow<Location?>(null)
  /** The venue whose card the explorer opened, or null. */
  private val selectedVenueId = MutableStateFlow<String?>(null)
  /** Every venue with valid quests, on screen or not. */
  private val allPins = venuePins().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  init {
    viewModelScope.launch {
      // The venues on screen get a pin, and the open one keeps it wherever it is: the card is far
      // bigger than the pin, so it can still be on screen when its venue isn't
      combine(allPins, visibleArea, selectedVenueId) { pins, area, opened ->
            val selected = opened?.takeIf { id -> pins.any { it.venueId == id } }
            val shown = pins.filter {
              (area != null && it.location in area) || it.venueId == selected
            }
            Triple(shown, opened, selected)
          }
          .collect { (shown, opened, selected) ->
            // A venue left without valid quests closes its card for good
            if (selected == null && opened != null) selectedVenueId.compareAndSet(opened, null)
            _uiState.update { it.copy(pins = shown, selectedVenueId = selected) }
          }
    }
  }

  /**
   * Called with the answer to the location permission request, or with `true` when it was already
   * granted. The explorer is located once per visit to the map: switching back to the Map tab keeps
   * the camera where they left it, unless no position was found yet.
   */
  fun onLocationPermissionResult(granted: Boolean) {
    _uiState.update { it.copy(showsUserLocation = granted) }
    if (!granted || located || lookup?.isActive == true) return
    lookup = viewModelScope.launch {
      val here = locationRepository.currentLocation() ?: return@launch
      located = true
      explorer.value = here
      _uiState.update { it.copy(areaToFrame = here.boundsWithin(NEARBY_RADIUS_METERS)) }
    }
  }

  /**
   * Called once the camera shows [area]. A newer area that arrived while the camera moved is kept,
   * so it still gets framed.
   */
  fun onAreaFramed(area: GeoBounds) {
    _uiState.update { it.copy(areaToFrame = it.areaToFrame.afterFraming(area)) }
  }

  /** Called with the part of the world on screen, each time the camera stops moving. */
  fun onVisibleAreaChanged(area: GeoBounds) {
    visibleArea.value = area
  }

  /**
   * Called when the explorer taps the closed pin of [venueId]: opens its card, closing any other. A
   * tap on a venue that no longer has valid quests changes nothing.
   */
  fun onPinClick(venueId: String) {
    if (allPins.value.none { it.venueId == venueId }) return
    selectedVenueId.value = venueId
  }

  /** Called when the explorer taps the map itself: closes the open card. */
  fun onMapClick() {
    selectedVenueId.value = null
  }

  /**
   * Every venue with valid quests, again whenever quests or their venues change, when a reward
   * expires, and once the explorer is located, which gives each its distance. Without quests, or
   * when they fail, there are none; without venues, pins are drawn from the quests alone.
   *
   * The quests are listened to once, and their venues again only when the set of venues changes, as
   * each new listener costs Firestore reads.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun venuePins(): Flow<List<VenuePin>> {
    val quests =
        questRepository
            .observeActiveQuests()
            .catch { emit(emptyList()) }
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)
    val venues =
        quests
            .map { it.mapTo(mutableSetOf()) { quest -> quest.venueId } }
            .distinctUntilChanged()
            .flatMapLatest { venueIds ->
              venueRepository.observeVenues(venueIds).catch { emit(emptyList()) }
            }
    return combine(quests, venues, explorer) { current, known, here ->
          Triple(current, known.associateBy { it.id }, here)
        }
        .transformLatest { (quests, venues, here) ->
          while (true) {
            val now = clock()
            emit(buildVenuePins(quests, venues, now, from = here))
            val nextExpiry = quests.nextExpiryAfter(now) ?: break
            delay(nextExpiry - now)
          }
        }
  }

  companion object {
    /** The map frames this distance around the explorer. */
    const val NEARBY_RADIUS_METERS = 5_000.0

    /** Builds the [MapViewModel] with the app's [LocationRepository]. */
    val factory: ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val app = this[APPLICATION_KEY]!!
        MapViewModel(
            LocationRepositoryProvider.repository(app),
            QuestRepositoryProvider.repository,
            VenueRepositoryProvider.repository,
        )
      }
    }
  }
}
