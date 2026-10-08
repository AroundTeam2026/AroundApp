// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import com.github.aroundteam2026.aroundapp.ui.map.marker.DistanceUnit.KILOMETERS
import com.github.aroundteam2026.aroundapp.ui.map.marker.DistanceUnit.METERS
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests [formatDistance], the card's distance chip. */
class DistanceFormatTest {

  private fun format(meters: Double) = formatDistance(meters, Locale.US)

  @Test
  fun aFewStepsAwayIsInWholeMetres() {
    assertEquals(DistanceText("0", METERS), format(0.0))
    assertEquals(DistanceText("45", METERS), format(45.4))
    assertEquals(DistanceText("99", METERS), format(99.4))
  }

  @Test
  fun hundredsOfMetresAreRoundedToTens() {
    // Positions are only accurate to about a city block, so 347 m would be false precision
    assertEquals(DistanceText("100", METERS), format(99.6))
    assertEquals(DistanceText("350", METERS), format(347.0))
    assertEquals(DistanceText("990", METERS), format(994.9))
  }

  @Test
  fun justUnderAKilometreRoundsUpToKilometres() {
    // Rounded to tens it would read "1000 m"
    assertEquals(DistanceText("1.0", KILOMETERS), format(995.0))
  }

  @Test
  fun kilometresHaveOneDecimal() {
    assertEquals(DistanceText("1.2", KILOMETERS), format(1_234.0))
    assertEquals(DistanceText("9.9", KILOMETERS), format(9_940.0))
  }

  @Test
  fun tenKilometresAndMoreAreWhole() {
    assertEquals(DistanceText("10", KILOMETERS), format(9_950.0))
    assertEquals(DistanceText("51", KILOMETERS), format(51_359.2))
  }

  @Test
  fun followsTheLanguagesDecimalSeparator() {
    assertEquals(DistanceText("1,2", KILOMETERS), formatDistance(1_234.0, Locale.FRANCE))
  }
}
