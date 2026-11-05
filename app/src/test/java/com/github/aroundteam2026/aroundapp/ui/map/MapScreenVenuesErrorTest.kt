// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.ScriptedQuestRepository
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.resources.C
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests the banner that says the venues failed to load, and its "Try again" button. */
@RunWith(AndroidJUnit4::class)
class MapScreenVenuesErrorTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val app = ApplicationProvider.getApplicationContext<Application>()
  private val cafe =
      testVenue(id = "cafe", name = "Café Lumen", location = Location(46.5220, 6.6330))
  private val quests =
      MutableStateFlow<List<Quest>>(listOf(testQuest(id = "cafe-1", venueId = "cafe")))
  private val venueRepository = FlakyVenueRepository(listOf(cafe))
  private val viewModel by lazy {
    MapViewModel(
        FakeLocationRepository(Location(46.5220, 6.6330)),
        ScriptedQuestRepository.of(quests),
        venueRepository,
    )
  }

  /** Shows the map; the location permission dialog, if asked for, answers with [answer]. */
  private fun show(answer: Map<String, Boolean>? = null) {
    val dialog = PermissionDialog(answer)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides dialog) {
        MapScreen(viewModel, onOpenVenue = { _, _ -> })
      }
    }
    composeTestRule.waitForIdle()
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  @Test
  fun failingVenuesShowTheBanner() {
    venueRepository.failing = true
    show()

    node(C.Tag.VENUES_ERROR_BANNER).assertIsDisplayed()
    composeTestRule.onNodeWithText(app.getString(R.string.map_venues_failed)).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(app.getString(R.string.map_venues_failed_detail))
        .assertIsDisplayed()
  }

  @Test
  fun tryAgainLoadsTheVenuesAndHidesTheBanner() {
    venueRepository.failing = true
    show()

    venueRepository.failing = false
    node(C.Tag.VENUES_TRY_AGAIN).performClick()
    composeTestRule.waitForIdle()

    node(C.Tag.VENUES_ERROR_BANNER).assertDoesNotExist()
    assertFalse(viewModel.uiState.value.venuesFailed)
  }

  @Test
  fun loadedVenuesShowNoBanner() {
    show()

    node(C.Tag.VENUES_ERROR_BANNER).assertDoesNotExist()
  }

  @Test
  fun anAreaWithoutQuestsShowsNoBanner() {
    quests.value = emptyList()
    show()

    node(C.Tag.VENUES_ERROR_BANNER).assertDoesNotExist()
  }

  @Test
  fun bothBannersShowWhenTheVenuesFailAndTheLocationIsOff() {
    venueRepository.failing = true
    show(mapOf(ACCESS_FINE_LOCATION to false, ACCESS_COARSE_LOCATION to false))

    node(C.Tag.VENUES_ERROR_BANNER).assertIsDisplayed()
    node(C.Tag.LOCATION_BANNER).assertIsDisplayed()
  }
}
