// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.e2e

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await

/** The provider name the mocked positions report; any name will do. */
private const val MOCK_PROVIDER = "e2e"

/**
 * Puts the device at [location] for the fused location provider, which the app reads, until
 * [stopMockingDeviceLocation]. The test and the app then agree on where the explorer is, whatever
 * fix the emulator has, or doesn't have yet.
 *
 * The fused provider takes mocked positions only from an app allowed to mock locations, which only
 * the shell can allow, so this allows the app first.
 */
suspend fun mockDeviceLocation(location: Location) {
  val instrumentation = InstrumentationRegistry.getInstrumentation()
  val app = instrumentation.targetContext
  // Reading the output to its end waits for the command to finish
  ParcelFileDescriptor.AutoCloseInputStream(
          instrumentation.uiAutomation.executeShellCommand(
              "appops set ${app.packageName} android:mock_location allow"
          )
      )
      .use { it.readBytes() }
  val client = LocationServices.getFusedLocationProviderClient(app)
  client.setMockMode(true).await()
  client
      .setMockLocation(
          android.location.Location(MOCK_PROVIDER).apply {
            latitude = location.lat
            longitude = location.lng
            // The provider ignores a position without these
            accuracy = 1f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
          }
      )
      .await()
}

/** Gives the device its real position back, after [mockDeviceLocation]. */
suspend fun stopMockingDeviceLocation() {
  val app = InstrumentationRegistry.getInstrumentation().targetContext
  LocationServices.getFusedLocationProviderClient(app).setMockMode(false).await()
}
