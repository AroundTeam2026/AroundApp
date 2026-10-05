// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.location

import android.annotation.SuppressLint
import android.content.Context
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

/** A [LocationRepository] backed by Google Play services' fused location provider. */
class FusedLocationRepository(
    private val context: Context,
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context),
) : LocationRepository {

  // Lint can't see the permission check through LocationPermissions.isGranted
  @SuppressLint("MissingPermission")
  override suspend fun currentLocation(): Location? {
    if (!LocationPermissions.isGranted(context)) return null
    // Cancelling the caller cancels the request, so leaving the map stops the location hardware
    val cancellation = CancellationTokenSource()
    return try {
      client
          // City-block accuracy: enough to frame the map, cheaper and faster than GPS
          .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
          .await(cancellation)
          ?.let { Location(it.latitude, it.longitude) }
    } catch (e: ApiException) {
      // Location is turned off or Play services can't provide it
      null
    } catch (e: SecurityException) {
      // The permission was revoked after the check above
      null
    }
  }
}
