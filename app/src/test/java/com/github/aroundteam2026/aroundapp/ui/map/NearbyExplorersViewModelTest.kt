// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.presence.ExplorerPresence
import com.github.aroundteam2026.aroundapp.model.presence.PresenceRepository
import com.github.aroundteam2026.aroundapp.model.user.FakeUserRepository
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.User
import com.github.aroundteam2026.aroundapp.ui.map.MapViewModel.Companion.NEARBY_RADIUS_METERS
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Tests how [NearbyExplorersViewModel] finds the explorers around the user and keeps up. */
@OptIn(ExperimentalCoroutinesApi::class)
class NearbyExplorersViewModelTest {

  private val dispatcher = StandardTestDispatcher()

  /** The user, at the Place de la Palud in Lausanne. */
  private val here = Location(46.5210, 6.6330)
  private val minute = 60_000L

  private val location = FakeLocationRepository(here)
  private val presences = ScriptedPresenceRepository()
  private val users = FakeUserRepository()

  private val viewModel by lazy {
    NearbyExplorersViewModel(
        explorerUid = "me",
        locationRepository = location,
        presenceRepository = presences,
        userRepository = users,
        clock = { START + dispatcher.scheduler.currentTime },
    )
  }

  private val state
    get() = viewModel.uiState.value

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  /** Someone [northMeters] north of the user, visible for [minutes] more. */
  private fun presence(uid: String, northMeters: Double, minutes: Long = 60) =
      ExplorerPresence(
          uid,
          Location(here.lat + northMeters / 111_195.0, here.lng),
          availableUntil = START + minutes * minute,
          updatedAt = START,
      )

  private suspend fun explorer(uid: String, name: String = uid) {
    users.createUser(User(uid, name, Role.EXPLORER, createdAt = 0L))
  }

  /** Answers the location permission and lets the view model look the user up. */
  private fun TestScope.located(granted: Boolean = true) {
    viewModel.onLocationPermissionResult(granted)
    runCurrent()
  }

  @Test
  fun isLocatingUntilThePermissionIsAnswered() = test {
    runCurrent()

    assertEquals(NearbyExplorersUiState(ExplorersStatus.LOCATING), state)
  }

  @Test
  fun aRefusedPermissionSaysThereIsNoLocationAndListsNobody() = test {
    located(granted = false)

    assertEquals(NearbyExplorersUiState(ExplorersStatus.NO_LOCATION), state)
    assertEquals(emptyList<GeoBounds>(), presences.asked)
  }

  @Test
  fun noFixSaysThereIsNoLocation() = test {
    location.location = null

    located()

    assertEquals(ExplorersStatus.NO_LOCATION, state.status)
  }

  @Test
  fun isLoadingUntilTheExplorersAnswer() = test {
    located()

    assertEquals(NearbyExplorersUiState(ExplorersStatus.LOADING), state)
  }

  @Test
  fun listsTheOtherExplorersAroundTheUserNearestFirst() = test {
    explorer("me")
    explorer("lea", "Lea")
    explorer("noe", "Noé")
    presences.nearby.value =
        listOf(presence("lea", 800.0), presence("me", 0.0), presence("noe", 200.0))

    located()

    assertEquals(ExplorersStatus.LOADED, state.status)
    assertEquals(listOf("Noé", "Lea"), state.explorers.map { it.name })
    assertEquals(listOf(here.boundsWithin(NEARBY_RADIUS_METERS)), presences.asked)
  }

  @Test
  fun noExplorerAroundIsLoadedAndEmpty() = test {
    presences.nearby.value = emptyList()

    located()

    assertEquals(NearbyExplorersUiState(ExplorersStatus.LOADED), state)
  }

  @Test
  fun anExplorerLeavesTheListWhenTheirTimeRunsOut() = test {
    explorer("soon")
    explorer("later")
    presences.nearby.value =
        listOf(presence("soon", 100.0, minutes = 10), presence("later", 200.0, minutes = 30))
    located()

    advanceTimeBy(10 * minute - 1)
    runCurrent()
    assertEquals(listOf("soon", "later"), state.explorers.map { it.uid })

    advanceTimeBy(1)
    runCurrent()
    assertEquals(listOf("later"), state.explorers.map { it.uid })

    advanceTimeBy(20 * minute)
    runCurrent()
    assertEquals(NearbyExplorersUiState(ExplorersStatus.LOADED), state)
  }

  @Test
  fun keepsUpWithExplorersShowingThemselvesAndRenaming() = test {
    explorer("lea", "Lea")
    explorer("noe", "Noé")
    presences.nearby.value = listOf(presence("lea", 300.0))
    located()

    presences.nearby.value = listOf(presence("lea", 300.0), presence("noe", 100.0))
    users.updateProfile("lea", "Léa", pictureUrl = "pics/lea")
    runCurrent()

    assertEquals(listOf("Noé", "Léa"), state.explorers.map { it.name })
    assertEquals("pics/lea", state.explorers.last().pictureUrl)
  }

  @Test
  fun aFailureSaysSoAndRetryListensAgain() = test {
    explorer("lea")
    presences.failing = true
    presences.nearby.value = listOf(presence("lea", 300.0))
    located()
    assertEquals(NearbyExplorersUiState(ExplorersStatus.FAILED), state)

    presences.failing = false
    viewModel.onRetry()
    runCurrent()

    assertEquals(listOf("lea"), state.explorers.map { it.uid })
    assertEquals(2, presences.asked.size)
  }

  @Test
  fun retryWithoutALocationLooksForItAgain() = test {
    location.location = null
    located()
    location.location = here
    presences.nearby.value = emptyList()

    viewModel.onRetry()
    runCurrent()

    assertEquals(ExplorersStatus.LOADED, state.status)
    assertEquals(2, location.calls)
  }

  @Test
  fun theUserIsLocatedOnlyOnce() = test {
    presences.nearby.value = emptyList()
    located()

    located()

    assertEquals(1, location.calls)
    assertEquals(1, presences.asked.size)
  }

  @Test
  fun refusingThePermissionWhileLocatingStopsTheLookup() = test {
    location.gate = CompletableDeferred()
    located()
    assertEquals(ExplorersStatus.LOCATING, state.status)

    located(granted = false)
    location.gate!!.complete(Unit)
    runCurrent()

    assertEquals(ExplorersStatus.NO_LOCATION, state.status)
    assertEquals(emptyList<GeoBounds>(), presences.asked)
  }

  /**
   * Lists [nearby] wherever it is asked, once it holds a list, and records the areas asked. While
   * [failing], a new listener fails instead.
   */
  private class ScriptedPresenceRepository : PresenceRepository {
    val nearby = MutableStateFlow<List<ExplorerPresence>?>(null)
    var failing = false
    val asked = mutableListOf<GeoBounds>()

    override fun observeNearby(bounds: GeoBounds, now: Long): Flow<List<ExplorerPresence>> {
      asked += bounds
      return if (failing) flow { throw IOException("offline") } else nearby.filterNotNull()
    }

    override suspend fun show(uid: String, location: Location, availableUntil: Long) =
        error("Not used by the view model")

    override suspend fun hide(uid: String) = error("Not used by the view model")

    override fun observeOwn(uid: String): Flow<ExplorerPresence?> =
        error("Not used by the view model")
  }

  private companion object {
    /** When each test starts, in epoch milliseconds. */
    const val START = 1_764_250_000_000L
  }
}
