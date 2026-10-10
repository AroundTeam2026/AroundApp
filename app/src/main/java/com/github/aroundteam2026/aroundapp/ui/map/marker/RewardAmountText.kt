// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import java.util.Locale

/** Writes a discount [amount] for [locale], without decimals when it is whole (10, but 2.50). */
internal fun formatRewardAmount(amount: Double, locale: Locale): String =
    String.format(locale, if (amount % 1.0 == 0.0) "%.0f" else "%.2f", amount)
