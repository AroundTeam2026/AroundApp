// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.address.FakeAddressSearchRepository
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests the venue area screen's "Save location" button, its error and its back arrow. */
@RunWith(AndroidJUnit4::class)
class VenueAreaSaveScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  // A venue without a saved area; writes wait for their gate and give writeResult
  private val stored = FakeVenueRepository(listOf(testVenue(location = null)))
  private var writeGate = CompletableDeferred(Unit)
  private var writeResult: Result<Unit>? = null
  private val venues =
      object : VenueRepository by stored {
        override suspend fun setArea(venueId: String, location: Location, radiusMeters: Int) =
            writeGate.await().let { writeResult ?: stored.setArea(venueId, location, radiusMeters) }
      }
  private val viewModel =
      VenueAreaViewModel(
          "venue",
          venues,
          FakeLocationRepository(null),
          FakeAddressSearchRepository(),
      )
  private val marker = Location(46.5210, 6.6300)
  private var saves = 0
  private var backs = 0

  private val save
    get() = composeTestRule.onNodeWithTag(C.Tag.VENUE_AREA_SAVE)

  private fun show() {
    composeTestRule.setContent {
      VenueAreaScreen("venue", onSaved = { saves++ }, onBack = { backs++ }, viewModel = viewModel)
    }
  }

  @Test
  fun saveNeedsAMarkerShowsProgressThenTellsTheCallerOnce() {
    writeGate = CompletableDeferred()
    show()
    save.assertIsNotEnabled()
    composeTestRule.runOnIdle { viewModel.onMarkerPlaced(marker) }

    save.performClick()
    save.assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(C.Tag.VENUE_AREA_SAVE_PROGRESS, useUnmergedTree = true)
        .assertIsDisplayed()

    writeGate.complete(Unit)
    composeTestRule.waitForIdle()
    assertEquals(1, saves)
  }

  @Test
  fun aFailedSaveShowsAnError() {
    writeResult = Result.failure(IllegalStateException("offline"))
    viewModel.onMarkerPlaced(marker)
    show()
    save.performClick()

    composeTestRule.onNodeWithTag(C.Tag.VENUE_AREA_SAVE_ERROR).assertIsDisplayed()
    assertEquals(0, saves)
  }

  @Test
  fun theBackArrowCallsTheCallersBack() {
    show()
    composeTestRule.onNodeWithTag(C.Tag.VENUE_AREA_BACK).performClick()

    assertEquals(1, backs)
  }

  @Test
  fun itsOwnViewModelOpensTheGivenVenue() {
    // The app's demo venues each have a saved marker, so saving is available at once
    composeTestRule.setContent { VenueAreaScreen("demo-lumen", onSaved = {}, onBack = {}) }

    save.assertIsEnabled()
  }
}
