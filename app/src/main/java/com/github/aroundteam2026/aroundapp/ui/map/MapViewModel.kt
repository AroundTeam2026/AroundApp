// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.util.Log
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
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.quest.nextExpiryAfter
import com.github.aroundteam2026.aroundapp.model.venue.Venue
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
import kotlinx.coroutines.flow.onEach
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
 * @property locationRefused Whether the explorer refused the location permission, so the map says
 *   it is off. False until they answer.
 * @property pins The venues to mark: those on screen with valid quests, and the open one wherever
 *   it is.
 * @property selectedVenueId The venue whose card is open, or null when every pin is closed.
 * @property nearby The valid quests for the list: those within [MapViewModel.NEARBY_RADIUS_METERS]
 *   of the explorer, nearest first, or, while their position is unknown, every valid quest by venue
 *   name, with no distance. Only those that pass [filters] are listed.
 * @property filters The filters the explorer chose for the list. They don't hide any pin.
 * @property showsNearby Whether the nearby quests are open over the map. They and a card are never
 *   open together.
 * @property loadFailed Whether the quests or their venues failed to load, so the map has no pins
 *   even where there are quests. False again once they load.
 * @property retrying Whether what failed is being listened to again after [MapViewModel.onRetry],
 *   until it answers.
 */
data class MapUiState(
    val areaToFrame: GeoBounds?,
    val showsUserLocation: Boolean = false,
    val locationRefused: Boolean = false,
    val pins: List<VenuePin> = emptyList(),
    val selectedVenueId: String? = null,
    val nearby: List<NearbyQuest> = emptyList(),
    val showsNearby: Boolean = false,
    val filters: NearbyFilters = NearbyFilters(),
    val loadFailed: Boolean = false,
    val retrying: Boolean = false,
)

