// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.address.FakeAddressSearchRepository
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.GatedVenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests the venue area screen's "Save location" button, its error and its back arrow. */
@RunWith(AndroidJUnit4::class)
class VenueAreaSaveScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val entrance = Location(46.5191, 6.6335)
  private val placed = Location(46.5210, 6.6300)
  private val venue = testVenue(id = VENUE_ID, location = null)

  private var venues = GatedVenueRepository(listOf(venue))
  private var saves = 0
  private var backs = 0
  private lateinit var viewModel: VenueAreaViewModel

  private val save
    get() = composeTestRule.onNodeWithTag(C.Tag.VENUE_AREA_SAVE)

  private fun show() {
    viewModel =
        VenueAreaViewModel(
            VENUE_ID,
            venues,
            FakeLocationRepository(null),
            FakeAddressSearchRepository(),
        )
    composeTestRule.setContent {
      VenueAreaScreen(VENUE_ID, onSaved = { saves++ }, onBack = { backs++ }, viewModel = viewModel)
    }
    composeTestRule.waitForIdle()
  }

  private fun placeMarker() {
    composeTestRule.runOnIdle { viewModel.onMarkerPlaced(placed) }
    composeTestRule.waitForIdle()
  }

  private fun stored() = runBlocking { venues.getVenue(VENUE_ID) }

  @Test
  fun itOpensShowingTheSavedRadius() {
    venues = GatedVenueRepository(listOf(venue.copy(location = entrance, radiusMeters = 120)))
    show()

    composeTestRule.onNodeWithTag(C.Tag.VENUE_AREA_RADIUS).assertTextEquals("120 m")
    save.assertIsEnabled()
  }

  @Test
  fun saveIsDisabledUntilAMarkerIsPlaced() {
    show()
    save.assertIsNotEnabled()

    placeMarker()

    save.assertIsEnabled()
    save.assertTextEquals(composeTestRule.activity.getString(R.string.venue_area_save))
  }

  @Test
  fun pressingSaveSavesTheAreaAndTellsTheCallerOnce() {
    show()
    placeMarker()

    save.performClick()
    composeTestRule.waitForIdle()

    assertEquals(placed, stored()?.location)
    assertEquals(1, saves)
    assertEquals(0, backs)
    // Recomposing doesn't tell it again
    composeTestRule.runOnIdle { viewModel.onRadiusChanged(90) }
    composeTestRule.waitForIdle()
    assertEquals(1, saves)
  }

  @Test
  fun whileSavingTheButtonShowsProgressAndCantBePressed() {
    venues.writeGate = CompletableDeferred()
    show()
    placeMarker()

    save.performClick()
    composeTestRule.waitForIdle()

    save.assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(C.Tag.VENUE_AREA_SAVE_PROGRESS, useUnmergedTree = true)
        .assertIsDisplayed()
    save.performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, venues.writes)

    venues.writeGate!!.complete(Unit)
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(C.Tag.VENUE_AREA_SAVE_PROGRESS, useUnmergedTree = true)
        .assertDoesNotExist()
    assertEquals(1, saves)
  }

  @Test
  fun aFailedSaveShowsAnErrorAndKeepsTheEdits() {
    venues.writeFailure = IllegalStateException("offline")
    show()
    composeTestRule.onNodeWithTag(C.Tag.VENUE_AREA_SAVE_ERROR).assertDoesNotExist()
    placeMarker()

    save.performClick()
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(C.Tag.VENUE_AREA_SAVE_ERROR)
        .assertIsDisplayed()
        .assertTextEquals(composeTestRule.activity.getString(R.string.venue_area_save_failed))
    assertEquals(placed, viewModel.uiState.value.marker)
    assertEquals(0, saves)
    // It can be tried again
    save.assertIsEnabled()
  }

  @Test
  fun theBackArrowCallsTheCallersBack() {
    show()

    composeTestRule
        .onNodeWithTag(C.Tag.VENUE_AREA_BACK)
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    composeTestRule
        .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.venue_area_back))
        .performClick()

    assertEquals(1, backs)
    assertEquals(0, saves)
  }

  private companion object {
    const val VENUE_ID = "venue"
  }
}
