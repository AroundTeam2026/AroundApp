// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.address

import android.location.Address
import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Searches addresses with a [Geocoding]. Around a known view, it looks within [BIAS_RADIUS_METERS]
 * of the view's centre and anywhere at the same time, then lists the nearby matches first, so a
 * nearby street comes first but an address in another town is still found. It lists at most
 * [MAX_SUGGESTIONS].
 *
 * Either lookup may fail on its own: the other's matches are still listed. The search fails only
 * when every lookup fails.
 */
class GeocoderAddressRepository(private val geocoding: Geocoding) : AddressSearchRepository {

  override suspend fun search(query: String, near: GeoBounds?): AddressSearchResult {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return AddressSearchResult.Found(emptyList())
    if (!geocoding.isAvailable) return AddressSearchResult.Unavailable
    // Around the view first, then anywhere; the same bounds twice are looked up once
    val answered = coroutineScope {
      listOf(biasArea(near), null).distinct().map { async { lookUp(trimmed, it) } }.awaitAll()
    }
        .filterNotNull()
    if (answered.isEmpty()) return AddressSearchResult.Failed
    return AddressSearchResult.Found(answered.flatten().distinct().take(MAX_SUGGESTIONS))
  }

  /** The suggestions for [query] within [within], or null when the lookup fails. */
  private suspend fun lookUp(query: String, within: GeoBounds?): List<AddressSuggestion>? =
      try {
        geocoding.fromName(query, MAX_SUGGESTIONS, within).mapNotNull { it.toSuggestion() }
      } catch (_: IOException) {
        null
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
 * This address as a suggestion. A named place, such as "EPFL", shows its name above its street,
 * postcode and town; a street address shows its street and number above its postcode and town. An
 * address known only by its full line is split at its first comma. Null when it has no coordinates
 * or nothing to show.
 */
private fun Address.toSuggestion(): AddressSuggestion? {
  if (!hasLatitude() || !hasLongitude()) return null
  val street = listOfNotNull(thoroughfare, subThoroughfare).joinToString(" ").ifBlank { null }
  val town = listOfNotNull(postalCode, locality).joinToString(" ").ifBlank { null }
  val location = Location(latitude, longitude)
  val place = placeName(street)
  if (place != null) {
    return AddressSuggestion(
        place,
        listOfNotNull(street, town).joinToString(", ").ifBlank { null },
        location,
    )
  }
  val title = street ?: featureName?.takeIf { it.isNotBlank() }
  if (title != null) return AddressSuggestion(title, town, location)
  val line = getAddressLine(0)?.takeIf { it.isNotBlank() } ?: return null
  val parts = line.split(",", limit = 2).map { it.trim() }
  return AddressSuggestion(parts[0], parts.getOrNull(1)?.ifBlank { null }, location)
}

/**
 * The name of the place at this address, or null when it has none. Geocoders also put the house
 * number or the street in [Address.getFeatureName]; those are not a place's name.
 */
private fun Address.placeName(street: String?): String? {
  val name = featureName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  val notAName =
      listOfNotNull(subThoroughfare, thoroughfare, street).any {
        it.trim().equals(name, ignoreCase = true)
      }
  return if (notAName || street == null) null else name
}
