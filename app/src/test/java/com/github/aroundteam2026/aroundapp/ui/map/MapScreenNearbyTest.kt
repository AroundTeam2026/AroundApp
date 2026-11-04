// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.quest.ScriptedQuestRepository
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.marker.assertDpEquals
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * Tests the map's nearby quests: the pill that opens them, and the overlay that holds the pager.
 */
@RunWith(AndroidJUnit4::class)
class MapScreenNearbyTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val context = ApplicationProvider.getApplicationContext<Context>()

  private val quests =
      MutableStateFlow(
          listOf(
              testQuest(id = "cafe-1", venueId = "cafe"),
              testQuest(id = "bar-1", venueId = "bar"),
          )
      )
  private val viewModel =
      MapViewModel(
          FakeLocationRepository(Location(46.5220, 6.6330)),
          ScriptedQuestRepository.of(quests),
          FakeVenueRepository(
              listOf(
                  testVenue(id = "cafe", name = "Café Lumen"),
                  testVenue(id = "bar", name = "Bar Nocturne"),
              )
          ),
      )

  /** Shows the map; unless [locationOn], the explorer refuses the location permission. */
  private fun show(
      locationOn: Boolean = false,
      onOpenVenue: (venueId: String, venueName: String) -> Unit = { _, _ -> },
  ) {
    if (locationOn) {
      shadowOf(ApplicationProvider.getApplicationContext<Application>())
          .grantPermissions(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
    }
    val dialog =
        PermissionDialog(mapOf(ACCESS_FINE_LOCATION to false, ACCESS_COARSE_LOCATION to false))
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides dialog) {
        MapScreen(viewModel, onOpenVenue = onOpenVenue)
      }
    }
    composeTestRule.waitForIdle()
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  private fun openNearby() {
    node(C.Tag.NEARBY_BUTTON).performClick()
    composeTestRule.waitForIdle()
  }

  private fun close() =
      composeTestRule.onNodeWithContentDescription(context.getString(R.string.map_nearby_close))

  private fun assertOverlayClosed() {
    node(C.Tag.NEARBY_OVERLAY).assertDoesNotExist()
    node(C.Tag.NEARBY_BUTTON).assertIsDisplayed()
  }

  @Test
  fun thePillOpensTheNearbyQuestsInPlaceOfItself() {
    show()
    node(C.Tag.NEARBY_OVERLAY).assertDoesNotExist()

    openNearby()

    node(C.Tag.NEARBY_OVERLAY).assertIsDisplayed()
    node(C.Tag.NEARBY_BUTTON).assertDoesNotExist()
    // The map's nearby quests: without the location, every valid quest, by venue name
    node(C.Tag.NEARBY_COUNTER)
        .assertTextEquals(context.getString(R.string.map_nearby_by_name, 1, 2))
    node(C.Tag.NEARBY_LOCATION_HINT).assertIsDisplayed()
  }

  @Test
  fun withNoQuestsThePillStillOpensTheEmptyState() {
    quests.value = emptyList()
    show()

    openNearby()

    node(C.Tag.NEARBY_EMPTY).assertIsDisplayed()
  }

  @Test
  fun withTheLocationTheQuestsAreClosestFirstWithNoHint() {
    show(locationOn = true)

    openNearby()

    node(C.Tag.NEARBY_COUNTER)
        .assertTextEquals(context.getString(R.string.map_nearby_closest_first, 1, 2))
    node(C.Tag.NEARBY_LOCATION_HINT).assertDoesNotExist()
  }

  @Test
  fun tappingAPageOpensItsVenuesCardInPlaceOfTheOverlay() {
    show()
    openNearby()

    node(C.Tag.NEARBY_PAGE).performClick()
    composeTestRule.waitForIdle()

    assertOverlayClosed()
    // Without the location the quests go by venue name, so the first page is the bar's
    assertEquals("bar", viewModel.uiState.value.selectedVenueId)
  }

  @Test
  fun viewQuestOpensTheShownQuestsVenueAndKeepsTheOverlay() {
    val opened = mutableListOf<Pair<String, String>>()
    show(onOpenVenue = { id, name -> opened += id to name })
    openNearby()

    node(C.Tag.NEARBY_VIEW).performClick()
    composeTestRule.waitForIdle()

    assertEquals(listOf("bar" to "Bar Nocturne"), opened)
    // Back from the venue's page finds the quests where the explorer left them
    node(C.Tag.NEARBY_OVERLAY).assertIsDisplayed()
  }

  @Test
  fun theCloseButtonClosesTheOverlay() {
    show()
    openNearby()

    close().performClick()
    composeTestRule.waitForIdle()

    assertOverlayClosed()
  }

  @Test
  fun backClosesTheOverlayAndStaysOnTheMap() {
    show()
    openNearby()

    composeTestRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    composeTestRule.waitForIdle()

    assertOverlayClosed()
    composeTestRule.activityRule.scenario.onActivity { assertFalse(it.isFinishing) }
  }

  @Test
  fun withTheOverlayClosedBackLeavesTheMap() {
    show()

    composeTestRule.activityRule.scenario.onActivity {
      it.onBackPressedDispatcher.onBackPressed()
      assertTrue(it.isFinishing)
    }
  }

  @Test
  fun aSlowDragPastAThirdOfTheOverlayClosesIt() {
    show()
    openNearby()
    val overlay = node(C.Tag.NEARBY_OVERLAY).getBoundsInRoot()

    // Half its height over a second: far enough, but far slower than a fling
    node(C.Tag.NEARBY_GRABBER).performTouchInput {
      swipeDown(
          endY = centerY + ((overlay.bottom - overlay.top) / 2).toPx(),
          durationMillis = 1_000,
      )
    }
    composeTestRule.waitForIdle()

    assertOverlayClosed()
  }

  @Test
  fun aShortFastFlingClosesTheOverlay() {
    show()
    openNearby()

    // Far short of a third of its height, but let go faster than a fling must be
    node(C.Tag.NEARBY_GRABBER).performTouchInput {
      swipeWithVelocity(
          start = center,
          end = center.copy(y = centerY + 40.dp.toPx()),
          endVelocity = 1_500.dp.toPx(),
      )
    }
    composeTestRule.waitForIdle()

    assertOverlayClosed()
  }

  @Test
  fun aShortDragLeavesTheOverlayOpen() {
    show()
    openNearby()
    val top = node(C.Tag.NEARBY_OVERLAY).getBoundsInRoot().top

    node(C.Tag.NEARBY_GRABBER).performTouchInput {
      swipeDown(endY = centerY + 20.dp.toPx(), durationMillis = 1_000)
    }
    composeTestRule.waitForIdle()

    node(C.Tag.NEARBY_OVERLAY).assertIsDisplayed()
    // And it springs back into place
    assertDpEquals("overlay top", top, node(C.Tag.NEARBY_OVERLAY).getBoundsInRoot().top)
  }

  @Test
  fun theMapHasNoZoomButtonsForThePillToCover() {
    // They sit in the pill's corner; pinching and double-tapping still zoom, as in Figma
    assertFalse(mapUiSettings(showsUserLocation = true).zoomControlsEnabled)
    assertFalse(mapUiSettings(showsUserLocation = false).zoomControlsEnabled)
  }

  @Test
  fun theMyLocationButtonFollowsThePermission() {
    // Without it, the Maps SDK throws a SecurityException
    assertTrue(mapUiSettings(showsUserLocation = true).myLocationButtonEnabled)
    assertFalse(mapUiSettings(showsUserLocation = false).myLocationButtonEnabled)
  }

  @Test
  fun thePillSitsSixteenDpFromTheCornerAndIsFortyEightDpTall() {
    show()

    val screen = composeTestRule.onRoot().getBoundsInRoot()
    val pill = node(C.Tag.NEARBY_BUTTON).getBoundsInRoot()
    assertDpEquals("pill height", 48.dp, pill.bottom - pill.top)
    assertDpEquals("pill to the end", 16.dp, screen.right - pill.right)
    assertDpEquals("pill to the bottom", 16.dp, screen.bottom - pill.bottom)
  }

  @Test
  fun theOverlaySitsSixteenDpFromTheEdgesWithItsGrabberOnTop() {
    show()
    openNearby()

    val screen = composeTestRule.onRoot().getBoundsInRoot()
    val overlay = node(C.Tag.NEARBY_OVERLAY).getBoundsInRoot()
    val grabber = node(C.Tag.NEARBY_GRABBER).getBoundsInRoot()
    assertDpEquals("overlay start", 16.dp, overlay.left)
    assertDpEquals("overlay end", 16.dp, screen.right - overlay.right)
    assertDpEquals("overlay bottom", 16.dp, screen.bottom - overlay.bottom)
    assertDpEquals("grabber width", 40.dp, grabber.right - grabber.left)
    assertDpEquals("grabber height", 4.dp, grabber.bottom - grabber.top)
    assertDpEquals("grabber top", 10.dp, grabber.top - overlay.top)
    assertDpEquals(
        "grabber centred",
        (overlay.left + overlay.right) / 2,
        (grabber.left + grabber.right) / 2,
    )
    assertDpEquals(
        "pager inset",
        18.dp,
        node(C.Tag.NEARBY_COUNTER).getBoundsInRoot().left - overlay.left,
    )
  }
}
