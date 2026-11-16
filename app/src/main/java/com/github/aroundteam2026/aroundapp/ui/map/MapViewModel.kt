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
import com.github.aroundteam2026.aroundapp.model.connectivity.ConnectivityRepository
import com.github.aroundteam2026.aroundapp.model.connectivity.ConnectivityRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.location.LocationRepository
import com.github.aroundteam2026.aroundapp.model.location.LocationRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.quest.nextExpiryAfter
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepositoryProvider
import com.github.aroundteam2026.aroundapp.ui.observeOffline
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningReduce
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
 * @property selectedPinKey The [VenuePin.key] of the pin whose card is open, or null when every pin
 *   is closed.
 * @property nearby The valid quests for the list: those within [MapViewModel.NEARBY_RADIUS_METERS]
 *   of the explorer, nearest first, or, while their position is unknown, every valid quest by venue
 *   name, with no distance. Only those that pass [filters] are listed.
 * @property filters The filters the explorer chose for the list. They don't hide any pin.
 * @property showsNearby Whether the nearby quests are open over the map. They and a card are never
 *   open together.
 * @property loadFailed Whether the quests or their venues failed to load, so the map has no pins
 *   even where there are quests. False again once they load, and always false offline, as the map
 *   then shows what Firestore kept on the phone and loads the rest once back online.
 * @property retrying Whether what failed is being listened to again after [MapViewModel.onRetry],
 *   until it answers.
 */
