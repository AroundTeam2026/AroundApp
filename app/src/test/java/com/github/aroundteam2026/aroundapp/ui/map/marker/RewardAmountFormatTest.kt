// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests how a discount's amount is written on a quest card. */
class RewardAmountFormatTest {

  @Test
  fun wholeAmountsHaveNoDecimals() {
    assertEquals("10", formatRewardAmount(10.0, Locale.US))
  }

  @Test
  fun fractionalAmountsHaveTwoDecimals() {
    assertEquals("2.50", formatRewardAmount(2.5, Locale.US))
  }

  @Test
  fun followsTheLanguagesDecimalSeparator() {
    assertEquals("2,50", formatRewardAmount(2.5, Locale.FRENCH))
  }
}
