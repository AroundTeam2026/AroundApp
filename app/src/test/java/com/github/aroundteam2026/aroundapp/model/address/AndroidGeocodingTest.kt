// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.address

import android.location.Address
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.robolectric.shadows.ShadowGeocoder

/**
 * Tests [AndroidGeocoding] on Robolectric's geocoder, which answers lookups without bounds. The
 * bounded lookup, which it doesn't support, is covered on a device by `AndroidGeocodingDeviceTest`.
 */
@RunWith(AndroidJUnit4::class)
class AndroidGeocodingTest {
  private val geocoding = AndroidGeocoding(ApplicationProvider.getApplicationContext())

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

  /** The shadow of the geocoder [geocoding] wraps. */
  private fun shadowGeocoder(): ShadowGeocoder {
    val field =
        AndroidGeocoding::class.java.getDeclaredField("geocoder").apply { isAccessible = true }
    return org.robolectric.Shadows.shadowOf(field.get(geocoding) as android.location.Geocoder)
  }
}
