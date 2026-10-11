// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest

import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Writes a discount [amount] in [unit] for [locale], rounded to two decimals: none when the rounded
 * amount is whole, otherwise two in CHF (2.50) and only those needed in percent (12.5). The
 * decimals are chosen on the rounded value, so 9.999 reads 10 rather than 10.00.
 */
internal fun formatRewardAmount(amount: Double, unit: DiscountUnit, locale: Locale): String {
  val hundredths = (amount * 100).roundToLong()
  val decimals =
      when {
        hundredths % 100 == 0L -> 0
        unit == DiscountUnit.CHF || hundredths % 10 != 0L -> 2
        else -> 1
      }
  return String.format(locale, "%.${decimals}f", hundredths / 100.0)
}
