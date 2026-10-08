// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits.MAX_RADIUS_METERS
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits.MIN_RADIUS_METERS
import com.github.aroundteam2026.aroundapp.ui.map.DEFAULT_MAP_CENTER
import com.github.aroundteam2026.aroundapp.ui.venue.VenueAreaViewModel.Companion.FRAMED_RADIUS_METERS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VenueAreaViewModelTest {

  private val entrance = Location(46.5191, 6.6335)
  private val elsewhere = Location(46.5210, 6.6300)
  private val lausanne = DEFAULT_MAP_CENTER.boundsWithin(FRAMED_RADIUS_METERS)

  private val viewModel = VenueAreaViewModel()

  private val state
    get() = viewModel.uiState.value

  @Test
  fun placingTheMarkerSetsItThenMovesIt() {
    viewModel.onMarkerPlaced(entrance)
    assertEquals(entrance, state.marker)

    viewModel.onMarkerPlaced(elsewhere)
    assertEquals(elsewhere, state.marker)
  }

  @Test
  fun aRadiusWithinTheLimitsIsKept() {
    viewModel.onRadiusChanged(120)
    assertEquals(120, state.radiusMeters)

    // The limits themselves are allowed
    viewModel.onRadiusChanged(MIN_RADIUS_METERS)
    assertEquals(MIN_RADIUS_METERS, state.radiusMeters)
    viewModel.onRadiusChanged(MAX_RADIUS_METERS)
    assertEquals(MAX_RADIUS_METERS, state.radiusMeters)
  }

  @Test
  fun aRadiusBelowTheMinimumIsClampedToIt() {
    viewModel.onRadiusChanged(MIN_RADIUS_METERS - 1)

    assertEquals(MIN_RADIUS_METERS, state.radiusMeters)
  }

  @Test
  fun aRadiusAboveTheMaximumIsClampedToIt() {
    viewModel.onRadiusChanged(MAX_RADIUS_METERS + 1)

    assertEquals(MAX_RADIUS_METERS, state.radiusMeters)
  }

  @Test
  fun changingTheRadiusKeepsTheMarker() {
    viewModel.onMarkerPlaced(entrance)

    viewModel.onRadiusChanged(120)

    assertEquals(entrance, state.marker)
  }

  @Test
  fun framingTheAreaClearsIt() {
    viewModel.onAreaFramed(lausanne)

    assertNull(state.areaToFrame)
  }

  @Test
  fun framingADifferentAreaKeepsTheOneStillToFrame() {
    viewModel.onAreaFramed(entrance.boundsWithin(FRAMED_RADIUS_METERS))

    assertEquals(lausanne, state.areaToFrame)
  }
}
