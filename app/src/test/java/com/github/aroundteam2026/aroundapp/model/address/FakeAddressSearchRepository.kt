// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.address

import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import kotlinx.coroutines.CompletableDeferred

/**
 * Answers each search with [answer], after its query's gate in [gates] opens if there is one, and
 * records the searches.
 */
class FakeAddressSearchRepository(
    var answer: (query: String) -> AddressSearchResult = { AddressSearchResult.Found(emptyList()) }
) : AddressSearchRepository {
  data class Search(val query: String, val near: GeoBounds?)

  val searches = mutableListOf<Search>()
  val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

  override suspend fun search(query: String, near: GeoBounds?): AddressSearchResult {
    searches += Search(query, near)
    gates[query]?.await()
    return answer(query)
  }
}
