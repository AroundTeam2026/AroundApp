// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.venue

import com.github.aroundteam2026.aroundapp.model.common.Location
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeVenueRepositoryTest {

  /** Builds a placed venue by default; tests override only the fields they care about. */
  private fun venue(
      id: String = "venue-1",
      name: String = "Cafe",
      location: Location? = Location(46.52, 6.57),
      address: String? = "Rue du Lac 1",
  ) =
      Venue(
          id = id,
          name = name,
          location = location,
          radiusMeters = 50,
          address = address,
          createdAt = 1_000L,
      )

  @Test
  fun getVenue_returnsNullForUnknownId() = runTest {
    val repository = FakeVenueRepository()

    Assert.assertNull(repository.getVenue("unknown-venue"))
  }

  @Test
  fun createVenue_thenGetVenue_returnsTheStoredVenue() = runTest {
    val repository = FakeVenueRepository()
    val venue = venue()

    val result = repository.createVenue(venue)

    Assert.assertEquals(Result.success(Unit), result)
    Assert.assertEquals(venue, repository.getVenue(venue.id))
  }

  @Test
  fun createVenue_keepsANullLocation() = runTest {
    val repository = FakeVenueRepository()
    val venue = venue(location = null, address = null)

    repository.createVenue(venue)

    val stored = repository.getVenue(venue.id)
    Assert.assertNotNull(stored)
    Assert.assertNull(stored?.location)
    Assert.assertNull(stored?.address)
  }

  @Test
  fun createVenue_withExistingId_failsAndKeepsTheOriginal() = runTest {
    val repository = FakeVenueRepository()
    val original = venue(name = "Original")
    val replacement = venue(name = "Replacement")

    repository.createVenue(original)
    val result = repository.createVenue(replacement)

    Assert.assertTrue(result.exceptionOrNull() is IllegalStateException)
    Assert.assertEquals(original, repository.getVenue(original.id))
  }

  @Test
  fun createVenue_forDifferentIds_storesBoth() = runTest {
    val repository = FakeVenueRepository()
    val venueA = venue(id = "venue-a")
    val venueB = venue(id = "venue-b")

    repository.createVenue(venueA)
    repository.createVenue(venueB)

    Assert.assertEquals(venueA, repository.getVenue("venue-a"))
    Assert.assertEquals(venueB, repository.getVenue("venue-b"))
  }

  @Test
  fun observeVenue_emitsNullForUnknownId() = runTest {
    val repository = FakeVenueRepository()

    Assert.assertNull(repository.observeVenue("unknown-venue").first())
  }

  @Test
  fun observeVenue_emitsTheVenueAfterCreate() = runTest {
    val repository = FakeVenueRepository()
    val venue = venue()
    val emissions = mutableListOf<Venue?>()
    val job = launch { repository.observeVenue(venue.id).collect { emissions.add(it) } }
    runCurrent()

    repository.createVenue(venue)
    runCurrent()

    Assert.assertEquals(listOf(null, venue), emissions)
    job.cancel()
  }

  @Test
  fun observeVenue_doesNotEmitWhenAnotherVenueIsCreated() = runTest {
    val repository = FakeVenueRepository()
    val venueA = venue(id = "venue-a")
    repository.createVenue(venueA)
    val emissions = mutableListOf<Venue?>()
    val job = launch { repository.observeVenue("venue-a").collect { emissions.add(it) } }
    runCurrent()

    repository.createVenue(venue(id = "venue-b"))
    runCurrent()

    Assert.assertEquals(listOf(venueA), emissions)
    job.cancel()
  }

  @Test(timeout = 30_000)
  fun concurrentCreateVenue_onlyOneCallSucceeds() = runBlocking {
    repeat(1_000) {
      val repository = FakeVenueRepository()
      // Same id, different names, so the stored venue shows which call won.
      val candidates = listOf(venue(name = "First"), venue(name = "Second"))
      val barrier = CyclicBarrier(2)
      val calls = candidates.map { candidate ->
        async(Dispatchers.IO) {
          barrier.await(5, TimeUnit.SECONDS)
          repository.createVenue(candidate)
        }
      }
      val results = calls.map { it.await() }
      Assert.assertEquals(1, results.count { it.isSuccess })
      Assert.assertTrue(results.single { it.isFailure }.exceptionOrNull() is IllegalStateException)
      val winner = candidates[results.indexOfFirst { it.isSuccess }]
      Assert.assertEquals(winner, repository.getVenue(winner.id))
    }
  }
}
