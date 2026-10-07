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

  @Test
  fun setArea_onExistingVenue_succeedsAndReplacesOnlyLocationAndRadius() = runTest {
    val repository = FakeVenueRepository()
    val original = venue(location = null)
    repository.createVenue(original)

    val result = repository.setArea(original.id, Location(47.37, 8.54), 120)

    Assert.assertEquals(Result.success(Unit), result)
    Assert.assertEquals(
        original.copy(location = Location(47.37, 8.54), radiusMeters = 120),
        repository.getVenue(original.id),
    )
  }

  @Test
  fun setArea_onUnknownId_failsAndCreatesNoVenue() = runTest {
    val repository = FakeVenueRepository()

    val result = repository.setArea("unknown-venue", Location(47.37, 8.54), 120)

    Assert.assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    Assert.assertNull(repository.getVenue("unknown-venue"))
  }

  @Test
  fun setArea_calledTwice_keepsTheLastValues() = runTest {
    val repository = FakeVenueRepository()
    val original = venue(location = null)
    repository.createVenue(original)

    val first = repository.setArea(original.id, Location(47.37, 8.54), 120)
    val second = repository.setArea(original.id, Location(46.20, 6.14), 80)

    Assert.assertEquals(Result.success(Unit), first)
    Assert.assertEquals(Result.success(Unit), second)
    Assert.assertEquals(
        original.copy(location = Location(46.20, 6.14), radiusMeters = 80),
        repository.getVenue(original.id),
    )
  }

  @Test
  fun setArea_doesNotChangeAnotherVenue() = runTest {
    val repository = FakeVenueRepository()
    val venueB = venue(id = "venue-b")
    repository.createVenue(venue(id = "venue-a"))
    repository.createVenue(venueB)

    val result = repository.setArea("venue-a", Location(47.37, 8.54), 120)

    Assert.assertEquals(Result.success(Unit), result)
    Assert.assertEquals(venueB, repository.getVenue("venue-b"))
  }

  @Test
  fun observeVenue_emitsTheUpdatedVenueAfterSetArea() = runTest {
    val repository = FakeVenueRepository()
    val original = venue(location = null)
    repository.createVenue(original)
    val emissions = mutableListOf<Venue?>()
    val job = launch { repository.observeVenue(original.id).collect { emissions.add(it) } }
    runCurrent()

    repository.setArea(original.id, Location(47.37, 8.54), 120)
    runCurrent()

    val updated = original.copy(location = Location(47.37, 8.54), radiusMeters = 120)
    Assert.assertEquals(listOf(original, updated), emissions)
    job.cancel()
  }

  @Test
  fun observeVenue_doesNotEmitWhenSetAreaKeepsTheSameArea() = runTest {
    val repository = FakeVenueRepository()
    val location = Location(46.52, 6.57)
    val original = venue(location = location)
    repository.createVenue(original)
    val emissions = mutableListOf<Venue?>()
    val job = launch { repository.observeVenue(original.id).collect { emissions.add(it) } }
    runCurrent()

    val result = repository.setArea(original.id, location, original.radiusMeters)
    runCurrent()

    Assert.assertEquals(Result.success(Unit), result)
    Assert.assertEquals(listOf(original), emissions)
    job.cancel()
  }

  @Test(timeout = 30_000)
  fun concurrentSetArea_onOneVenue_storesOneCallsLocationAndRadiusTogether() = runBlocking {
    repeat(1_000) {
      val repository = FakeVenueRepository()
      val original = venue(location = null)
      repository.createVenue(original)
      // Each call pairs a distinct location with a distinct radius, so a mix shows up.
      val areas = (0 until 4).map { i -> Location(46.0 + i, 6.0 + i) to 20 + i * 10 }
      val barrier = CyclicBarrier(areas.size)
      val calls = areas.map { (location, radiusMeters) ->
        async(Dispatchers.IO) {
          barrier.await(5, TimeUnit.SECONDS)
          repository.setArea(original.id, location, radiusMeters)
        }
      }
      val results = calls.map { it.await() }
      Assert.assertTrue(results.all { it.isSuccess })
      val candidates = areas.map { (location, radiusMeters) ->
        original.copy(location = location, radiusMeters = radiusMeters)
      }
      Assert.assertTrue(repository.getVenue(original.id) in candidates)
    }
  }

  @Test(timeout = 30_000)
  fun concurrentSetArea_onDifferentVenues_keepsBothUpdates() = runBlocking {
    repeat(1_000) {
      val repository = FakeVenueRepository()
      val venueA = venue(id = "venue-a", location = null)
      val venueB = venue(id = "venue-b", location = null)
      repository.createVenue(venueA)
      repository.createVenue(venueB)
      val barrier = CyclicBarrier(2)
      val updateA =
          async(Dispatchers.IO) {
            barrier.await(5, TimeUnit.SECONDS)
            repository.setArea(venueA.id, Location(47.37, 8.54), 120)
          }
      val updateB =
          async(Dispatchers.IO) {
            barrier.await(5, TimeUnit.SECONDS)
            repository.setArea(venueB.id, Location(46.20, 6.14), 80)
          }
      Assert.assertTrue(updateA.await().isSuccess)
      Assert.assertTrue(updateB.await().isSuccess)
      // A read-then-write update would let one call overwrite the other's change.
      Assert.assertEquals(
          venueA.copy(location = Location(47.37, 8.54), radiusMeters = 120),
          repository.getVenue(venueA.id),
      )
      Assert.assertEquals(
          venueB.copy(location = Location(46.20, 6.14), radiusMeters = 80),
          repository.getVenue(venueB.id),
      )
    }
  }

  @Test(timeout = 30_000)
  fun setAreaRacingCreateVenue_resultMatchesStoredVenue() = runBlocking {
    repeat(1_000) {
      val repository = FakeVenueRepository()
      val original = venue(location = null)
      val barrier = CyclicBarrier(2)
      val creation =
          async(Dispatchers.IO) {
            barrier.await(5, TimeUnit.SECONDS)
            repository.createVenue(original)
          }
      val update =
          async(Dispatchers.IO) {
            barrier.await(5, TimeUnit.SECONDS)
            repository.setArea(original.id, Location(47.37, 8.54), 120)
          }
      Assert.assertTrue(creation.await().isSuccess)
      val result = update.await()
      if (result.isSuccess) {
        Assert.assertEquals(
            original.copy(location = Location(47.37, 8.54), radiusMeters = 120),
            repository.getVenue(original.id),
        )
      } else {
        Assert.assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        Assert.assertEquals(original, repository.getVenue(original.id))
      }
    }
  }
}
