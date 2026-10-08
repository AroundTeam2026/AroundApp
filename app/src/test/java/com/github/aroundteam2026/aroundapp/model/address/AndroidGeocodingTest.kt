// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.address

import android.location.Address
import android.location.Geocoder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowGeocoder

/**
 * Tests [AndroidGeocoding] on Robolectric's geocoder, which answers lookups without bounds from
 * Android 13, and on a mock for the rest: bounded lookups, and the blocking lookup used before
 * Android 13, which Robolectric's geocoder doesn't answer. `AndroidGeocodingDeviceTest` runs them
 * on a real geocoder.
 */
@RunWith(AndroidJUnit4::class)
class AndroidGeocodingTest {
  private val geocoder = Geocoder(ApplicationProvider.getApplicationContext())
  private val geocoding = AndroidGeocoding(geocoder)

  private val bourg =
      Address(Locale.ROOT).apply {
        latitude = 46.5199
        longitude = 6.6346
        thoroughfare = "Rue de Bourg"
      }

  @After fun tearDown() = ShadowGeocoder.reset()

  @Test
  fun isAvailableWhenTheDeviceHasAGeocoder() {
    ShadowGeocoder.setIsPresent(true)

    assertTrue(geocoding.isAvailable)
  }

  @Test
  fun isUnavailableWhenTheDeviceHasNone() {
    ShadowGeocoder.setIsPresent(false)

    assertFalse(geocoding.isAvailable)
  }

  @Test
  fun returnsWhatTheGeocoderFinds() = runTest {
    shadowGeocoder().setFromLocation(listOf(bourg))

    val found = geocoding.fromName("Rue de Bourg", maxResults = 5, within = null)

    assertEquals(listOf("Rue de Bourg"), found.map { it.thoroughfare })
  }

  @Test
  fun aGeocoderErrorIsAnIOException() = runTest {
    shadowGeocoder().setErrorMessage("Service unavailable")

    try {
      geocoding.fromName("Rue de Bourg", maxResults = 5, within = null)
      fail("Expected an IOException")
    } catch (e: IOException) {
      assertEquals("Service unavailable", e.message)
    }
  }

  @Test
  fun boundsReachTheGeocoderAsSouthWestNorthEast() = runTest {
    val mock = mockk<Geocoder>()
    every { mock.getFromLocationName("gare", 5, 46.3, 6.4, 46.7, 6.9, any()) } answers
        {
          arg<Geocoder.GeocodeListener>(6).onGeocode(mutableListOf(bourg))
        }

    val found =
        AndroidGeocoding(mock)
            .fromName("gare", 5, GeoBounds(south = 46.3, west = 6.4, north = 46.7, east = 6.9))

    assertEquals(listOf(bourg), found)
  }

  @Test
  @Config(sdk = [32])
  fun beforeAndroid13TheBlockingLookupIsUsed() = runTest {
    val mock = mockk<Geocoder>()
    @Suppress("DEPRECATION")
    every { mock.getFromLocationName("Rue de Bourg", 5) } returns listOf(bourg)

    assertEquals(listOf(bourg), AndroidGeocoding(mock).fromName("Rue de Bourg", 5, null))
  }

  @Test
  @Config(sdk = [32])
  fun beforeAndroid13BoundsReachTheGeocoderAsSouthWestNorthEast() = runTest {
    val mock = mockk<Geocoder>()
    @Suppress("DEPRECATION")
    every { mock.getFromLocationName("gare", 5, 46.3, 6.4, 46.7, 6.9) } returns listOf(bourg)

    val found =
        AndroidGeocoding(mock)
            .fromName("gare", 5, GeoBounds(south = 46.3, west = 6.4, north = 46.7, east = 6.9))

    assertEquals(listOf(bourg), found)
  }

  @Test
  @Config(sdk = [32])
  fun beforeAndroid13NoAnswerIsNoAddresses() = runTest {
    val mock = mockk<Geocoder>()
    @Suppress("DEPRECATION")
    every { mock.getFromLocationName("nowhere", 5) } returns null

    assertEquals(emptyList<Address>(), AndroidGeocoding(mock).fromName("nowhere", 5, null))
  }

  @Test
  @Config(sdk = [32])
  fun beforeAndroid13AFailingLookupThrowsItsIOException() = runTest {
    val mock = mockk<Geocoder>()
    @Suppress("DEPRECATION")
    every { mock.getFromLocationName("Rue de Bourg", 5) } throws IOException("Timeout")

    try {
      AndroidGeocoding(mock).fromName("Rue de Bourg", 5, null)
      fail("Expected an IOException")
    } catch (e: IOException) {
      assertEquals("Timeout", e.message)
    }
  }

  private fun shadowGeocoder(): ShadowGeocoder = Shadows.shadowOf(geocoder)
}
