// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.address

import android.location.Address
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.model.common.contains
import com.github.aroundteam2026.aroundapp.model.common.distanceTo
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Tests how geocoder results become address suggestions, and how the search is biased. */
@RunWith(AndroidJUnit4::class)
class GeocoderAddressRepositoryTest {

  private val bourg = address(46.5199, 6.6346, "Rue de Bourg", "12", "1003", "Lausanne")
  private val gare = address(46.5167, 6.6291, "Place de la Gare", "9", "1003", "Lausanne")
  private val bahnhof = address(47.3779, 8.5403, "Bahnhofplatz", "1", "8001", "Zürich")

  /** The bounds the map shows: a few hundred metres around Lausanne's cathedral. */
  private val visible = Location(46.5226, 6.6356).boundsWithin(300.0)

  private val geocoding = FakeGeocoding()
  private val repository = GeocoderAddressRepository(geocoding)

  @Test
  fun aSuggestionShowsTheStreetAndNumberAboveThePostcodeAndTown() = runTest {
    geocoding.answer = { _, _ -> listOf(bourg) }

    val suggestion = found(repository.search("rue de bourg", near = null)).single()

    assertEquals("Rue de Bourg 12", suggestion.title)
    assertEquals("1003 Lausanne", suggestion.subtitle)
    assertEquals("Rue de Bourg 12, 1003 Lausanne", suggestion.address)
    assertEquals(Location(46.5199, 6.6346), suggestion.location)
  }

  @Test
  fun aStreetWithoutANumberIsTheStreetAlone() = runTest {
    geocoding.answer = { _, _ ->
      listOf(address(46.52, 6.63, "Avenue de Rumine", null, "1005", "Lausanne"))
    }

    val suggestion = found(repository.search("rumine", near = null)).single()

    assertEquals("Avenue de Rumine", suggestion.title)
    assertEquals("Avenue de Rumine, 1005 Lausanne", suggestion.address)
  }

  @Test
  fun aPlaceWithoutAStreetIsNamedByItsFeature() = runTest {
    val cathedral =
        address(46.5226, 6.6356, null, null, "1005", "Lausanne").apply {
          featureName = "Cathédrale de Lausanne"
        }
    geocoding.answer = { _, _ -> listOf(cathedral) }

    val suggestion = found(repository.search("cathédrale", near = null)).single()

    assertEquals("Cathédrale de Lausanne", suggestion.title)
    assertEquals("1005 Lausanne", suggestion.subtitle)
  }

  @Test
  fun anAddressKnownOnlyByItsLineIsSplitAtTheFirstComma() = runTest {
    val line =
        Address(Locale.ROOT).apply {
          latitude = 46.51
          longitude = 6.62
          setAddressLine(0, "Quai d'Ouchy 1, 1006 Lausanne, Switzerland")
        }
    geocoding.answer = { _, _ -> listOf(line) }

    val suggestion = found(repository.search("ouchy", near = null)).single()

    assertEquals("Quai d'Ouchy 1", suggestion.title)
    assertEquals("1006 Lausanne, Switzerland", suggestion.subtitle)
    assertEquals("Quai d'Ouchy 1, 1006 Lausanne, Switzerland", suggestion.address)
  }

  @Test
  fun aTownWithoutAPostcodeIsTheSubtitleAlone() = runTest {
    geocoding.answer = { _, _ -> listOf(address(46.5, 6.6, "Chemin Vert", "3", null, "Pully")) }

    val suggestion = found(repository.search("chemin vert", near = null)).single()

    assertEquals("Pully", suggestion.subtitle)
    assertEquals("Chemin Vert 3, Pully", suggestion.address)
  }

  @Test
  fun anAddressWithNothingToShowIsLeftOut() = runTest {
    val blank =
        Address(Locale.ROOT).apply {
          latitude = 46.5
          longitude = 6.6
        }
    geocoding.answer = { _, _ -> listOf(blank, bourg) }

    assertEquals(
        listOf("Rue de Bourg 12"),
        found(repository.search("x y z", null)).map { it.title },
    )
  }

  @Test
  fun anAddressWithoutCoordinatesIsLeftOut() = runTest {
    val nowhere =
        Address(Locale.ROOT).apply {
          thoroughfare = "Rue Nulle Part"
          locality = "Lausanne"
        }
    geocoding.answer = { _, _ -> listOf(nowhere, bourg) }

    assertEquals(listOf("Rue de Bourg 12"), found(repository.search("rue", null)).map { it.title })
  }

  @Test
  fun theQueryIsTrimmedBeforeSearching() = runTest {
    geocoding.answer = { _, _ -> listOf(bourg) }

    repository.search("  rue de bourg 12 \n", near = null)

    assertEquals(listOf("rue de bourg 12"), geocoding.calls.map { it.query })
  }

  @Test
  fun aBlankQueryFindsNothingWithoutAskingTheGeocoder() = runTest {
    assertEquals(AddressSearchResult.Found(emptyList()), repository.search("   ", near = null))
    assertTrue(geocoding.calls.isEmpty())
  }

