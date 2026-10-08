// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.address

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.github.aroundteam2026.aroundapp.model.common.GeoBounds
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Looks addresses up by name. Kept apart from [GeocoderAddressRepository] so it can be faked. */
interface Geocoding {
  /** Whether this device can look addresses up at all. */
  val isAvailable: Boolean

  /**
   * Up to [maxResults] addresses matching [query], only within [within] when given. Bounds that
   * cross the antimeridian aren't supported.
   *
   * @throws IOException when the lookup fails, as without a network connection.
   */
  suspend fun fromName(query: String, maxResults: Int, within: GeoBounds?): List<Address>
}

/**
 * [Geocoding] through Android's [geocoder], which Google Play services provides.
 *
 * @param geocoder The geocoder to ask; tests pass their own.
 */
class AndroidGeocoding(private val geocoder: Geocoder) : Geocoding {
  /** Looks addresses up with the device's geocoder; any context will do. */
  constructor(context: Context) : this(Geocoder(context.applicationContext))

  override val isAvailable: Boolean
    get() = Geocoder.isPresent()

  override suspend fun fromName(query: String, maxResults: Int, within: GeoBounds?): List<Address> =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { continuation ->
          val listener =
              object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) =
                    continuation.resume(addresses)

                override fun onError(errorMessage: String?) =
                    continuation.resumeWithException(IOException(errorMessage))
              }
          if (within == null) geocoder.getFromLocationName(query, maxResults, listener)
          else
              geocoder.getFromLocationName(
                  query,
                  maxResults,
                  within.south,
                  within.west,
                  within.north,
                  within.east,
                  listener,
              )
        }
      } else {
        // The only lookup before Android 13 blocks until the answer arrives
        withContext(Dispatchers.IO) {
              @Suppress("DEPRECATION")
              if (within == null) geocoder.getFromLocationName(query, maxResults)
              else
                  geocoder.getFromLocationName(
                      query,
                      maxResults,
                      within.south,
                      within.west,
                      within.north,
                      within.east,
                  )
            }
            .orEmpty()
      }
}
