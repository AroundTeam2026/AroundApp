// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.address

import android.location.Address
import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import java.io.IOException

/**
 * Searches addresses with a [Geocoding]. Around a known view, it looks within [BIAS_RADIUS_METERS]
 * of the view's centre first, then fills up with matches from anywhere, so a nearby street comes
 * first but an address in another town is still found. It lists at most [MAX_SUGGESTIONS].
 */
class GeocoderAddressRepository(private val geocoding: Geocoding) : AddressSearchRepository {

  override suspend fun search(query: String, near: GeoBounds?): AddressSearchResult {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return AddressSearchResult.Found(emptyList())
    if (!geocoding.isAvailable) return AddressSearchResult.Unavailable
    return try {
      val suggestions = mutableListOf<AddressSuggestion>()
      biasArea(near)?.let { suggestions.addNew(lookUp(trimmed, it)) }
      if (suggestions.size < MAX_SUGGESTIONS) suggestions.addNew(lookUp(trimmed, null))
      AddressSearchResult.Found(suggestions.take(MAX_SUGGESTIONS))
    } catch (_: IOException) {
      AddressSearchResult.Failed
    }
  }

  private suspend fun lookUp(query: String, within: GeoBounds?): List<AddressSuggestion> =
      geocoding.fromName(query, MAX_SUGGESTIONS, within).mapNotNull { it.toSuggestion() }

  private fun MutableList<AddressSuggestion>.addNew(found: List<AddressSuggestion>) {
    found.forEach { if (it !in this) add(it) }
  }

  companion object {
    /** How many suggestions a search lists at most. */
    const val MAX_SUGGESTIONS = 5

    /** How far around the view's centre the first lookup goes. */
    const val BIAS_RADIUS_METERS = 20_000.0
  }
}

/**
 * The bounds the first lookup searches: [BIAS_RADIUS_METERS] around the centre of [view], or null
 * when there is no view, or when either crosses the antimeridian, which the geocoder can't take.
 */
private fun biasArea(view: GeoBounds?): GeoBounds? {
  if (view == null || view.west > view.east) return null
  val centre = Location((view.south + view.north) / 2, (view.west + view.east) / 2)
  return centre.boundsWithin(GeocoderAddressRepository.BIAS_RADIUS_METERS).takeIf {
    it.west <= it.east
  }
}

/**
 * This address as a suggestion: the street and number, or the place's name, above the postcode and
 * town. An address known only by its full line is split at its first comma. Null when it has no
 * coordinates or nothing to show.
 */
private fun Address.toSuggestion(): AddressSuggestion? {
  if (!hasLatitude() || !hasLongitude()) return null
  val street = listOfNotNull(thoroughfare, subThoroughfare).joinToString(" ").ifBlank { null }
  val town = listOfNotNull(postalCode, locality).joinToString(" ").ifBlank { null }
  val title = street ?: featureName?.takeIf { it.isNotBlank() }
  val location = Location(latitude, longitude)
  if (title != null) return AddressSuggestion(title, town, location)
  val line = getAddressLine(0)?.takeIf { it.isNotBlank() } ?: return null
  val parts = line.split(",", limit = 2).map { it.trim() }
  return AddressSuggestion(parts[0], parts.getOrNull(1)?.ifBlank { null }, location)
}
