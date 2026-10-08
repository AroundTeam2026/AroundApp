// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.venue

import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.testVenue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests [VenueRepository.observeVenues] on [FakeVenueRepository] and on the interface's default
 * implementation, which every other repository (e.g. test doubles in other features) inherits.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class ObserveVenuesTest(private val implementation: String) {

  private val cafe = testVenue(id = "cafe", name = "Café Lumen")
  private val bar = testVenue(id = "bar", name = "Bar Nocturne")
  private val brasserie = testVenue(id = "brasserie", name = "Brasserie des Quais")

  private fun repository(vararg venues: Venue): VenueRepository =
      when (implementation) {
        FAKE -> FakeVenueRepository(initialVenues = venues.toList())
        else -> OnlyObserveVenue(FakeVenueRepository(initialVenues = venues.toList()))
      }

  /** Collects every emission of [flow] until the test ends. */
  private fun <T> TestScope.record(flow: Flow<T>): List<T> {
    val emissions = mutableListOf<T>()
    backgroundScope.launch { flow.collect { emissions += it } }
    runCurrent()
    return emissions
  }

  @Test
  fun emitsTheRequestedVenues() = runTest {
    val repository = repository(cafe, bar, brasserie)

    val venues = repository.observeVenues(setOf("cafe", "bar")).first()

    assertEquals(setOf(cafe, bar), venues.toSet())
  }

  @Test
  fun leavesOutIdsWithoutAVenue() = runTest {
    // Quests can outlive their venue, or name one that this repository doesn't have
    val repository = repository(cafe)

    assertEquals(listOf(cafe), repository.observeVenues(setOf("cafe", "gone")).first())
  }

  @Test
  fun emitsAnEmptyListAtOnceForNoIds() = runTest {
    // A caller with nothing to ask for must not wait forever
    val repository = repository(cafe)

    assertEquals(emptyList<Venue>(), repository.observeVenues(emptySet()).first())
  }

  @Test
  fun emitsAgainWhenARequestedVenueChanges() = runTest {
    val repository = repository(cafe, bar)
    val emissions = record(repository.observeVenues(setOf("cafe")))

    repository.setArea("cafe", Location(46.60, 6.70), radiusMeters = 80)
    runCurrent()

    assertEquals(2, emissions.size)
    assertEquals(Location(46.60, 6.70), emissions.last().single().location)
  }

  @Test
  fun emitsOnceARequestedVenueIsCreated() = runTest {
    val repository = repository(cafe)
    val emissions = record(repository.observeVenues(setOf("cafe", "bar")))

    repository.createVenue(bar)
    runCurrent()

    assertEquals(setOf(cafe), emissions.first().toSet())
    assertEquals(setOf(cafe, bar), emissions.last().toSet())
  }

  @Test
  fun doesNotEmitWhenAnotherVenueChanges() = runTest {
    val repository = repository(cafe, bar)
    val emissions = record(repository.observeVenues(setOf("cafe")))

    repository.setArea("bar", Location(46.60, 6.70), radiusMeters = 80)
    repository.createVenue(brasserie)
    runCurrent()

    assertEquals(listOf(listOf(cafe)), emissions)
  }

  companion object {
    private const val FAKE = "FakeVenueRepository"
    private const val DEFAULT = "VenueRepository default"

    @JvmStatic @Parameterized.Parameters(name = "{0}") fun implementations() = listOf(FAKE, DEFAULT)
  }
}

/** A [VenueRepository] that implements only what it must, so it uses every default method. */
private class OnlyObserveVenue(private val venues: FakeVenueRepository) : VenueRepository {
  override fun observeVenue(venueId: String): Flow<Venue?> = venues.observeVenue(venueId)

  override suspend fun getVenue(venueId: String) = venues.getVenue(venueId)

  override suspend fun createVenue(venue: Venue) = venues.createVenue(venue)

  override suspend fun setArea(venueId: String, location: Location, radiusMeters: Int) =
      venues.setArea(venueId, location, radiusMeters)
}
