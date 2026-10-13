// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest

import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests how a discount's amount is written on a quest card. */
class RewardAmountFormatTest {

  @Test
  fun wholeAmountsHaveNoDecimals() {
    assertEquals("10", formatRewardAmount(10.0, DiscountUnit.CHF, Locale.US))
    assertEquals("10", formatRewardAmount(10.0, DiscountUnit.PERCENT, Locale.US))
  }

  @Test
  fun fractionalChfAmountsHaveTwoDecimals() {
    assertEquals("2.50", formatRewardAmount(2.5, DiscountUnit.CHF, Locale.US))
  }

  @Test
  fun fractionalPercentagesHaveOnlyTheDecimalsTheyNeed() {
    assertEquals("12.5", formatRewardAmount(12.5, DiscountUnit.PERCENT, Locale.US))
    assertEquals("12.25", formatRewardAmount(12.25, DiscountUnit.PERCENT, Locale.US))
  }

  // Bug: the decimals are chosen before rounding, so an amount that rounds to a whole number
  // keeps two zero decimals.
  @Test
  fun decimalsAreChosenOnTheRoundedAmount() {
    assertEquals("10", formatRewardAmount(9.999, DiscountUnit.CHF, Locale.US))
    assertEquals("12.5", formatRewardAmount(12.499, DiscountUnit.PERCENT, Locale.US))
  }

  @Test
  fun followsTheLanguagesDecimalSeparator() {
    assertEquals("2,50", formatRewardAmount(2.5, DiscountUnit.CHF, Locale.FRENCH))
  }

  // The amount field accepts only ASCII digits and a point or comma, so the minimum shown in its
  // error must be written that way too.
  @Test
  fun writesOnlyWhatTheAmountFieldAccepts() {
    assertEquals("2.50", formatRewardAmount(2.5, DiscountUnit.CHF, Locale.forLanguageTag("ar-EG")))
    assertEquals("1000", formatRewardAmount(1000.0, DiscountUnit.CHF, Locale.forLanguageTag("fa")))
  }
}
