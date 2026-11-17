// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest

import com.github.aroundteam2026.aroundapp.model.common.Location
import org.junit.Assert.assertEquals
import org.junit.Test

class RadiusMapFramingTest {
  private val start = Location(46.5220, 6.6330)
  private val tapped = Location(46.5230, 6.6340)
  private val picked = Location(46.5300, 6.6400)

  @Test
  fun framesWhereItStarts() {
    assertEquals(start, RadiusMapFraming(start).center)
  }

  @Test
  fun aMoveFromElsewhereFramesThePinAgain() {
    val framing = RadiusMapFraming(start)

    framing.follow(picked)

    assertEquals(picked, framing.center)
  }

  @Test
  fun aMoveOnTheMapKeepsTheCameraWhereItIs() {
    val framing = RadiusMapFraming(start)

    framing.movedOnMap(tapped)
    framing.follow(tapped)

    assertEquals(start, framing.center)
  }

  @Test
  fun aMoveFromElsewhereAfterOneOnTheMapFramesThePinAgain() {
    val framing = RadiusMapFraming(start)
    framing.movedOnMap(tapped)
    framing.follow(tapped)

    framing.follow(picked)

    assertEquals(picked, framing.center)
  }

  @Test
  fun comingBackFromElsewhereToWhereTheMapMovedThePinFramesItAgain() {
    val framing = RadiusMapFraming(start)
    framing.movedOnMap(tapped)
    framing.follow(tapped)
    framing.follow(picked)

    framing.follow(tapped)

    assertEquals(tapped, framing.center)
  }

  @Test
  fun aMoveTheFormChangedFramesThePinWhereTheFormPutIt() {
    val framing = RadiusMapFraming(start)

    framing.movedOnMap(tapped)
    framing.follow(picked)

    assertEquals(picked, framing.center)
  }
}