/**
 * Holds the map's state. The map starts on [DEFAULT_MAP_CENTER], then frames [NEARBY_RADIUS_METERS]
 * around the explorer once their position is known.
 *
 * It marks every venue on screen that has valid quests, keeping up as quests and venues change and
 * as rewards expire, by [clock]. At most one pin is open into its card at a time. It also lists the
 * valid quests near the explorer, from the same quests, venues and position as the pins. If the
 * quests or their venues fail to load, it says so until [onRetry] loads them.
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
  /**
   * Whether the explorer picked a venue, which the first framing of their position must not undo.
   */
  private var picked = false

  /** The part of the world on screen, or null until the map reports it. */
  private val visibleArea = MutableStateFlow<GeoBounds?>(null)
  /** Where the explorer was located, which pins measure their distance from; null until then. */
  private val explorer = MutableStateFlow<Location?>(null)
  /** The venue whose card the explorer opened, or null. */
  private val selectedVenueId = MutableStateFlow<String?>(null)
  /** The filters of the nearby list. */
  private val filters = MutableStateFlow(NearbyFilters())
  /** Bumped by [onRetry] when the quests failed: each new value listens to them again. */
  private val questRetries = MutableStateFlow(0)
  /** Bumped by [onRetry] when only the venues failed: each new value listens to them again. */
  private val venueRetries = MutableStateFlow(0)
  /** Whether a retry waits for its answer, which a second tap must not restart. */
  private val retryWaiting = MutableStateFlow(false)
  /**
   * The pins of every venue with valid quests, on screen or not, and the nearby list, built
   * together from each snapshot of [mapData], so the screen never shows one newer than the other.
   */
  private val content =
      mapData()
          .map {
            MapContent(
                pins = buildVenuePins(it.quests, it.venues, it.now, from = it.here),
                nearby = nearbyQuests(it.quests, it.venues, it.now, from = it.here),
                questsFailed = it.questsFailed,
                venuesFailed = it.venuesFailed,
            )
          }
          .stateIn(viewModelScope, SharingStarted.Eagerly, MapContent(emptyList(), emptyList()))

  init {
    viewModelScope.launch {
      // The venues on screen get a pin, and the open one keeps it wherever it is: the card is far
      // bigger than the pin, so it can still be on screen when its venue isn't
      combine(content, visibleArea, selectedVenueId, filters, retryWaiting) {
              (pins, nearby, questsFailed, venuesFailed),
              area,
              opened,
              chosen,
              retrying ->
            val selected = opened?.takeIf { id -> pins.any { it.venueId == id } }
            val shown = pins.filter {
              (area != null && it.location in area) || it.venueId == selected
            }
            val listed = nearby.filter(chosen::matches)
            // One error for both: without quests there are no venues to load either
            val failed = questsFailed || venuesFailed
            ShownMap(shown, opened, selected, listed, chosen, failed, retrying)
          }
          .collect { (shown, opened, selected, nearby, chosen, failed, retrying) ->
            // A venue left without valid quests closes its card for good
            if (selected == null && opened != null) selectedVenueId.compareAndSet(opened, null)
            // One update, so the pins and the list always come from the same snapshot
            _uiState.update {
              it.copy(
                  pins = shown,
                  selectedVenueId = selected,
                  nearby = nearby,
                  filters = chosen,
                  loadFailed = failed,
                  retrying = retrying,
              )
            }
          }
    }
  }

  /**
   * Called with the answer to the location permission request, or with `true` when it was already
   * granted. The explorer is located once per visit to the map: switching back to the Map tab keeps
   * the camera where they left it, unless no position was found yet. Once found, the camera frames
   * it, unless the explorer already picked a venue, which stays in view.
   */
  fun onLocationPermissionResult(granted: Boolean) {
    _uiState.update { it.copy(showsUserLocation = granted, locationRefused = !granted) }
    if (!granted || located || lookup?.isActive == true) return
    lookup = viewModelScope.launch {
      val here = locationRepository.currentLocation() ?: return@launch
      located = true
      explorer.value = here
      // A venue picked meanwhile stays in view: its card would be left off screen
      if (!picked)
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
    if (content.value.pins.none { it.venueId == venueId }) return
    picked = true
    selectedVenueId.value = venueId
    // The card must stay in sight
    _uiState.update { it.copy(showsNearby = false) }
  }

  /** Called when the explorer taps the map itself: closes the open card, not the nearby quests. */
  fun onMapClick() {
    selectedVenueId.value = null
  }

  /** Called when the explorer opens the nearby quests: shows them, closing the open card. */
  fun onNearbyOpen() {
    selectedVenueId.value = null
    _uiState.update { it.copy(showsNearby = true) }
  }

  /** Called when the explorer changes the nearby list's filters; they stay until changed again. */
  fun onFiltersChange(filters: NearbyFilters) {
    this.filters.value = filters
  }

  /**
   * Called when the explorer taps "Try again" after the quests or their venues failed to load:
   * listens again to the quests if they failed, otherwise to the venues. Does nothing while nothing
   * failed or a retry waits for its answer, as each new listener costs Firestore reads.
   */
  fun onRetry() {
    val (_, _, questsFailed, venuesFailed) = content.value
    if (!(questsFailed || venuesFailed) || retryWaiting.value) return
    retryWaiting.value = true
    if (questsFailed) questRetries.update { it + 1 } else venueRetries.update { it + 1 }
  }

  /** Called when the explorer closes the nearby quests. */
  fun onNearbyClose() {
    _uiState.update { it.copy(showsNearby = false) }
  }

  /**
   * Called when the explorer taps the page of [quest] in the nearby quests: opens its venue's card
   * in their place, as a tap on its pin does, and frames [VENUE_FRAME_RADIUS_METERS] around the
   * venue. The venue is placed where its pin is. A page whose venue no longer has valid quests
   * changes nothing.
   */
  fun onNearbyQuestClick(quest: NearbyQuest) {
    val pin = content.value.pins.find { it.venueId == quest.venueId } ?: return
    picked = true
    selectedVenueId.value = pin.venueId
    _uiState.update {
      it.copy(
          areaToFrame = pin.location.boundsWithin(VENUE_FRAME_RADIUS_METERS),
          showsNearby = false,
      )
    }
  }

  /**
   * The active quests, their venues, the explorer's position and the time, again whenever quests or
   * their venues change, when a reward expires, and once the explorer is located. Without quests
   * there are none; without venues there are none either, as each quest is shown at its venue's
   * location, under its name. A failure to load the quests or the venues is logged and reported in
   * [MapData.questsFailed] or [MapData.venuesFailed] until they load, on [onRetry] or, for the
   * venues, for a new set of venues.
   *
   * The quests are listened to once, for both the pins and the nearby list, and their venues again
   * only when the set of venues changes, as each new listener costs Firestore reads.
   */
  // NOSONAR below: Sonar's S1874 takes Flow.catch for deprecated code, but it isn't deprecated
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun mapData(): Flow<MapData> {
    val quests =
        questRetries
            .flatMapLatest {
              questRepository
                  .observeActiveQuests()
                  .map { Loaded(it, failed = false) }
                  .catch { // NOSONAR
                    Log.w(TAG, "Listening to the active quests failed", it)
                    emit(Loaded(emptyList(), failed = true))
                  }
                  // Answered, whether loaded or failed again: the explorer may try again
                  .onEach { retryWaiting.value = false }
            }
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)
    val venues =
        quests
            .map { it.value.mapTo(mutableSetOf()) { quest -> quest.venueId } }
            .distinctUntilChanged()
            .combine(venueRetries) { venueIds, _ -> venueIds }
            .flatMapLatest { venueIds ->
              venueRepository
                  .observeVenues(venueIds)
                  .map { Loaded(it.associateBy { venue -> venue.id }, failed = false) }
                  .catch { // NOSONAR
                    Log.w(TAG, "Listening to venues $venueIds failed", it)
                    emit(Loaded(emptyMap(), failed = true))
                  }
                  .onEach { retryWaiting.value = false }
            }
    return combine(quests, venues, explorer) { current, known, here ->
          Triple(current, known, here)
        }
        .transformLatest { (quests, venues, here) ->
          while (true) {
            val now = clock()
            emit(MapData(quests.value, venues.value, here, now, quests.failed, venues.failed))
            val nextExpiry = quests.value.nextExpiryAfter(now) ?: break
            delay(nextExpiry - now)
          }
        }
  }

  /** What a listener answered: its [value], empty when it [failed]. */
  private data class Loaded<T>(val value: T, val failed: Boolean)

  /**
   * What the map knows at [now]: the active [quests], their [venues] by id, the explorer, and
   * whether the quests [questsFailed] or the venues [venuesFailed] to load.
   */
  private data class MapData(
      val quests: List<Quest>,
      val venues: Map<String, Venue>,
      val here: Location?,
      val now: Long,
      val questsFailed: Boolean,
      val venuesFailed: Boolean,
  )

  /**
   * Every venue's pin and the nearby list, built from the same snapshot of [mapData], and whether
   * the quests or the venues failed to load.
   */
  private data class MapContent(
      val pins: List<VenuePin>,
      val nearby: List<NearbyQuest>,
      val questsFailed: Boolean = false,
      val venuesFailed: Boolean = false,
  )

  /**
   * What the screen shows: the [pins] on screen, the card the explorer [opened], the one still
   * [selected] (null once its venue has no valid quest left), the [nearby] list that passes the
   * [filters], whether the quests or their venues [failed] to load, and whether a retry is
   * [retrying].
   */
  private data class ShownMap(
      val pins: List<VenuePin>,
      val opened: String?,
      val selected: String?,
      val nearby: List<NearbyQuest>,
      val filters: NearbyFilters,
      val failed: Boolean,
      val retrying: Boolean,
  )

  companion object {
    private const val TAG = "MapViewModel"

    /** The map frames this distance around the explorer, and lists the quests within it. */
    const val NEARBY_RADIUS_METERS = 5_000.0

    /** A tap on a nearby quest frames this distance around its venue, so 500 m across. */
    const val VENUE_FRAME_RADIUS_METERS = 250.0

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
