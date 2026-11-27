// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.visibility

import androidx.lifecycle.viewmodel.CreationExtras
import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.connectivity.FakeConnectivityRepository
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.presence.ExplorerPresence
import com.github.aroundteam2026.aroundapp.model.presence.PresenceRepository
import com.github.aroundteam2026.aroundapp.ui.OFFLINE_HINT_DELAY_MS
import com.github.aroundteam2026.aroundapp.ui.visibility.VisibilityChoice.ONE_HOUR
import com.github.aroundteam2026.aroundapp.ui.visibility.VisibilityChoice.TONIGHT
import com.github.aroundteam2026.aroundapp.ui.visibility.VisibilityChoice.TWO_HOURS
import java.io.IOException
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VisibilityViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val presence = PresenceStub()
  private val locations = FakeLocationRepository(HERE)
  private val connectivity = FakeConnectivityRepository()

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  /** The card's ViewModel, opened at [at] (15:00 by default) and once it read its visibility. */
  private fun TestScope.open(at: Long = AFTERNOON): VisibilityViewModel {
    val model =
        VisibilityViewModel(
            UID,
            presence,
            locations,
            connectivity,
            clock = { at + testScheduler.currentTime },
            zone = ZONE,
        )
    runCurrent()
    return model
  }

  @Test
  fun itLoadsThenOffersAnHourTwoHoursAndTonight() =
      runTest(dispatcher) {
        val model =
            VisibilityViewModel(
                UID,
                presence,
                locations,
                connectivity,
                clock = { AFTERNOON + testScheduler.currentTime },
                zone = ZONE,
            )
        assertTrue(model.state.value.loading)

        runCurrent()

        val state = model.state.value
        assertFalse(state.loading)
        assertNull(state.visibleUntil)
        assertEquals(listOf(ONE_HOUR, TWO_HOURS, TONIGHT), state.choices)
        assertNull(state.error)
      }

  @Test
  fun anHourSharesTheCurrentLocationUntilAnHourFromNow() =
      runTest(dispatcher) {
        val model = open()

        model.show(ONE_HOUR)
        runCurrent()

        assertEquals(listOf(Shown(UID, HERE, AFTERNOON + HOUR)), presence.shown)
        assertEquals(AFTERNOON + HOUR, model.state.value.visibleUntil)
        assertFalse(model.state.value.busy)
      }

  @Test
  fun twoHoursAndTonightEndAtTheirOwnTimes() =
      runTest(dispatcher) {
        val model = open()

        model.show(TWO_HOURS)
        runCurrent()
        model.show(TONIGHT)
        runCurrent()

        assertEquals(listOf(AFTERNOON + 2 * HOUR, TONIGHT_AT), presence.shown.map { it.until })
        assertEquals(TONIGHT_AT, model.state.value.visibleUntil)
      }

  @Test
  fun tonightIsNotOfferedFromTenPm() =
      runTest(dispatcher) {
        val model = open(at = TONIGHT_AT + 30 * MINUTE)

        model.show(TONIGHT)
        runCurrent()

        assertEquals(listOf(ONE_HOUR, TWO_HOURS), model.state.value.choices)
        assertTrue(presence.shown.isEmpty())
        assertFalse(model.state.value.busy)
      }

  @Test
  fun tonightStopsBeingOfferedAtTenPmWhileTheCardIsOpen() =
      runTest(dispatcher) {
        val model = open(at = TONIGHT_AT - MINUTE)
        assertEquals(listOf(ONE_HOUR, TWO_HOURS, TONIGHT), model.state.value.choices)

        advanceTimeBy(MINUTE + 1)

        assertEquals(listOf(ONE_HOUR, TWO_HOURS), model.state.value.choices)
      }

  @Test
  fun hideMeHidesTheExplorer() =
      runTest(dispatcher) {
        presence.own.value = visibleUntil(AFTERNOON + HOUR)
        val model = open()
        assertEquals(AFTERNOON + HOUR, model.state.value.visibleUntil)

        model.hide()
        runCurrent()

        assertEquals(listOf(UID), presence.hidden)
        assertNull(model.state.value.visibleUntil)
      }

  @Test
  fun theExplorerIsHiddenOnceTheirTimePassesWithoutANewRead() =
      runTest(dispatcher) {
        presence.own.value = visibleUntil(AFTERNOON + MINUTE)
        val model = open()
        assertEquals(AFTERNOON + MINUTE, model.state.value.visibleUntil)

        advanceTimeBy(MINUTE + 1)

        assertNull(model.state.value.visibleUntil)
        assertEquals(listOf(ONE_HOUR, TWO_HOURS, TONIGHT), model.state.value.choices)
      }

  @Test
  fun aDocumentWhoseTimeHasPassedCountsAsHidden() =
      runTest(dispatcher) {
        presence.own.value = visibleUntil(AFTERNOON - 1)

        assertNull(open().state.value.visibleUntil)
      }

  @Test
  fun withoutALocationItSaysSoAndSharesNothing() =
      runTest(dispatcher) {
        locations.location = null
        val model = open()

        model.show(ONE_HOUR)
        runCurrent()

        assertEquals(VisibilityError.NO_LOCATION, model.state.value.error)
        assertTrue(presence.shown.isEmpty())
      }

  @Test
  fun offlineShowingAndHidingAreUnavailable() =
      runTest(dispatcher) {
        connectivity.online.value = false
        presence.own.value = visibleUntil(AFTERNOON + HOUR)
        val model = open()

        model.show(TWO_HOURS)
        model.hide()
        advanceTimeBy(OFFLINE_HINT_DELAY_MS + 1)

        assertTrue(model.state.value.offline)
        assertTrue(presence.shown.isEmpty())
        assertTrue(presence.hidden.isEmpty())
        assertEquals(0, locations.calls)
      }

  @Test
  fun aFailedShowSaysSoAndRetryShowsAgain() =
      runTest(dispatcher) {
        presence.showFailure = IOException("unreachable")
        val model = open()

        model.show(TWO_HOURS)
        runCurrent()
        assertEquals(VisibilityError.SHOW_FAILED, model.state.value.error)
        assertNull(model.state.value.visibleUntil)

        presence.showFailure = null
        model.retry()
        runCurrent()

        assertEquals(2, presence.shown.size)
        assertNull(model.state.value.error)
        assertEquals(AFTERNOON + 2 * HOUR, model.state.value.visibleUntil)
      }

  @Test
  fun aFailedHideSaysSoAndRetryHidesAgain() =
      runTest(dispatcher) {
        presence.own.value = visibleUntil(AFTERNOON + HOUR)
        presence.hideFailure = IOException("unreachable")
        val model = open()

        model.hide()
        runCurrent()
        assertEquals(VisibilityError.HIDE_FAILED, model.state.value.error)
        assertEquals(AFTERNOON + HOUR, model.state.value.visibleUntil)

        presence.hideFailure = null
        model.retry()
        runCurrent()

        assertEquals(listOf(UID, UID), presence.hidden)
        assertNull(model.state.value.error)
        assertNull(model.state.value.visibleUntil)
      }

  @Test
  fun whenItsVisibilityCantBeReadRetryReadsItAgain() =
      runTest(dispatcher) {
        presence.readFailure = IOException("unreachable")
        val model = open()
        assertEquals(VisibilityError.LOAD_FAILED, model.state.value.error)
        assertFalse(model.state.value.loading)

        presence.readFailure = null
        presence.own.value = visibleUntil(AFTERNOON + HOUR)
        model.retry()
        runCurrent()

        assertNull(model.state.value.error)
        assertEquals(AFTERNOON + HOUR, model.state.value.visibleUntil)
      }

  @Test
  fun tapsWhileAnActionIsSentAreIgnored() =
      runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        presence.gate = gate
        val model = open()

        model.show(ONE_HOUR)
        runCurrent()
        assertTrue(model.state.value.busy)
        model.show(TWO_HOURS)
        model.hide()
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf(AFTERNOON + HOUR), presence.shown.map { it.until })
        assertTrue(presence.hidden.isEmpty())
        assertFalse(model.state.value.busy)
      }

  @Test
  fun theFactoryBuildsTheViewModel() {
    val factory = VisibilityViewModel.factory(UID, presence, locations, connectivity)

    val model = factory.create(VisibilityViewModel::class.java, CreationExtras.Empty)

    assertTrue(model.state.value.loading)
  }

  private fun visibleUntil(until: Long) = ExplorerPresence(UID, HERE, until, updatedAt = 0)

  private data class Shown(val uid: String, val location: Location, val until: Long)

  /**
   * Records what is shown and hidden, and makes them fail on demand.
   *
   * ponytail: stands in for #721's FakePresenceRepository; switch to it once #721 is merged.
   */
  private class PresenceStub : PresenceRepository {
    val own = MutableStateFlow<ExplorerPresence?>(null)
    val shown = mutableListOf<Shown>()
    val hidden = mutableListOf<String>()
    var showFailure: Exception? = null
    var hideFailure: Exception? = null
    var readFailure: Exception? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun show(uid: String, location: Location, availableUntil: Long): Result<Unit> {
      gate?.await()
      shown += Shown(uid, location, availableUntil)
      showFailure?.let {
        return Result.failure(it)
      }
      own.value = ExplorerPresence(uid, location, availableUntil, updatedAt = 0)
      return Result.success(Unit)
    }

    override suspend fun hide(uid: String): Result<Unit> {
      hidden += uid
      hideFailure?.let {
        return Result.failure(it)
      }
      own.value = null
      return Result.success(Unit)
    }

    override fun observeOwn(uid: String): Flow<ExplorerPresence?> = flow {
      readFailure?.let { throw it }
      emitAll(own)
    }

    override fun observeNearby(bounds: GeoBounds, now: Long): Flow<List<ExplorerPresence>> =
        emptyFlow()
  }

  private companion object {
    const val UID = "explorer"
    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE
    val HERE = Location(46.5191, 6.5668)
    val ZONE: ZoneId = ZoneId.of("Europe/Zurich")
    val AFTERNOON = ZonedDateTime.of(2026, 11, 27, 15, 0, 0, 0, ZONE).toInstant().toEpochMilli()
    val TONIGHT_AT = ZonedDateTime.of(2026, 11, 27, 22, 0, 0, 0, ZONE).toInstant().toEpochMilli()
  }
}
