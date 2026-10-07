// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits.DEFAULT_RADIUS_METERS
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits.MAX_RADIUS_METERS
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits.MIN_RADIUS_METERS
import com.github.aroundteam2026.aroundapp.resources.C
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.MarkerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class VenueAreaScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val entrance = Location(46.5191, 6.6335)
  private val elsewhere = Location(46.5210, 6.6300)

  private val locations = FakeLocationRepository(Location(47.3769, 8.5417))
  private val viewModel = VenueAreaViewModel(locations)

  // The Maps SDK can't drag a marker in a unit test, so these drive the state a marker exposes
  private var dragging by mutableStateOf(false)
  private var position by mutableStateOf(entrance.toLatLng())
  private val drops = mutableListOf<Location>()

  private fun show() {
    composeTestRule.setContent { VenueAreaScreen(viewModel) }
    composeTestRule.waitForIdle()
  }

  @Test
  fun theSliderIsBoundedByTheLimits() {
    show()

    composeTestRule
        .onNodeWithTag(C.Tag.VENUE_AREA_SLIDER)
        .assertRangeInfoEquals(
            ProgressBarRangeInfo(
                DEFAULT_RADIUS_METERS.toFloat(),
                MIN_RADIUS_METERS.toFloat()..MAX_RADIUS_METERS.toFloat(),
            )
        )
  }

  @Test
  fun movingTheSliderSetsAndShowsTheRadiusInWholeMeters() {
    show()

    composeTestRule.onNodeWithTag(C.Tag.VENUE_AREA_SLIDER).performSemanticsAction(
        SemanticsActions.SetProgress
    ) {
      it(120.6f)
    }

    assertEquals(121, viewModel.uiState.value.radiusMeters)
    composeTestRule.onNodeWithTag(C.Tag.VENUE_AREA_RADIUS).assertTextEquals("121 m")
    // TalkBack reads the radius in meters, not as a percentage of the slider
    composeTestRule
        .onNodeWithTag(C.Tag.VENUE_AREA_SLIDER)
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "121 m"))
  }

  @Test
  fun theSliderIsNamedForTalkBack() {
    show()

    composeTestRule
        .onNodeWithTag(C.Tag.VENUE_AREA_SLIDER)
        .assertContentDescriptionEquals(
            composeTestRule.activity.getString(R.string.venue_area_radius)
        )
  }

  @Test
  fun aTapCountsOnlyOnTheVisibleMap() {
    val visible = LatLngBounds(LatLng(47.375, 8.539), LatLng(47.379, 8.544))

    assertTrue(isOnVisibleMap(LatLng(47.377, 8.541), visible))
    // Where a tap lands while the map still reports taps against its starting view
    assertFalse(isOnVisibleMap(LatLng(49.738, -95.993), visible))
  }

  @Test
  fun aTapBeforeTheMapExistsIsIgnored() {
    assertFalse(isOnVisibleMap(LatLng(47.377, 8.541), visible = null))
  }

  @Test
  fun withoutThePermissionUseMyLocationIsHidden() {
    show()

    composeTestRule.onNodeWithTag(C.Tag.VENUE_AREA_USE_MY_LOCATION).assertDoesNotExist()
    assertEquals(0, locations.calls)
  }

  @Test
  fun withThePermissionUseMyLocationLocatesTheDevice() {
    shadowOf(ApplicationProvider.getApplicationContext<Application>())
        .grantPermissions(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
    show()
    // Opening the screen with the permission already locates the device once
    assertEquals(1, locations.calls)

    composeTestRule.onNodeWithTag(C.Tag.VENUE_AREA_USE_MY_LOCATION).performClick()
    composeTestRule.waitForIdle()

    assertEquals(2, locations.calls)
  }

  @Test
  fun itsOwnViewModelShowsUseMyLocationWithThePermission() {
    shadowOf(ApplicationProvider.getApplicationContext<Application>())
        .grantPermissions(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)

    composeTestRule.setContent { VenueAreaScreen() }

    composeTestRule.onNodeWithTag(C.Tag.VENUE_AREA_USE_MY_LOCATION).assertIsDisplayed()
  }

  @Test
  fun theMarkerMovesToANewLocation() {
    var location by mutableStateOf(entrance)
    lateinit var marker: MarkerState
    composeTestRule.setContent { marker = rememberDraggableMarker(location) {} }
    composeTestRule.waitForIdle()
    assertEquals(entrance.toLatLng(), marker.position)

    location = elsewhere
    composeTestRule.waitForIdle()

    assertEquals(elsewhere.toLatLng(), marker.position)
  }

  /** Applies the state changes, so the flow reading them sees them, and lets it react. */
  private fun TestScope.settle() {
    Snapshot.sendApplyNotifications()
    runCurrent()
  }

  /** Starts a drag, moves the marker to [to], then drops it, as the Maps SDK reports a drag. */
  private fun TestScope.drag(to: Location) {
    dragging = true
    settle()
    position = to.toLatLng()
    settle()
    dragging = false
    settle()
  }

  @Test
  fun aDragReportsOnlyWhereItEnded() = runTest {
    backgroundScope.launch { reportDrops({ dragging }, { position }, drops::add) }
    runCurrent()

    dragging = true
    settle()
    position = elsewhere.toLatLng()
    settle()
    assertEquals(emptyList<Location>(), drops)

    dragging = false
    settle()
    assertEquals(listOf(elsewhere), drops)
  }

  @Test
  fun eachDragIsReported() = runTest {
    backgroundScope.launch { reportDrops({ dragging }, { position }, drops::add) }
    runCurrent()

    drag(to = elsewhere)
    drag(to = entrance)

    assertEquals(listOf(elsewhere, entrance), drops)
  }

  private fun Location.toLatLng() = LatLng(lat, lng)
}
