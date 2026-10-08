// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.address

import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.common.Location

/**
 * An address that matches what the user typed, as a search lists it.
 *
 * @property title The first line: the street and number, or the place's name.
 * @property subtitle The second line, such as the postcode and town, or null when unknown.
 * @property location Where the address is.
 */
data class AddressSuggestion(val title: String, val subtitle: String?, val location: Location) {
  /** The whole address on one line, as a venue's address is kept. */
  val address: String
    get() = listOfNotNull(title, subtitle).joinToString(", ")
}

/** What an address search found. */
sealed interface AddressSearchResult {
  /** The search ran; [suggestions] are best first, and may be empty. */
  data class Found(val suggestions: List<AddressSuggestion>) : AddressSearchResult

  /** The search failed, for example without a network connection; trying again may work. */
  data object Failed : AddressSearchResult

  /** This device can't search addresses at all. */
  data object Unavailable : AddressSearchResult
}

/** Finds addresses from what the user types. */
interface AddressSearchRepository {
  /**
   * Addresses matching [query], preferring those around [near], the area the map shows, when it is
   * known.
   */
  suspend fun search(query: String, near: GeoBounds?): AddressSearchResult
}
