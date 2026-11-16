// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import com.github.aroundteam2026.aroundapp.model.connectivity.FakeConnectivityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineBannerViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  @Test
  fun bannerTracksOfflineAndOnlineTransitions() =
      runTest(dispatcher) {
        val connectivity = FakeConnectivityRepository()
        val model = OfflineBannerViewModel(connectivity)

        assertFalse(model.showBanner.value)
        connectivity.online.value = false
        advanceUntilIdle()
        assertTrue(model.showBanner.value)
        connectivity.online.value = true
        advanceUntilIdle()
        assertFalse(model.showBanner.value)
      }
}
