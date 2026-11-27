// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.connectivity.FakeConnectivityRepository
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.quest.ScriptedQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeRefusedWritesStore
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.RecordedWrite
import com.github.aroundteam2026.aroundapp.model.reservation.RefusedWrites
import com.github.aroundteam2026.aroundapp.model.reservation.WriteState
import com.github.aroundteam2026.aroundapp.model.reservation.fakeRefusedWrites
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testUsers
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.marker.assertDpEquals
import com.github.aroundteam2026.aroundapp.ui.navigation.LocalShowOfflineBanner
import com.github.aroundteam2026.aroundapp.ui.venuepage.AcceptStatus
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

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
          FakeConnectivityRepository(),
      )

  /** Shows the map; unless [locationOn], the explorer refuses the location permission. */
  private fun show(
      locationOn: Boolean = false,
      showOfflineBanner: Boolean = false,
      onOpenQuest: (venueId: String, questId: String) -> Unit = { _, _ -> },
      acceptViewModel: NearbyAcceptViewModel? = null,
  ) {
    if (locationOn) {
      shadowOf(ApplicationProvider.getApplicationContext<Application>())
          .grantPermissions(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
    }
    val dialog =
        PermissionDialog(mapOf(ACCESS_FINE_LOCATION to false, ACCESS_COARSE_LOCATION to false))
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides dialog) {
        MapScreen(
            viewModel,
            onFocusQuest = { _, _ -> },
            onOpenQuest = onOpenQuest,
            acceptViewModel = acceptViewModel,
            showOfflineBanner = showOfflineBanner,
        )
      }
    }
    composeTestRule.waitForIdle()
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  // The shell leaves the Map's banner to the screen, so the screen must show it by default offline
  @Test
  fun theMapShowsTheBannerWhileTheShellSaysItIsOffline() {
    val dialog =
        PermissionDialog(mapOf(ACCESS_FINE_LOCATION to false, ACCESS_COARSE_LOCATION to false))
    composeTestRule.setContent {
      CompositionLocalProvider(
          LocalActivityResultRegistryOwner provides dialog,
          LocalShowOfflineBanner provides true,
      ) {
        MapScreen(viewModel, onFocusQuest = { _, _ -> }, onOpenQuest = { _, _ -> })
      }
    }
    composeTestRule.waitForIdle()

    node(C.Tag.OFFLINE_BANNER).assertIsDisplayed()
  }

  // As in Figma, the offline banner comes first in the map's top column, above the location banner
  // that shows here since the permission is refused
  @Test
  fun offlineBannerComesFirstAtTheTopOfTheMap() {
    show(showOfflineBanner = true)

    val mapTop = node(C.Tag.MAP).getBoundsInRoot().top
    val banner = node(C.Tag.OFFLINE_BANNER).getBoundsInRoot()
    val locationTop = node(C.Tag.LOCATION_BANNER).getBoundsInRoot().top
    assertTrue(banner.top >= mapTop)
    assertTrue(banner.bottom <= locationTop)
    node(C.Tag.OFFLINE_BANNER).assertIsDisplayed()
  }

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
        .assertTextEquals(nearbyHeader(context, 2, context.getString(R.string.map_nearby_by_name)))
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
        .assertTextEquals(
            nearbyHeader(context, 2, context.getString(R.string.map_nearby_closest_first))
        )
    node(C.Tag.NEARBY_LOCATION_HINT).assertDoesNotExist()
  }

  @Test
  fun thePinOpensItsVenuesCardInPlaceOfTheOverlay() {
    show()
    openNearby()

    // Without the location the quests go by venue name, so the first card is the bar's
    composeTestRule.inQuest("bar-1", C.Tag.NEARBY_SHOW_ON_MAP).performClick()
    composeTestRule.waitForIdle()

    assertOverlayClosed()
    assertEquals("bar", viewModel.uiState.value.selectedPinKey)
  }

  @Test
  fun tappingACardOpensItsQuestsDetailsAndKeepsTheOverlay() {
    val opened = mutableListOf<Pair<String, String>>()
    show(onOpenQuest = { venueId, questId -> opened += venueId to questId })
    openNearby()

    composeTestRule.inQuest("bar-1", C.Tag.NEARBY_CARD).performClick()
    composeTestRule.waitForIdle()

    assertEquals(listOf("bar" to "bar-1"), opened)
    // Back from the details finds the quests where the explorer left them
    node(C.Tag.NEARBY_OVERLAY).assertIsDisplayed()
  }

  @Test
  fun withoutAnAcceptViewModelTheCardsOfferNoAccept() {
    show()
    openNearby()

    node(C.Tag.NEARBY_ACCEPT).assertDoesNotExist()
  }

  @Test
  fun acceptOnACardBooksItsQuestAndSaysItIsAccepted() {
    val reservations = FakeReservationRepository({ 0L })
    val accept =
        NearbyAcceptViewModel(
            reservations,
            testUsers(EXPLORER),
            EXPLORER,
            FakeConnectivityRepository(),
            refusedWrites = fakeRefusedWrites(),
        )
    show(acceptViewModel = accept)
    openNearby()

    composeTestRule.inQuest("bar-1", C.Tag.NEARBY_ACCEPT).performClick()
    composeTestRule.waitUntil { accept.uiState.value.statusOf("bar-1") == AcceptStatus.ACCEPTED }

    composeTestRule
        .inQuest("bar-1", C.Tag.NEARBY_ACCEPT)
        .assertTextEquals(context.getString(R.string.accept_quest_accepted_short))
    // The quests stay open, so the explorer can go on to the next one
    node(C.Tag.NEARBY_OVERLAY).assertIsDisplayed()
  }

  // The write was queued, the app was killed, and the server refused it once it was back online
  @Test
  fun aRefusalFromBeforeARestartIsToldOnceOnTheMap() {
    val remembered =
        FakeRefusedWritesStore().apply { put(RecordedWrite("r1", "bar-1", WriteState.REFUSED)) }
    val accept =
        NearbyAcceptViewModel(
            FakeReservationRepository({ 0L }),
            testUsers(EXPLORER),
            EXPLORER,
            FakeConnectivityRepository(),
            refusedWrites = RefusedWrites(remembered),
        )

    show(acceptViewModel = accept)

    composeTestRule
        .onNodeWithText(context.getString(R.string.accept_quest_refused_notice))
        .assertIsDisplayed()
    // Kept while it shows, and cleared once it is gone, so a recreated map doesn't say it again
    assertTrue(accept.uiState.value.refusalNotice)
    composeTestRule.mainClock.advanceTimeBy(SNACKBAR_LONG_ENOUGH_MS)
    composeTestRule.waitForIdle()
    assertFalse(accept.uiState.value.refusalNotice)
    assertFalse(RefusedWrites(remembered).hasUnreportedRefusal)
  }

  // Rotating the phone or leaving the map cancels the snackbar: the explorer must still be told
  @Test
  fun aRefusalIsNotMarkedToldIfTheMapLeavesBeforeItWasShownThrough() {
    val remembered =
        FakeRefusedWritesStore().apply { put(RecordedWrite("r1", "bar-1", WriteState.REFUSED)) }
    val accept =
        NearbyAcceptViewModel(
            FakeReservationRepository({ 0L }),
            testUsers(EXPLORER),
            EXPLORER,
            FakeConnectivityRepository(),
            refusedWrites = RefusedWrites(remembered),
        )
    val dialog =
        PermissionDialog(mapOf(ACCESS_FINE_LOCATION to false, ACCESS_COARSE_LOCATION to false))
    var onMap by mutableStateOf(true)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides dialog) {
        if (onMap) {
          MapScreen(
              viewModel,
              onFocusQuest = { _, _ -> },
              onOpenQuest = { _, _ -> },
              acceptViewModel = accept,
              showOfflineBanner = false,
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithText(context.getString(R.string.accept_quest_refused_notice))
        .assertIsDisplayed()

    onMap = false
    composeTestRule.waitForIdle()

    assertTrue(accept.uiState.value.refusalNotice)
    assertTrue(RefusedWrites(remembered).hasUnreportedRefusal)
  }

  @Test
  fun withoutARefusalTheMapSaysNothing() {
    val accept =
        NearbyAcceptViewModel(
            FakeReservationRepository({ 0L }),
            testUsers(EXPLORER),
            EXPLORER,
            FakeConnectivityRepository(),
            refusedWrites = fakeRefusedWrites(),
        )

    show(acceptViewModel = accept)

    composeTestRule
        .onNodeWithText(context.getString(R.string.accept_quest_refused_notice))
        .assertDoesNotExist()
  }

  @Test
  fun theCloseButtonClosesTheOverlay() {
    show()
    openNearby()

    close().performClick()
    composeTestRule.waitForIdle()

    assertOverlayClosed()
  }

  /** Gives the map enough quests for the pulled-up list to fill the sheet up to its stop. */
  private fun manyQuests() {
    quests.value = List(MANY_QUESTS) { testQuest(id = "cafe-$it", venueId = "cafe") }
  }

  /** Drags the open overlay's grabber up slowly, well past where it stops, and lets go. */
  private fun pullUp() {
    node(C.Tag.NEARBY_GRABBER).performTouchInput {
      swipe(center, center - Offset(0f, 600.dp.toPx()), durationMillis = 1_000L)
    }
    composeTestRule.waitForIdle()
  }

  // Offline with the location refused, the two banners reach past Figma's stop: the pulled-up sheet
  // stops under them instead, so its header's close button is still visible and takes the tap
  @Test
  @Config(qualifiers = TALL_SCREEN)
  fun withTwoBannersThePulledUpSheetStopsUnderThemAndItsCloseButtonWorks() {
    manyQuests()
    show(showOfflineBanner = true)
    openNearby()

    pullUp()

    val bannersBottom = node(C.Tag.LOCATION_BANNER).getBoundsInRoot().bottom
    val sheetTop = node(C.Tag.NEARBY_OVERLAY).getBoundsInRoot().top
    // Under the banners, where Figma's stop alone would have put it over them
    assertTrue("sheet at $sheetTop, banners end at $bannersBottom", sheetTop >= bannersBottom)
    assertTrue(bannersBottom > node(C.Tag.MAP).getBoundsInRoot().top + PULLED_UP_TOP)
    node(C.Tag.NEARBY_SCRIM).assertIsDisplayed()
    close().assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertOverlayClosed()
  }

  @Test
  @Config(qualifiers = TALL_SCREEN)
  fun withNoBannerThePulledUpSheetStillStopsAtFigmasStop() {
    manyQuests()
    show(locationOn = true)
    openNearby()
    node(C.Tag.LOCATION_BANNER).assertDoesNotExist()

    pullUp()

    val mapTop = node(C.Tag.MAP).getBoundsInRoot().top
    assertDpEquals("top", mapTop + PULLED_UP_TOP, node(C.Tag.NEARBY_OVERLAY).getBoundsInRoot().top)
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
  fun backTakesAPulledUpOverlayBackToRestBeforeClosingIt() {
    show()
    openNearby()
    assertBackLowersThePulledUpOverlayThenClosesIt()
  }

  // As when coming back from a quest's details, or after a rotation: the map and the overlay enter
  // the composition together, and the overlay's Back must still be asked before the map's
  @Test
  fun backTakesAPulledUpOverlayBackToRestWhenTheMapOpensWithItOpen() {
    viewModel.onNearbyOpen()
    show()
    assertBackLowersThePulledUpOverlayThenClosesIt()
  }

  /**
   * Pulls the open overlay up, then checks a first Back takes it to rest and a second closes it.
   */
  private fun assertBackLowersThePulledUpOverlayThenClosesIt() {
    val restTop = node(C.Tag.NEARBY_OVERLAY).getBoundsInRoot().top
    node(C.Tag.NEARBY_GRABBER).performTouchInput {
      swipe(center, center - Offset(0f, 600.dp.toPx()), durationMillis = 1_000L)
    }
    composeTestRule.waitForIdle()

    composeTestRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    composeTestRule.waitForIdle()
    assertDpEquals("overlay top", restTop, node(C.Tag.NEARBY_OVERLAY).getBoundsInRoot().top)
    node(C.Tag.NEARBY_SCRIM).assertDoesNotExist()

    composeTestRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    composeTestRule.waitForIdle()
    assertOverlayClosed()
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
  fun aSlowDragDownOnTheListAtItsTopClosesTheOverlay() {
    show()
    openNearby()
    val overlay = node(C.Tag.NEARBY_OVERLAY).getBoundsInRoot()

    // The list has nothing above to scroll to, so the overlay takes the drag as from the grabber
    node(C.Tag.NEARBY_LIST).performTouchInput {
      swipeDown(
          startY = top + 10.dp.toPx(),
          endY = top + 10.dp.toPx() + ((overlay.bottom - overlay.top) / 2).toPx(),
          durationMillis = 1_000,
      )
    }
    composeTestRule.waitForIdle()

    assertOverlayClosed()
  }

  @Test
  fun aShortFastFlingDownOnTheListClosesTheOverlay() {
    show()
    openNearby()

    node(C.Tag.NEARBY_LIST).performTouchInput {
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
  fun aShortDragDownOnTheListLeavesTheOverlayOpen() {
    show()
    openNearby()
    val top = node(C.Tag.NEARBY_OVERLAY).getBoundsInRoot().top

    node(C.Tag.NEARBY_LIST).performTouchInput {
      swipeDown(startY = centerY, endY = centerY + 20.dp.toPx(), durationMillis = 1_000)
    }
    composeTestRule.waitForIdle()

    node(C.Tag.NEARBY_OVERLAY).assertIsDisplayed()
    assertDpEquals("overlay top", top, node(C.Tag.NEARBY_OVERLAY).getBoundsInRoot().top)
  }

  @Test
  fun aDragDownOnAScrolledListScrollsItBackBeforeMovingTheOverlay() {
    // Enough quests that the list has far more than the drag to scroll back
    quests.value = (1..5).map { testQuest(id = "cafe-$it", venueId = "cafe") }
    show()
    openNearby()
    val top = node(C.Tag.NEARBY_OVERLAY).getBoundsInRoot().top
    node(C.Tag.NEARBY_LIST).performScrollToIndex(4)

    // Checked with the finger still down, before a short drag would spring back
    node(C.Tag.NEARBY_LIST).performTouchInput {
      down(center)
      moveBy(Offset(0f, 30.dp.toPx()))
    }
    composeTestRule.waitForIdle()

    assertDpEquals("overlay top", top, node(C.Tag.NEARBY_OVERLAY).getBoundsInRoot().top)
    node(C.Tag.NEARBY_LIST).performTouchInput { up() }
  }

  @Test
  fun aDragBackUpOnTheListReturnsTheOverlayFirst() {
    show()
    openNearby()
    val top = node(C.Tag.NEARBY_OVERLAY).getBoundsInRoot().top

    node(C.Tag.NEARBY_LIST).performTouchInput {
      down(center)
      moveBy(Offset(0f, 60.dp.toPx()))
      moveBy(Offset(0f, -60.dp.toPx()))
    }
    composeTestRule.waitForIdle()

    // Back at its place, or above it once the finger passes where it started; the list never
    // scrolled
    val overlayTop = node(C.Tag.NEARBY_OVERLAY).getBoundsInRoot().top
    val listScrolled = node(C.Tag.NEARBY_LIST).scrolled()
    node(C.Tag.NEARBY_LIST).performTouchInput { up() }
    assertTrue("the overlay is at $overlayTop, under its place $top", overlayTop <= top)
    assertEquals(0f, listScrolled)
  }

  @Test
  fun aFlingThatScrollsTheListBackToItsTopLeavesTheOverlayOpen() {
    quests.value = (1..5).map { testQuest(id = "cafe-$it", venueId = "cafe") }
    show()
    openNearby()
    val overlayTop = node(C.Tag.NEARBY_OVERLAY).getBoundsInRoot().top
    node(C.Tag.NEARBY_LIST).performScrollToIndex(4)

    // The finger stops short of the top and the fling carries the list past it, but only a
    // finger drags the overlay
    node(C.Tag.NEARBY_LIST).performTouchInput {
      swipeWithVelocity(
          start = center.copy(y = top + 10.dp.toPx()),
          end = center.copy(y = top + 130.dp.toPx()),
          endVelocity = 2_500.dp.toPx(),
      )
    }
    composeTestRule.waitForIdle()

    node(C.Tag.NEARBY_OVERLAY).assertIsDisplayed()
    assertDpEquals("overlay top", overlayTop, node(C.Tag.NEARBY_OVERLAY).getBoundsInRoot().top)
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

  private companion object {
    const val EXPLORER = "explorer"

    /** Longer than a snackbar stays up. */
    const val SNACKBAR_LONG_ENOUGH_MS = 10_000L

    /** A phone-sized screen, tall enough for the sheet to grow under two banners. */
    const val TALL_SCREEN = "w411dp-h891dp"

    /** More quests than the pulled-up sheet shows at once. */
    const val MANY_QUESTS = 10
  }
}