data class MapUiState(
    val areaToFrame: GeoBounds?,
    val showsUserLocation: Boolean = false,
    val locationRefused: Boolean = false,
    val pins: List<VenuePin> = emptyList(),
    val selectedPinKey: String? = null,
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
 *
 * Offline, it keeps what it last loaded and doesn't say that loading failed, as the app's offline
 * banner explains it; back online, it loads again whatever failed, without [onRetry], and says it
 * failed only if that reload fails or doesn't answer within [RELOAD_TIMEOUT_MILLIS].
 */
class MapViewModel(
    private val locationRepository: LocationRepository,
    private val questRepository: QuestRepository,
    private val venueRepository: VenueRepository,
    connectivityRepository: ConnectivityRepository,
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
  /** The [VenuePin.key] of the pin whose card the explorer opened, or null. */
  private val selectedPinKey = MutableStateFlow<String?>(null)
  /** The filters of the nearby list. */
  private val filters = MutableStateFlow(NearbyFilters())
  /** The quests' attempt: each new value listens to them again (see [reload]). */
  private val questAttempts = MutableStateFlow(0)
  /** The venues' attempt: each new value listens to them again (see [reload]). */
  private val venueAttempts = MutableStateFlow(0)
  /**
   * The last reload asked of each listener. It waits until [content] holds that attempt's answer,
   * which arrives with the answer itself, so the screen never sees one without the other.
   */
  private val reloads = MutableStateFlow<Map<Listener, Reload>>(emptyMap())
  /**
   * Ends the wait for the reloads asked when the device came back online; see [reloadWhatFailed].
   */
  private var reloadTimeout: Job? = null
  /**
   * Whether the device is online, assumed until the repository says otherwise. As for the other
   * screens, a disconnect counts once it lasted a moment, and a failure to watch it counts as
   * online. Declared before [content], whose listeners read it as soon as they start.
   */
  private val online =
      connectivityRepository
          .observeOffline(TAG)
          .map { offline -> !offline }
          .stateIn(viewModelScope, SharingStarted.Eagerly, true)
  /**
   * The pins of every venue with valid quests, on screen or not, and the nearby list, built
   * together from each snapshot of [mapData], so the screen never shows one newer than the other.
   */
  private val content =
      mapData()
          .map {
            MapContent(
                pins = buildVenuePins(it.quests.value, it.venues.value, it.now, from = it.here),
                nearby = nearbyQuests(it.quests.value, it.venues.value, it.now, from = it.here),
                questsFailed = it.quests.failed,
                venuesFailed = it.venues.failed,
                questsAttempt = it.quests.attempt,
                venuesAttempt = it.venues.attempt,
            )
          }
          .stateIn(viewModelScope, SharingStarted.Eagerly, MapContent(emptyList(), emptyList()))

  init {
    viewModelScope.launch {
      // The venues on screen get a pin, and the open one keeps it wherever it is: the card is far
      // bigger than the pin, so it can still be on screen when its venue isn't
      combine(content, visibleArea, selectedPinKey, filters, combine(reloads, online, ::Pair)) {
              current,
              area,
              opened,
              chosen,
              (asked, isOnline) ->
            val selected = opened?.takeIf { id -> current.pins.any { it.key == id } }
            val shown =
                current.pins.filter { (area != null && it.location in area) || it.key == selected }
            val listed = current.nearby.filter(chosen::matches)
            val waiting = current.awaiting(asked)
            // One error for both: without quests there are no venues to load either. Offline,
            // "Try again" couldn't help: the offline banner shows instead. Back online, it waits
            // for what was loaded again, which needed no tap
            val failed = current.failed && isOnline && waiting.all { it.tapped }
            ShownMap(shown, opened, selected, listed, chosen, failed, waiting.any { it.tapped })
          }
          .collect { (shown, opened, selected, nearby, chosen, failed, retrying) ->
            // A venue left without valid quests closes its card for good
            if (selected == null && opened != null) selectedPinKey.compareAndSet(opened, null)
            // One update, so the pins and the list always come from the same snapshot
            _uiState.update {
              it.copy(
                  pins = shown,
                  selectedPinKey = selected,
                  nearby = nearby,
                  filters = chosen,
                  loadFailed = failed,
                  retrying = retrying,
              )
            }
          }
    }
    // Back online, whatever failed loads again, so the explorer needn't try again themselves. Not
    // on the first value: a failure while online keeps its "Try again"
    viewModelScope.launch { online.drop(1).collect { if (it) reloadWhatFailed() } }
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
   * Called when the explorer taps the closed pin [key] (see [VenuePin.key]): opens its card,
   * closing any other. A tap on a pin that no longer has valid quests changes nothing.
   */
  fun onPinClick(key: String) {
    if (content.value.pins.none { it.key == key }) return
    picked = true
    selectedPinKey.value = key
    // The card must stay in sight
    _uiState.update { it.copy(showsNearby = false) }
  }

  /** Called when the explorer taps the map itself: closes the open card, not the nearby quests. */
  fun onMapClick() {
    selectedPinKey.value = null
  }

  /** Called when the explorer opens the nearby quests: shows them, closing the open card. */
  fun onNearbyOpen() {
    selectedPinKey.value = null
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
    val current = content.value
    if (!current.failed || current.awaiting(reloads.value).isNotEmpty()) return
    reload(if (current.questsFailed) Listener.QUESTS else Listener.VENUES, tapped = true)
  }

  /**
   * Listens again to every listener that failed, the quests and the venues alike: the venues kept
   * offline wouldn't change, so reloading the quests alone wouldn't reload them. Their failure
   * stays hidden until they answer, or for [RELOAD_TIMEOUT_MILLIS] at most, after which the
   * explorer can try again themselves.
   */
  private fun reloadWhatFailed() {
    val current = content.value
    if (!current.failed) return
    if (current.questsFailed) reload(Listener.QUESTS, tapped = false)
    if (current.venuesFailed) reload(Listener.VENUES, tapped = false)
    reloadTimeout?.cancel()
    reloadTimeout = viewModelScope.launch {
      delay(RELOAD_TIMEOUT_MILLIS)
      reloads.update { asked -> asked.filterValues { it.tapped } }
    }
  }

  /**
   * Listens again to [listener] with a new attempt, which the screen waits for; [tapped] when the
   * explorer asked for it with "Try again".
   */
  private fun reload(listener: Listener, tapped: Boolean) {
    val attempts = if (listener == Listener.QUESTS) questAttempts else venueAttempts
    val attempt = attempts.value + 1
    reloads.update { it + (listener to Reload(attempt, tapped)) }
    attempts.value = attempt
  }

  /** Called when the explorer closes the nearby quests. */
  fun onNearbyClose() {
    _uiState.update { it.copy(showsNearby = false) }
  }

  /**
   * Called when the explorer taps the pin under [quest] in the nearby quests: opens its pin's card
   * in their place, as a tap on its pin does, and frames [VENUE_FRAME_RADIUS_METERS] around the
   * pin: the quest's own, or else its venue's. A page whose venue no longer has valid quests
   * changes nothing.
   */
  fun onShowOnMap(quest: NearbyQuest) {
    val pins = content.value.pins
    val pin =
        pins.find { it.key == quest.questId } ?: pins.find { it.key == quest.venueId } ?: return
    picked = true
    selectedPinKey.value = pin.key
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
   * there are none; without venues there are none either, as each quest is shown under its venue's
   * name. A failure to load the quests or the venues is logged and reported in [Loaded.failed]
   * until they load, on [onRetry] or, for the venues, for a new set of venues. Each answer carries
   * the [Loaded.attempt] of the listener that gave it.
   *
   * The quests are listened to once, for both the pins and the nearby list, and their venues again
   * only when the set of venues changes, as each new listener costs Firestore reads. A failure
   * while offline keeps what was last loaded (see [keptOffline]).
   */
  // NOSONAR below: Sonar's S1874 takes Flow.catch for deprecated code, but it isn't deprecated
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun mapData(): Flow<MapData> {
    val quests =
        questAttempts
            .flatMapLatest { attempt ->
              questRepository
                  .observeActiveQuests()
                  .map { Loaded(it, failed = false, attempt) }
                  .catch { // NOSONAR
                    Log.w(TAG, "Listening to the active quests failed", it)
                    emit(Loaded(emptyList(), failed = true, attempt))
                  }
            }
            .keptOffline()
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)
    val venues =
        quests
            .map { it.value.mapTo(mutableSetOf()) { quest -> quest.venueId } }
            .distinctUntilChanged()
            .combine(venueAttempts, ::Pair)
            .flatMapLatest { (venueIds, attempt) ->
              venueRepository
                  .observeVenues(venueIds)
                  .map { Loaded(it.associateBy { venue -> venue.id }, failed = false, attempt) }
                  .catch { // NOSONAR
                    Log.w(TAG, "Listening to venues $venueIds failed", it)
                    emit(Loaded(emptyMap(), failed = true, attempt))
                  }
            }
            .keptOffline()
    return combine(quests, venues, explorer) { current, known, here ->
          Triple(current, known, here)
        }
        .transformLatest { (quests, venues, here) ->
          while (true) {
            val now = clock()
            emit(MapData(quests, venues, here, now))
            val nextExpiry = quests.value.nextExpiryAfter(now) ?: break
            delay(nextExpiry - now)
          }
        }
  }

  /**
   * Keeps the value last loaded when a listener fails while offline, still marked failed so it
   * loads again once back online; the map then keeps its pins rather than going empty.
   */
  private fun <T> Flow<Loaded<T>>.keptOffline(): Flow<Loaded<T>> = runningReduce { last, next ->
    if (next.failed && !online.value) next.copy(value = last.value) else next
  }

  /** What a listener answered on its [attempt]: its [value], empty when it [failed]. */
  private data class Loaded<T>(val value: T, val failed: Boolean, val attempt: Int)

  /** What the map knows at [now]: the active [quests], their [venues] by id, and the explorer. */
  private data class MapData(
      val quests: Loaded<List<Quest>>,
      val venues: Loaded<Map<String, Venue>>,
      val here: Location?,
      val now: Long,
  )

  /**
   * Every venue's pin and the nearby list, built from the same snapshot of [mapData], whether the
   * quests or the venues failed to load, and the attempt of each that answered.
   */
  private data class MapContent(
      val pins: List<VenuePin>,
      val nearby: List<NearbyQuest>,
      val questsFailed: Boolean = false,
      val venuesFailed: Boolean = false,
      val questsAttempt: Int = 0,
      val venuesAttempt: Int = 0,
  ) {
    /** Whether the quests or their venues failed to load. */
    val failed: Boolean
      get() = questsFailed || venuesFailed

    /** The reloads in [asked] whose answer this content doesn't hold yet. */
    fun awaiting(asked: Map<Listener, Reload>): Collection<Reload> =
        asked
            .filter { (listener, reload) ->
              val answered = if (listener == Listener.QUESTS) questsAttempt else venuesAttempt
              answered < reload.attempt
            }
            .values
  }

  /**
   * What the screen shows: the [pins] on screen, the card the explorer [opened], the one still
   * [selected] (null once its venue has no valid quest left), the [nearby] list that passes the
   * [filters], whether the quests or their venues [failed] to load while online, and whether a
   * retry is [retrying].
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

  /**
   * A reload of a listener: the [attempt] it waits for, and whether the explorer [tapped] "Try
   * again" for it, which shows the error as retrying rather than hiding it.
   */
  private data class Reload(val attempt: Int, val tapped: Boolean)

  /** A listener that a retry, or coming back online, listens to again. */
  private enum class Listener {
    QUESTS,
    VENUES,
  }

  companion object {
    private const val TAG = "MapViewModel"

    /** The map frames this distance around the explorer, and lists the quests within it. */
    const val NEARBY_RADIUS_METERS = 5_000.0

    /** A tap on a nearby quest frames this distance around its venue, so 500 m across. */
    const val VENUE_FRAME_RADIUS_METERS = 250.0

    /**
     * How long a reload after coming back online may go unanswered before the map says loading
     * failed, so a network that never answers still ends on "Try again".
     */
    const val RELOAD_TIMEOUT_MILLIS = 10_000L

    /** Builds the [MapViewModel] with the app's repositories. */
    val factory: ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val app = this[APPLICATION_KEY]!!
        MapViewModel(
            LocationRepositoryProvider.repository(app),
            QuestRepositoryProvider.repository,
            VenueRepositoryProvider.repository,
            ConnectivityRepositoryProvider.repository(app),
        )
      }
    }
  }
}
