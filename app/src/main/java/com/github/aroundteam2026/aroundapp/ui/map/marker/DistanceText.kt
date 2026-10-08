// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import java.util.Locale
import kotlin.math.roundToLong

/** The unit a [DistanceText] is in. */
enum class DistanceUnit {
  METERS,
  KILOMETERS,
}

/** A distance as the card says it: [amount], written for the language, in [unit]. */
data class DistanceText(val amount: String, val unit: DistanceUnit)

/**
 * How far [meters] is, as the card says it: whole metres up to 100, then tens of metres, then
 * kilometres with one decimal, and whole kilometres from 10, with [locale]'s decimal separator.
 * Each step is chosen on the rounded value, so 995 m reads 1.0 km rather than 1000 m.
 */
fun formatDistance(meters: Double, locale: Locale): DistanceText {
  val wholeMeters = meters.roundToLong()
  if (wholeMeters < 100) return DistanceText("$wholeMeters", DistanceUnit.METERS)
  val tensOfMeters = (meters / 10).roundToLong() * 10
  if (tensOfMeters < 1_000) return DistanceText("$tensOfMeters", DistanceUnit.METERS)
  val tenthsOfKilometers = (meters / 100).roundToLong()
  if (tenthsOfKilometers < 100) {
    return DistanceText(
        String.format(locale, "%.1f", tenthsOfKilometers / 10.0),
        DistanceUnit.KILOMETERS,
    )
  }
  return DistanceText("${(meters / 1_000).roundToLong()}", DistanceUnit.KILOMETERS)
}