  @Test
  fun withoutAViewTheWholeWorldIsSearchedOnce() = runTest {
    geocoding.answer = { _, _ -> listOf(bahnhof) }

    found(repository.search("bahnhofplatz", near = null))

    assertEquals(1, geocoding.calls.size)
    assertNull(geocoding.calls.single().within)
    assertEquals(GeocoderAddressRepository.MAX_SUGGESTIONS, geocoding.calls.single().maxResults)
  }

  @Test
  fun theViewIsSearchedFirstWithinTwentyKilometresOfItsCentre() = runTest {
    geocoding.answer = { _, _ -> emptyList() }

    repository.search("gare", near = visible)

    val within = geocoding.calls.first().within!!
    val centre = Location(46.5226, 6.6356)
    // A view of a few hundred metres would find almost nothing; the search looks around it
    assertTrue(within.contains(centre.copy(lat = centre.lat + 0.17)))
    assertTrue(within.contains(centre.copy(lng = centre.lng - 0.25)))
    val corner = Location(within.north, within.east)
    assertTrue(centre.distanceTo(corner) in 20_000.0..30_000.0)
  }

  @Test
  fun resultsAroundTheViewComeFirstThenTheRestOfTheWorld() = runTest {
    geocoding.answer = { _, within -> if (within != null) listOf(gare) else listOf(bahnhof, gare) }

    val titles = found(repository.search("platz gare", near = visible)).map { it.title }

    // Place de la Gare is found both times, and listed once
    assertEquals(listOf("Place de la Gare 9", "Bahnhofplatz 1"), titles)
    assertEquals(listOf(true, false), geocoding.calls.map { it.within != null })
  }

  @Test
  fun enoughResultsAroundTheViewSkipTheWorldSearch() = runTest {
    val nearby = (1..5).map { address(46.52, 6.63, "Rue de Bourg", "$it", "1003", "Lausanne") }
    geocoding.answer = { _, within -> if (within != null) nearby else listOf(bahnhof) }

    val titles = found(repository.search("rue de bourg", near = visible)).map { it.title }

    assertEquals((1..5).map { "Rue de Bourg $it" }, titles)
    assertEquals(1, geocoding.calls.size)
  }

  @Test
  fun neverMoreThanFiveSuggestions() = runTest {
    val many = (1..9).map { address(47.37, 8.54, "Bahnhofstrasse", "$it", "8001", "Zürich") }
    geocoding.answer = { _, within -> if (within != null) emptyList() else many }

    assertEquals(5, found(repository.search("bahnhofstrasse", near = visible)).size)
    assertEquals(GeocoderAddressRepository.MAX_SUGGESTIONS, geocoding.calls.last().maxResults)
  }

  @Test
  fun aViewAcrossTheAntimeridianSkipsTheBiasedSearch() = runTest {
    geocoding.answer = { _, _ -> listOf(bahnhof) }

    found(repository.search("bahnhofplatz", near = GeoBounds(-10.0, 179.9, -9.9, -179.9)))

    // The geocoder can't take bounds that wrap, so the world is searched instead
    assertEquals(listOf<GeoBounds?>(null), geocoding.calls.map { it.within })
  }

  @Test
  fun aFailingGeocoderReportsAFailure() = runTest {
    geocoding.answer = { _, _ -> throw IOException("No network") }

    assertEquals(AddressSearchResult.Failed, repository.search("rue de bourg", near = null))
  }

  @Test
  fun aFailureAroundTheViewStillReportsAFailure() = runTest {
    geocoding.answer = { _, within ->
      if (within != null) throw IOException("Timeout") else listOf(bahnhof)
    }

    assertEquals(AddressSearchResult.Failed, repository.search("bahnhofplatz", near = visible))
  }

  @Test
  fun aDeviceWithoutAGeocoderReportsSearchUnavailable() = runTest {
    geocoding.isAvailable = false

    assertEquals(AddressSearchResult.Unavailable, repository.search("rue de bourg", near = null))
    assertTrue(geocoding.calls.isEmpty())
  }

  private fun found(result: AddressSearchResult): List<AddressSuggestion> {
    assertTrue("Expected suggestions, got $result", result is AddressSearchResult.Found)
    return (result as AddressSearchResult.Found).suggestions
  }

  private fun address(
      lat: Double,
      lng: Double,
      street: String?,
      number: String?,
      postcode: String?,
      town: String?,
  ) =
      Address(Locale.ROOT).apply {
        latitude = lat
        longitude = lng
        thoroughfare = street
        subThoroughfare = number
        postalCode = postcode
        locality = town
      }
}

/** Answers lookups with [answer] and records them. */
private class FakeGeocoding : Geocoding {
  data class Call(val query: String, val maxResults: Int, val within: GeoBounds?)

  override var isAvailable = true
  var answer: (query: String, within: GeoBounds?) -> List<Address> = { _, _ -> emptyList() }
  val calls = mutableListOf<Call>()

  override suspend fun fromName(query: String, maxResults: Int, within: GeoBounds?): List<Address> {
    calls += Call(query, maxResults, within)
    return answer(query, within)
  }
}
