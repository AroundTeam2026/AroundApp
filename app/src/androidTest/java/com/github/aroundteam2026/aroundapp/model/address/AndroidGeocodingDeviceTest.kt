// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.address

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.model.common.contains
import com.github.aroundteam2026.aroundapp.model.common.distanceTo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs address lookups through the device's real geocoder, which needs Google Play services and a
 * network connection.
 */
@RunWith(AndroidJUnit4::class)
class AndroidGeocodingDeviceTest {
  private val geocoding = AndroidGeocoding(ApplicationProvider.getApplicationContext())
  private val lausanne = Location(46.5197, 6.6323)

  @Test
  fun aPlayServicesDeviceHasAGeocoder() {
    assertTrue(geocoding.isAvailable)
  }

  @Test
  fun findsAStreetByItsName() = runTest {
    val found = geocoding.fromName("Rue de Bourg, Lausanne", maxResults = 5, within = null)

    assertTrue("Nothing found", found.isNotEmpty())
    val first = Location(found.first().latitude, found.first().longitude)
    assertTrue("$first is not in Lausanne", lausanne.distanceTo(first) < 3_000)
  }

  @Test
  fun aLookupWithinBoundsStaysInThem() = runTest {
    val around = lausanne.boundsWithin(20_000.0)

    val found = geocoding.fromName("Place de la Gare", maxResults = 5, within = around)

    assertTrue("Nothing found", found.isNotEmpty())
    found.forEach {
      val where = Location(it.latitude, it.longitude)
      assertTrue("$where is outside $around", around.contains(where))
    }
  }

  @Test
  fun returnsAtMostTheResultsAskedFor() = runTest {
    val found = geocoding.fromName("Rue de Lausanne", maxResults = 2, within = null)

    assertTrue(found.size <= 2)
  }
}
