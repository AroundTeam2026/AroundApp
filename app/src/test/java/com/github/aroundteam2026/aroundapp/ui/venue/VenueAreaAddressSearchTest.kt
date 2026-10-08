// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import com.github.aroundteam2026.aroundapp.model.address.AddressSearchResult
import com.github.aroundteam2026.aroundapp.model.address.AddressSuggestion
import com.github.aroundteam2026.aroundapp.model.address.FakeAddressSearchRepository
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.model.common.distanceTo
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.ui.venue.VenueAreaViewModel.Companion.ADDRESS_DRIFT_METERS
import com.github.aroundteam2026.aroundapp.ui.venue.VenueAreaViewModel.Companion.FRAMED_RADIUS_METERS
import com.github.aroundteam2026.aroundapp.ui.venue.VenueAreaViewModel.Companion.SEARCH_DELAY_MILLIS
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests the address search of [VenueAreaViewModel]: typing, searching, picking and clearing. */
@OptIn(ExperimentalCoroutinesApi::class)
class VenueAreaAddressSearchTest {

  private val dispatcher = StandardTestDispatcher()
  private val bourg =
      AddressSuggestion("Rue de Bourg 12", "1003 Lausanne", Location(46.5199, 6.6346))
  private val gare =
      AddressSuggestion("Place de la Gare 9", "1003 Lausanne", Location(46.5167, 6.6291))
  private val entrance = Location(46.5191, 6.6335)
  private val zurich = Location(47.3769, 8.5417)

  private val locations = FakeLocationRepository(zurich)
  private val addresses = FakeAddressSearchRepository { query ->
    AddressSearchResult.Found(listOf(bourg, gare).filter { it.title.contains(query, true) })
  }
  private val viewModel by lazy { VenueAreaViewModel(locations, addresses) }

  private val state
    get() = viewModel.uiState.value

  private val search
    get() = state.addressSearch

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  @Test
  fun theSearchStartsEmptyAndIdle() {
    assertEquals("", viewModel.addressQuery)
    assertEquals(emptyList<AddressSuggestion>(), search.suggestions)
    assertEquals(AddressSearchStatus.IDLE, search.status)
    assertNull(state.address)
  }

  @Test
  fun typingShowsTheQueryAtOnce() {
    viewModel.onAddressQueryChanged("Ru")

    assertEquals("Ru", viewModel.addressQuery)
  }

  @Test
  fun fewerThanThreeCharactersNeverSearch() = test {
    viewModel.onAddressQueryChanged("R")
    viewModel.onAddressQueryChanged("Ru")
    viewModel.onAddressQueryChanged("  Ru  ")
    advanceUntilIdle()

    assertTrue(addresses.searches.isEmpty())
    assertEquals(AddressSearchStatus.IDLE, search.status)
  }

  @Test
  fun aLongEnoughQuerySearchesOnceTypingPauses() = test {
    viewModel.onAddressQueryChanged("Rue")
    advanceTimeBy(SEARCH_DELAY_MILLIS - 1)

    assertTrue(addresses.searches.isEmpty())
    assertEquals(AddressSearchStatus.SEARCHING, search.status)

    advanceTimeBy(2)
    assertEquals(listOf("Rue"), addresses.searches.map { it.query })
    assertEquals(listOf(bourg), search.suggestions)
    assertEquals(AddressSearchStatus.IDLE, search.status)
  }

  @Test
  fun typingQuicklySearchesOnlyTheLastQuery() = test {
    listOf("Rue", "Rue d", "Rue de", "Rue de B").forEach {
      viewModel.onAddressQueryChanged(it)
      advanceTimeBy(SEARCH_DELAY_MILLIS / 2)
    }
    advanceUntilIdle()

    assertEquals(listOf("Rue de B"), addresses.searches.map { it.query })
  }

  @Test
  fun anOlderSlowSearchNeverReplacesANewerOne() = test {
    addresses.gates["Rue"] = CompletableDeferred()
    viewModel.onAddressQueryChanged("Rue")
    advanceTimeBy(SEARCH_DELAY_MILLIS + 1)
    viewModel.onAddressQueryChanged("Place")
    advanceUntilIdle()
    assertEquals(listOf(gare), search.suggestions)

    addresses.gates.getValue("Rue").complete(Unit)
    advanceUntilIdle()

    assertEquals(listOf(gare), search.suggestions)
  }

  @Test
  fun earlierSuggestionsStayWhileTheNextSearchRuns() = test {
    viewModel.onAddressQueryChanged("Rue")
    advanceUntilIdle()

    viewModel.onAddressQueryChanged("Rue de")

    assertEquals(listOf(bourg), search.suggestions)
    assertEquals(AddressSearchStatus.SEARCHING, search.status)
  }

  @Test
  fun shorteningTheQueryBelowThreeCharactersClearsTheSuggestions() = test {
    viewModel.onAddressQueryChanged("Rue")
    advanceUntilIdle()

    viewModel.onAddressQueryChanged("Ru")
    advanceUntilIdle()

    assertEquals(emptyList<AddressSuggestion>(), search.suggestions)
    assertEquals(AddressSearchStatus.IDLE, search.status)
    assertEquals(1, addresses.searches.size)
  }

