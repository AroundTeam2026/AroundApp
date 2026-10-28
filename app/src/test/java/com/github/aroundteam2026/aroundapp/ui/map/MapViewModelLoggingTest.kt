// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.quest.ScriptedQuestRepository
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

/** Tests what [MapViewModel] logs, which needs Robolectric's [ShadowLog]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MapViewModelLoggingTest {

  private val dispatcher = StandardTestDispatcher()

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  @Test
  fun failingVenuesAreLogged() =
      runTest(dispatcher) {
        // The map is then empty, which alone looks like an area without quests
        val offline = IOException("offline")
        val venues =
            object : VenueRepository by FakeVenueRepository() {
              override fun observeVenues(venueIds: Set<String>) = flow<Nothing> { throw offline }
            }
        val viewModel =
            MapViewModel(
                FakeLocationRepository(null),
                ScriptedQuestRepository.of(MutableStateFlow(listOf(testQuest(venueId = "cafe")))),
                venues,
            )

        viewModel.onVisibleAreaChanged(
            GeoBounds(south = 46.50, west = 6.60, north = 46.55, east = 6.66)
        )
        runCurrent()

        val log = ShadowLog.getLogsForTag("MapViewModel").single()
        assertEquals(Log.WARN, log.type)
        assertEquals(offline, log.throwable)
      }
}
