// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.connectivity.FakeConnectivityRepository
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.ScriptedQuestRepository
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.resources.C
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Tests the banner that says the map is offline, showing the quests saved on the phone. */
@RunWith(AndroidJUnit4::class)
class MapScreenOfflineTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val app = ApplicationProvider.getApplicationContext<Application>()
  private val cafe =
      testVenue(id = "cafe", name = "Café Lumen", location = Location(46.5220, 6.6330))
  private val quests =
      MutableStateFlow<List<Quest>>(listOf(testQuest(id = "cafe-1", venueId = "cafe")))
  private val venueRepository = FlakyVenueRepository(listOf(cafe))
  private val connectivity = FakeConnectivityRepository(online = false)
  private val viewModel by lazy {
    MapViewModel(
        FakeLocationRepository(cafe.location),
        ScriptedQuestRepository.of(quests),
        venueRepository,
        connectivity,
    )
  }

  /** Shows the map; the location permission dialog, if asked for, answers with [answer]. */
  private fun show(answer: Map<String, Boolean>? = null) {
    val dialog = PermissionDialog(answer)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides dialog) {
        MapScreen(viewModel, onOpenVenue = { _, _ -> }, onOpenQuest = { _, _ -> })
      }
    }
    // The map says it's offline once the device has been for a moment
    shadowOf(Looper.getMainLooper())
        .idleFor(Duration.ofMillis(MapViewModel.OFFLINE_BANNER_DELAY_MILLIS))
    composeTestRule.waitForIdle()
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  @Test
  fun offlineShowsTheBannerWithTheMapsCaption() {
    show()

    node(C.Tag.OFFLINE_HINT).assertIsDisplayed()
    composeTestRule.onNodeWithText(app.getString(R.string.offline_hint_title)).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(app.getString(R.string.offline_map_saved_quests))
        .assertIsDisplayed()
  }

  @Test
  fun offlineFailingVenuesShowNoTryAgain() {
    venueRepository.failing = true
    show()

    node(C.Tag.OFFLINE_HINT).assertIsDisplayed()
    node(C.Tag.VENUES_ERROR_BANNER).assertDoesNotExist()
    node(C.Tag.VENUES_TRY_AGAIN).assertDoesNotExist()
  }

  @Test
  fun theLocationBannerShowsAlongsideTheOfflineOne() {
    show(answer = mapOf(ACCESS_FINE_LOCATION to false, ACCESS_COARSE_LOCATION to false))

    node(C.Tag.OFFLINE_HINT).assertIsDisplayed()
    node(C.Tag.LOCATION_BANNER).assertIsDisplayed()
  }

  @Test
  fun backOnlineHidesTheBanner() {
    show()

    connectivity.online.value = true
    composeTestRule.waitForIdle()

    node(C.Tag.OFFLINE_HINT).assertDoesNotExist()
  }
}