  @Test
  fun aSearchWithoutResultsSaysSo() = test {
    viewModel.onAddressQueryChanged("Nowhere street")
    advanceUntilIdle()

    assertEquals(emptyList<AddressSuggestion>(), search.suggestions)
    assertEquals(AddressSearchStatus.NO_RESULTS, search.status)
  }

  @Test
  fun aFailedSearchSaysSoAndDropsOldSuggestions() = test {
    viewModel.onAddressQueryChanged("Rue")
    advanceUntilIdle()
    addresses.answer = { AddressSearchResult.Failed }

    viewModel.onAddressQueryChanged("Rue de")
    advanceUntilIdle()

    assertEquals(emptyList<AddressSuggestion>(), search.suggestions)
    assertEquals(AddressSearchStatus.FAILED, search.status)
  }

  @Test
  fun anUnavailableSearchSaysSo() = test {
    addresses.answer = { AddressSearchResult.Unavailable }

    viewModel.onAddressQueryChanged("Rue")
    advanceUntilIdle()

    assertEquals(AddressSearchStatus.UNAVAILABLE, search.status)
  }

  @Test
  fun theSearchKeySearchesAtOnceEvenForAShortQuery() = test {
    viewModel.onAddressQueryChanged("Ru")
    viewModel.onAddressSearch()
    runCurrent()

    assertEquals(listOf("Ru"), addresses.searches.map { it.query })
    assertEquals(listOf(bourg), search.suggestions)
  }

  @Test
  fun theSearchKeyReplacesThePendingSearch() = test {
    viewModel.onAddressQueryChanged("Rue")
    viewModel.onAddressSearch()
    advanceUntilIdle()

    assertEquals(1, addresses.searches.size)
  }

  @Test
  fun theSearchKeyDoesNothingForABlankQuery() = test {
    viewModel.onAddressQueryChanged("   ")
    viewModel.onAddressSearch()
    advanceUntilIdle()

    assertTrue(addresses.searches.isEmpty())
    assertEquals(AddressSearchStatus.IDLE, search.status)
  }

  @Test
  fun theSearchLooksAroundTheAreaTheMapShows() = test {
    val view = entrance.boundsWithin(400.0)
    viewModel.onVisibleAreaChanged(view)

    viewModel.onAddressQueryChanged("Rue")
    advanceUntilIdle()

    assertEquals(view, addresses.searches.single().near)
  }

  @Test
  fun beforeTheMapReportsItsViewTheSearchHasNoArea() = test {
    viewModel.onAddressQueryChanged("Rue")
    advanceUntilIdle()

    assertNull(addresses.searches.single().near)
  }

  @Test
  fun pickingASuggestionPlacesTheMarkerFramesItAndKeepsTheAddress() = test {
    viewModel.onAddressQueryChanged("Rue")
    advanceUntilIdle()

    viewModel.onAddressPicked(bourg)

    assertEquals(bourg.location, state.marker)
    assertEquals(bourg.location.boundsWithin(FRAMED_RADIUS_METERS), state.areaToFrame)
    assertEquals("Rue de Bourg 12, 1003 Lausanne", state.address)
    assertEquals("Rue de Bourg 12, 1003 Lausanne", viewModel.addressQuery)
    assertEquals(emptyList<AddressSuggestion>(), search.suggestions)
    assertEquals(AddressSearchStatus.IDLE, search.status)
  }

  @Test
  fun pickingMovesAMarkerAlreadyPlaced() {
    viewModel.onMarkerPlaced(entrance)

    viewModel.onAddressPicked(gare)

    assertEquals(gare.location, state.marker)
    assertEquals(gare.location.boundsWithin(FRAMED_RADIUS_METERS), state.areaToFrame)
  }

  @Test
  fun pickingCancelsASearchStillWaiting() = test {
    viewModel.onAddressQueryChanged("Place")
    viewModel.onAddressPicked(bourg)
    advanceUntilIdle()

    assertTrue(addresses.searches.isEmpty())
    assertEquals(emptyList<AddressSuggestion>(), search.suggestions)
  }

  @Test
  fun aLocationLookupFinishingAfterAPickDoesNotMoveTheCamera() = test {
    val gate = CompletableDeferred<Unit>()
    locations.gate = gate
    viewModel.onLocationPermissionResult(true)
    viewModel.onUseMyLocation()
    runCurrent()

    viewModel.onAddressPicked(bourg)
    gate.complete(Unit)
    advanceUntilIdle()

    // The venue's latest choice wins over a slow position lookup
    assertEquals(bourg.location.boundsWithin(FRAMED_RADIUS_METERS), state.areaToFrame)
  }

  @Test
  fun useMyLocationAfterAPickStillFramesTheDevice() = test {
    viewModel.onAddressPicked(bourg)
    viewModel.onLocationPermissionResult(true)
    advanceUntilIdle()

    viewModel.onUseMyLocation()
    advanceUntilIdle()

    assertEquals(zurich.boundsWithin(FRAMED_RADIUS_METERS), state.areaToFrame)
    assertEquals(bourg.location, state.marker)
  }

