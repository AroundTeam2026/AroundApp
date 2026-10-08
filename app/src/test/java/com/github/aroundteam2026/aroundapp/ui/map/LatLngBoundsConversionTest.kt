// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.model.common.contains
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LatLngBoundsConversionTest {

  @Test
  fun keepsEachEdge() {
    val bounds = GeoBounds(south = 46.0, west = 6.0, north = 47.0, east = 7.0).toLatLngBounds()

    assertEquals(LatLng(46.0, 6.0), bounds.southwest)
    assertEquals(LatLng(47.0, 7.0), bounds.northeast)
  }

  @Test
  fun boundsAcrossTheAntimeridianHoldTheDateLineNotTheRestOfTheWorld() {
    // Fiji's 5 km box spans the date line: swapping west and east would frame the whole planet
    val bounds =
        GeoBounds(south = -17.9, west = 179.95, north = -17.8, east = -179.95).toLatLngBounds()

    assertTrue(bounds.contains(LatLng(-17.85, 179.99)))
    assertTrue(bounds.contains(LatLng(-17.85, -179.99)))
    assertFalse(bounds.contains(LatLng(-17.85, 0.0)))
  }

  @Test
  fun theMapsVisibleRegionKeepsEachEdge() {
    val bounds = LatLngBounds(LatLng(46.50, 6.60), LatLng(46.55, 6.66)).toGeoBounds()

    assertEquals(GeoBounds(south = 46.50, west = 6.60, north = 46.55, east = 6.66), bounds)
  }

  @Test
  fun aVisibleRegionAcrossTheAntimeridianStillHoldsTheDateLine() {
    // The Maps SDK gives a southwest east of the northeast there; GeoBounds reads that as wrapping
    val bounds = LatLngBounds(LatLng(-18.0, 179.0), LatLng(-17.0, -179.0)).toGeoBounds()

    assertTrue(Location(-17.5, 179.5) in bounds)
    assertTrue(Location(-17.5, -179.5) in bounds)
    assertFalse(Location(-17.5, 0.0) in bounds)
  }

  @Test
  fun convertingBackAndForthKeepsTheBounds() {
    val bounds = GeoBounds(south = -18.0, west = 179.0, north = -17.0, east = -179.0)

    assertEquals(bounds, bounds.toLatLngBounds().toGeoBounds())
  }

  @Test
  fun aLocationKeepsItsCoordinates() {
    assertEquals(LatLng(46.5197, 6.6323), Location(46.5197, 6.6323).toLatLng())
  }

  @Test
  fun boundsReachingAPoleKeepEveryLongitude() {
    // LatLng turns longitude 180 into -180, which would leave the box with no width at all
    val bounds = Location(89.99, 10.0).boundsWithin(5_000.0).toLatLngBounds()

    listOf(-180.0, -90.0, 0.0, 10.0, 90.0, 179.99).forEach {
      assertTrue("longitude $it is missing", bounds.contains(LatLng(89.995, it)))
    }
  }
}
