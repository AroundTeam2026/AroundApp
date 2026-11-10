// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import android.util.Log
import com.github.aroundteam2026.aroundapp.model.address.AddressSearchRepository
import com.github.aroundteam2026.aroundapp.model.address.AddressSearchResult
import com.github.aroundteam2026.aroundapp.model.address.AddressSuggestion
import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What the address search found. The field's text is kept by the screen's ViewModel.
 *
 * @property suggestions The addresses found for the latest search, best first. They stay while the
 *   next search runs, so the list doesn't flicker as the user types.
 * @property status Whether a search is running, or why there is nothing to show.
 */
data class AddressSearchUiState(
    val suggestions: List<AddressSuggestion> = emptyList(),
    val status: AddressSearchStatus = AddressSearchStatus.IDLE,
)

/** Where the address search stands. */
enum class AddressSearchStatus {
  /** Nothing to search, or the latest search found [AddressSearchUiState.suggestions]. */
  IDLE,
  /** A search is waiting for typing to pause, or running. */
  SEARCHING,
  /** The latest search found nothing. */
  NO_RESULTS,
  /** The latest search failed; trying again may work. */
  FAILED,
  /** This device can't search addresses. */
  UNAVAILABLE,
}

/**
 * Searches addresses as the user types, for a ViewModel that keeps the query and the
 * [AddressSearchUiState] itself, and gets each change to it through [onChange]. A query of at least
 * [MIN_QUERY_LENGTH] characters is searched once typing pauses for [SEARCH_DELAY_MILLIS]; each
 * search replaces the one still pending or running, and one that throws shows as failed. The
 * searches run in [scope].
 */
class AddressSearchController(
    private val scope: CoroutineScope,
    private val repository: AddressSearchRepository,
    private val onChange: ((AddressSearchUiState) -> AddressSearchUiState) -> Unit,
) {
  private var lookup: Job? = null

  /**
   * Called as the user types [query]: searches it around [near] once typing pauses, if it is long
   * enough once trimmed, and otherwise drops the search. [near] is read when the search runs.
   */
  fun onQueryChanged(query: String, near: () -> GeoBounds?) {
    lookup?.cancel()
    if (query.trim().length < MIN_QUERY_LENGTH) {
      onChange { AddressSearchUiState() }
      return
    }
    onChange { it.copy(status = AddressSearchStatus.SEARCHING) }
    lookup = search(query, near, SEARCH_DELAY_MILLIS)
  }

  /** Searches [query] at once, however short, unless it is blank: the keyboard's search key. */
  fun searchNow(query: String, near: () -> GeoBounds?) {
    if (query.isBlank()) return
    lookup?.cancel()
    onChange { it.copy(status = AddressSearchStatus.SEARCHING) }
    lookup = search(query, near, delayMillis = 0)
  }

  /** Drops the search pending or running, and what the last one found. */
  fun dismiss() {
    lookup?.cancel()
    onChange { AddressSearchUiState() }
  }

  private fun search(query: String, near: () -> GeoBounds?, delayMillis: Long): Job = scope.launch {
    delay(delayMillis)
    val result =
        try {
          repository.search(query, near())
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (e: Exception) {
          Log.w(TAG, "Searching addresses failed", e)
          AddressSearchResult.Failed
        }
    onChange { it.showing(result) }
  }

  companion object {
    private const val TAG = "AddressSearchController"

    /** How long typing must pause before the address is searched. */
    const val SEARCH_DELAY_MILLIS = 300L

    /** How many characters, spaces aside, a query needs before it is searched while typing. */
    const val MIN_QUERY_LENGTH = 3

    /**
     * How far a pin can move from the address picked for it and keep it: the largest visit radius,
     * enough to reach an entrance around the corner. Farther, the address no longer says where the
     * pin is.
     */
    const val ADDRESS_DRIFT_METERS = VenueLimits.MAX_RADIUS_METERS.toDouble()
  }
}

/** This search once [result] arrived: its suggestions, or why there are none. */
private fun AddressSearchUiState.showing(result: AddressSearchResult): AddressSearchUiState =
    when (result) {
      is AddressSearchResult.Found ->
          copy(
              suggestions = result.suggestions,
              status =
                  if (result.suggestions.isEmpty()) AddressSearchStatus.NO_RESULTS
                  else AddressSearchStatus.IDLE,
          )
      AddressSearchResult.Failed ->
          copy(suggestions = emptyList(), status = AddressSearchStatus.FAILED)
      AddressSearchResult.Unavailable ->
          copy(suggestions = emptyList(), status = AddressSearchStatus.UNAVAILABLE)
    }
