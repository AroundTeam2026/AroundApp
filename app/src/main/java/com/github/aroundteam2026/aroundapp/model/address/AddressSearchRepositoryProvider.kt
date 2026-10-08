// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.address

import android.content.Context

/** Builds the app's [AddressSearchRepository], so ViewModels never depend on an implementation. */
object AddressSearchRepositoryProvider {
  /** The repository to use; any context will do, since the geocoder keeps only the app's. */
  fun repository(context: Context): AddressSearchRepository =
      GeocoderAddressRepository(AndroidGeocoding(context))
}