  @Test
  fun movingTheMarkerAfterAPickKeepsTheAddress() {
    viewModel.onAddressPicked(bourg)

    viewModel.onMarkerPlaced(entrance)

    // About 120 m away: the marker refines where the entrance is; the address stays the venue's
    assertTrue(bourg.location.distanceTo(entrance) < ADDRESS_DRIFT_METERS)
    assertEquals("Rue de Bourg 12, 1003 Lausanne", state.address)
  }

  @Test
  fun movingTheMarkerFarFromThePickedAddressForgetsIt() {
    viewModel.onAddressPicked(bourg)
    val acrossTown = Location(46.5235, 6.6382)
    assertTrue(bourg.location.distanceTo(acrossTown) > ADDRESS_DRIFT_METERS)

    viewModel.onMarkerPlaced(acrossTown)

    // The address no longer describes where the marker is, so it mustn't be saved with it
    assertNull(state.address)
    assertEquals(acrossTown, state.marker)
    // Nor shown as if it would be
    assertEquals("", viewModel.addressQuery)
  }

  @Test
  fun aLongDragKeepsANewSearchTheVenueIsTyping() {
    viewModel.onAddressPicked(bourg)
    viewModel.onAddressQueryChanged("Place de")

    viewModel.onMarkerPlaced(Location(46.5235, 6.6382))

    assertNull(state.address)
    assertEquals("Place de", viewModel.addressQuery)
  }

  @Test
  fun aShortDragKeepsThePickedAddressInTheField() {
    viewModel.onAddressPicked(bourg)

    viewModel.onMarkerPlaced(entrance)

    assertEquals("Rue de Bourg 12, 1003 Lausanne", viewModel.addressQuery)
  }

  @Test
  fun aForgottenAddressStaysForgottenWhenTheMarkerComesBack() {
    viewModel.onAddressPicked(bourg)
    viewModel.onMarkerPlaced(Location(46.5235, 6.6382))

    viewModel.onMarkerPlaced(bourg.location)

    assertNull(state.address)
  }

  @Test
  fun aNewPickAfterForgettingKeepsTheNewAddress() {
    viewModel.onAddressPicked(bourg)
    viewModel.onMarkerPlaced(Location(46.5235, 6.6382))

    viewModel.onAddressPicked(gare)
    viewModel.onMarkerPlaced(gare.location)

    assertEquals("Place de la Gare 9, 1003 Lausanne", state.address)
  }

  @Test
  fun aMarkerPlacedWithoutAPickHasNoAddress() {
    viewModel.onMarkerPlaced(entrance)

    assertNull(state.address)
  }

  @Test
  fun dismissingHidesTheSuggestionsButKeepsTheQuery() = test {
    viewModel.onAddressQueryChanged("Rue")
    advanceUntilIdle()

    viewModel.onAddressSearchDismissed()

    assertEquals("Rue", viewModel.addressQuery)
    assertEquals(emptyList<AddressSuggestion>(), search.suggestions)
    assertEquals(AddressSearchStatus.IDLE, search.status)
  }

  @Test
  fun dismissingCancelsAPendingSearch() = test {
    viewModel.onAddressQueryChanged("Rue")
    viewModel.onAddressSearchDismissed()
    advanceUntilIdle()

    assertTrue(addresses.searches.isEmpty())
    assertEquals(emptyList<AddressSuggestion>(), search.suggestions)
  }

  @Test
  fun dismissingDuringASearchDropsItsResults() = test {
    addresses.gates["Rue"] = CompletableDeferred()
    viewModel.onAddressQueryChanged("Rue")
    advanceTimeBy(SEARCH_DELAY_MILLIS + 1)

    viewModel.onAddressSearchDismissed()
    addresses.gates.getValue("Rue").complete(Unit)
    advanceUntilIdle()

    assertEquals(emptyList<AddressSuggestion>(), search.suggestions)
    assertEquals(AddressSearchStatus.IDLE, search.status)
  }

  @Test
  fun dismissingKeepsTheMarkerAndAddress() {
    viewModel.onAddressPicked(bourg)

    viewModel.onAddressSearchDismissed()

    assertEquals(bourg.location, state.marker)
    assertEquals("Rue de Bourg 12, 1003 Lausanne", state.address)
  }

  @Test
  fun clearingEmptiesTheFieldAndSuggestionsButKeepsTheMarkerAndAddress() = test {
    viewModel.onAddressPicked(bourg)
    viewModel.onAddressQueryChanged("Place")
    advanceUntilIdle()

    viewModel.onAddressCleared()

    assertEquals("", viewModel.addressQuery)
    assertEquals(emptyList<AddressSuggestion>(), search.suggestions)
    assertEquals(AddressSearchStatus.IDLE, search.status)
    assertEquals(bourg.location, state.marker)
    assertEquals("Rue de Bourg 12, 1003 Lausanne", state.address)
  }
}
